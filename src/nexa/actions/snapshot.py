"""Target snapshot builder for Phase 4."""

import uuid
from datetime import datetime, timezone
from typing import Any

from nexa.domain.actions import TargetSnapshot
from nexa.domain.lifecycle import DeviceRepository
from nexa.domain.trust_lifecycle import TrustRepository


class SnapshotException(Exception):
    """Raised when a target snapshot cannot be constructed."""

    pass


def build_target_snapshot(
    action_id: uuid.UUID,
    identity_id: uuid.UUID,
    trust_repo: TrustRepository,
    device_repo: DeviceRepository,
    authorization_context: dict[str, Any],
    max_observation_age_seconds: int = 300,
    max_freshness_age_seconds: int = 300,
) -> TargetSnapshot:
    """
    Constructs a TargetSnapshot for an enforcement action.

    This ensures that immediately prior to action authorization and execution,
    we have a fixed snapshot of the target's IP, MAC, and cryptographic freshness.
    """
    # 1. Resolve Trust identity
    identity = trust_repo.get_identity(str(identity_id))
    if not identity:
        raise SnapshotException(f"Identity {identity_id} not found.")

    now = datetime.now(timezone.utc)
    freshness_age = (now - identity.updated_at).total_seconds()
    if freshness_age > max_freshness_age_seconds:
        raise SnapshotException(
            f"Identity {identity_id} cryptographic freshness expired."
        )

    # 2. Resolve associated DeviceRecord(s)
    device_ids = trust_repo.get_device_ids_for_identity(str(identity_id))
    if not device_ids:
        raise SnapshotException(f"No devices linked to identity {identity_id}.")

    valid_records = []

    for d_id in device_ids:
        record = device_repo.get_record_by_id(d_id)
        if not record:
            continue

        # Check staleness
        if not record.last_observed_at:
            continue
        age = (now - record.last_observed_at).total_seconds()
        if age > max_observation_age_seconds:
            continue

        # Must have IP and MAC
        if not record.ipv4_addresses or not record.mac_addresses:
            continue

        valid_records.append(record)

    if not valid_records:
        raise SnapshotException("0 matching valid/fresh device records found.")

    if len(valid_records) > 1:
        raise SnapshotException(
            f"Multiple plausible target records ({len(valid_records)}) found. "
            "Failing closed."
        )

    device_record = valid_records[0]

    # Stale IP/MAC check: ensure exactly one active IP/MAC to avoid ambiguity
    if len(device_record.ipv4_addresses) > 1:
        raise SnapshotException(
            "Multiple IPs associated with target device. Failing closed."
        )
    if len(device_record.mac_addresses) > 1:
        raise SnapshotException(
            "Multiple MACs associated with target device. Failing closed."
        )

    ip_address = str(next(iter(device_record.ipv4_addresses)))
    mac_address = next(iter(device_record.mac_addresses))

    # 3. Cryptographic freshness
    freshness = identity.updated_at

    return TargetSnapshot(
        action_id=action_id,
        trusted_identity=identity,
        device_record=device_record,
        network_scope=device_record.network_scope,
        ip_address=ip_address,
        mac_address=mac_address,
        observation_timestamp=device_record.last_observed_at,
        cryptographic_freshness=freshness,
        authorization_context=authorization_context,
    )
