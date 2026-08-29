import ipaddress
import sqlite3
from datetime import datetime
from typing import Any
from uuid import uuid4

import pytest

from nexa.domain.correlation import PresenceState
from nexa.domain.device import DeviceRecord
from nexa.domain.events import SecurityEvent, Severity
from nexa.domain.lifecycle import ScanTransactionEnvelope
from nexa.domain.scope import NetworkScope
from nexa.domain.trust import TrustedDeviceIdentity, TrustState
from nexa.persistence.sqlite_alerts import SqliteAlertRepository
from nexa.persistence.sqlite_repository import SqliteDeviceRepository
from nexa.persistence.sqlite_trust import SqliteTrustRepository
from nexa.persistence.uow import TransactionCoordinator


@pytest.fixture
def uow(tmp_path: Any) -> Any:
    db_path = str(tmp_path / "nexa.db")
    device_repo = SqliteDeviceRepository(db_path)
    trust_repo = SqliteTrustRepository(db_path)
    alert_repo = SqliteAlertRepository(db_path)

    # Initialize all schemas explicitly because PRAGMA user_version clashes
    # in a shared file
    from nexa.persistence.sqlite_alerts import SCHEMA_V1 as ALERT_SCHEMA
    from nexa.persistence.sqlite_repository import SCHEMA_V1 as DEV_SCHEMA
    from nexa.persistence.sqlite_trust import SCHEMA_V1 as TRUST_SCHEMA

    with sqlite3.connect(db_path) as conn:
        conn.executescript(DEV_SCHEMA)
        conn.executescript(TRUST_SCHEMA)
        conn.executescript(ALERT_SCHEMA)

    return TransactionCoordinator(db_path, device_repo, trust_repo, alert_repo)


def test_shared_transaction_commits_successfully(uow: Any) -> None:
    device_id = uuid4()
    identity_id = uuid4()
    event_id = uuid4()

    with uow:
        # 1. Device Repo operation
        record = DeviceRecord(
            device_id=device_id,
            network_scope=NetworkScope(
                network_address=ipaddress.IPv4Address("192.168.1.0"),
                broadcast_address=ipaddress.IPv4Address("192.168.1.255"),
                interface_name="eth0",
                prefix_length=24,
                host_count=254,
                first_usable_host=ipaddress.IPv4Address("192.168.1.1"),
                last_usable_host=ipaddress.IPv4Address("192.168.1.254"),
                gateway=None,
            ),
            mac_addresses=frozenset(["aa:bb:cc:dd:ee:ff"]),
            ipv4_addresses=frozenset(),
            first_observed_at=datetime.utcnow(),
            last_observed_at=datetime.utcnow(),
            presence_state=PresenceState.PRESENT,
        )
        envelope = ScanTransactionEnvelope(
            scope_key="test_scope",
            records=[record],
            conflicts=[],
            events=[],
            timestamp=datetime.utcnow(),
        )
        uow.device_repo.save_scan_transaction(envelope)

        # 2. Trust Repo operation
        identity = TrustedDeviceIdentity(
            identity_id=identity_id, state=TrustState.TRUSTED
        )
        uow.trust_repo.save_identity(identity)

        # 3. Alert Repo operation
        event = SecurityEvent(
            event_id=event_id,
            event_class="TEST_EVENT",
            severity=Severity.INFO,
            device_id=device_id,
        )
        uow.alert_repo.append_outbox_event(event)

    # Validate all three succeeded
    assert len(uow.device_repo.get_records_by_scope("test_scope")) == 1
    assert uow.trust_repo.get_identity(str(identity_id)) is not None
    assert uow.alert_repo.get_unprocessed_outbox_count() == 1


def test_shared_transaction_rollback_on_device_failure(uow: Any) -> None:
    identity_id = uuid4()

    with pytest.raises(sqlite3.Error):
        with uow:
            # Save identity (should rollback)
            identity = TrustedDeviceIdentity(
                identity_id=identity_id, state=TrustState.TRUSTED
            )
            uow.trust_repo.save_identity(identity)

            # Force failure in device repo operation
            raise sqlite3.Error("Simulated device repository failure")

    # Validate rollback
    assert uow.trust_repo.get_identity(str(identity_id)) is None


def test_shared_transaction_rollback_on_alert_failure(uow: Any) -> None:
    identity_id = uuid4()

    with pytest.raises(sqlite3.Error):
        with uow:
            identity = TrustedDeviceIdentity(
                identity_id=identity_id, state=TrustState.TRUSTED
            )
            uow.trust_repo.save_identity(identity)

            # Force failure
            raise sqlite3.Error("Simulated alert repository failure")

    assert uow.trust_repo.get_identity(str(identity_id)) is None


def test_shared_transaction_rollback_on_trust_failure(uow: Any) -> None:
    event_id = uuid4()

    with pytest.raises(sqlite3.Error):
        with uow:
            # Save event (should rollback)
            event = SecurityEvent(
                event_id=event_id, event_class="TEST", severity=Severity.INFO
            )
            uow.alert_repo.append_outbox_event(event)

            # Force failure in trust repo
            # save_identity missing fields, etc. or explicit raise
            raise sqlite3.Error("Simulated trust repository failure")

    assert uow.alert_repo.get_unprocessed_outbox_count() == 0
