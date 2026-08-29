import hashlib
import json
from typing import Union

from nexa.domain.events import AggregatedSecurityEvent, SecurityEvent


class AlertAggregator:
    """
    Deterministically aggregates SecurityEvents into a canonical hash key.
    """

    @staticmethod
    def compute_aggregation_key(
        event: Union[SecurityEvent, AggregatedSecurityEvent],
        policy_discriminator: str = "",
    ) -> str:
        """
        Computes a deterministic SHA-256 hash for aggregation based on:
        - event_class
        - identity_id (or null)
        - device_id (or null)
        - network_scope
        - policy_discriminator
        """
        identity_id = None
        device_id = None
        if isinstance(event, SecurityEvent):
            identity_id = str(event.identity_id) if event.identity_id else None
            device_id = str(event.device_id) if event.device_id else None
        elif isinstance(event, AggregatedSecurityEvent):
            # Aggregated events maintain arrays of IDs, but for canonical
            # aggregation to alert we can use the first ID, or None if
            # multiple. In practice, the sweeper groups by ID.
            identity_id = (
                event.identity_ids[0] if len(event.identity_ids) == 1 else None
            )
            device_id = event.device_ids[0] if len(event.device_ids) == 1 else None

        canonical_dict = {
            "event_class": event.event_class,
            "identity_id": identity_id,
            "device_id": device_id,
            "network_scope": event.network_scope,
            "policy_discriminator": policy_discriminator,
        }

        canonical_json = json.dumps(canonical_dict, sort_keys=True)
        return hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()
