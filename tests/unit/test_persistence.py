import sqlite3
from datetime import datetime, timezone
from ipaddress import IPv4Address, IPv4Network
from typing import Any
from uuid import uuid4

import pytest

from nexa.domain.correlation import PresenceState
from nexa.domain.device import DeviceRecord
from nexa.domain.lifecycle import (
    LifecycleEvent,
    LifecycleEventType,
    ScanTransactionEnvelope,
)
from nexa.domain.network import NetworkContext
from nexa.domain.scope import NetworkScope
from nexa.persistence.sqlite_repository import SqliteDeviceRepository


@pytest.fixture
def memory_db() -> Any:
    # Use memory database for testing
    # Note: sqlite3 allows passing ":memory:" but since our repo uses a path,
    # let's just pass ":memory:". Wait, using an actual temp file might be
    # better for WAL testing, but :memory: is simpler for now.
    return ":memory:"


@pytest.fixture
def repo(tmp_path: Any) -> Any:
    # Temp file is better for WAL and persistence tests
    db_file = tmp_path / "nexa.db"
    return SqliteDeviceRepository(str(db_file))


def test_schema_initialization(repo: Any) -> None:
    """Test fresh schema initialization to v1."""
    with sqlite3.connect(repo.db_path) as conn:
        cursor = conn.cursor()
        cursor.execute("PRAGMA user_version")
        assert cursor.fetchone()[0] == 1


def test_invalid_migration_version(tmp_path: Any) -> None:
    """Test that a DB from the future halts startup."""
    db_file = tmp_path / "future.db"
    with sqlite3.connect(str(db_file)) as conn:
        conn.execute("PRAGMA user_version = 999")

    with pytest.raises(RuntimeError, match="newer than application supports"):
        SqliteDeviceRepository(str(db_file))


def _create_scope(cidr: str, interface_name: str) -> NetworkScope:
    network = IPv4Network(cidr)
    ctx = NetworkContext(
        interface_name=interface_name,
        network=network,
        ipv4_address=IPv4Address(network.network_address + 2),
        gateway=IPv4Address(network.network_address + 1),
    )
    return NetworkScope.from_context(ctx)


def test_save_and_get_records(repo: Any) -> None:
    """Test basic CRUD logic."""
    scope = _create_scope("192.168.1.0/24", "eth0")
    device_id = uuid4()
    now = datetime.now(timezone.utc)

    record = DeviceRecord(
        device_id=device_id,
        network_scope=scope,
        mac_addresses=frozenset(["aa:bb:cc:dd:ee:ff"]),
        ipv4_addresses=frozenset([IPv4Address("192.168.1.50")]),
        first_observed_at=now,
        last_observed_at=now,
        presence_state=PresenceState.PRESENT,
        conflicts=frozenset(),
    )

    envelope = ScanTransactionEnvelope(
        scope_key="test_scope", records=[record], conflicts=[], events=[], timestamp=now
    )

    repo.save_scan_transaction(envelope)

    loaded = repo.get_records_by_scope("test_scope")
    assert len(loaded) == 1
    assert loaded[0].device_id == device_id
    assert "aa:bb:cc:dd:ee:ff" in loaded[0].mac_addresses
    assert IPv4Address("192.168.1.50") in loaded[0].ipv4_addresses


def test_scope_isolation(repo: Any) -> None:
    """Test that records from scope A don't bleed into scope B."""
    scope_a = _create_scope("192.168.1.0/24", "eth0")
    scope_b = _create_scope("10.0.0.0/24", "eth1")

    now = datetime.now(timezone.utc)
    record_a = DeviceRecord(
        device_id=uuid4(),
        network_scope=scope_a,
        mac_addresses=frozenset(["aa:bb:cc:dd:ee:ff"]),
        ipv4_addresses=frozenset(),
        first_observed_at=now,
        last_observed_at=now,
        presence_state=PresenceState.PRESENT,
        conflicts=frozenset(),
    )
    record_b = DeviceRecord(
        device_id=uuid4(),
        network_scope=scope_b,
        mac_addresses=frozenset(["aa:bb:cc:dd:ee:ff"]),  # Same MAC, different scope
        ipv4_addresses=frozenset(),
        first_observed_at=now,
        last_observed_at=now,
        presence_state=PresenceState.PRESENT,
        conflicts=frozenset(),
    )

    repo.save_scan_transaction(
        ScanTransactionEnvelope("scope_a", [record_a], [], [], now)
    )
    repo.save_scan_transaction(
        ScanTransactionEnvelope("scope_b", [record_b], [], [], now)
    )

    loaded_a = repo.get_records_by_scope("scope_a")
    assert len(loaded_a) == 1
    assert loaded_a[0].device_id == record_a.device_id

    loaded_b = repo.get_records_by_scope("scope_b")
    assert len(loaded_b) == 1
    assert loaded_b[0].device_id == record_b.device_id


def test_atomic_pruning(repo: Any) -> None:
    """Test that pruning removes UNSEEN devices and cascades correctly."""
    scope = _create_scope("192.168.1.0/24", "eth0")
    device_id = uuid4()

    old_time = datetime(2020, 1, 1, tzinfo=timezone.utc)

    record = DeviceRecord(
        device_id=device_id,
        network_scope=scope,
        mac_addresses=frozenset(["aa:bb:cc:dd:ee:ff"]),
        ipv4_addresses=frozenset([IPv4Address("192.168.1.50")]),
        first_observed_at=old_time,
        last_observed_at=old_time,  # Very old
        presence_state=PresenceState.UNSEEN,  # Unseen
        conflicts=frozenset(),
    )

    event = LifecycleEvent(
        device_id=device_id,
        event_type=LifecycleEventType.BECAME_UNSEEN,
        timestamp=old_time,
        description="test",
    )

    repo.save_scan_transaction(
        ScanTransactionEnvelope("test_scope", [record], [], [event], old_time)
    )

    # Verify it exists
    assert len(repo.get_records_by_scope("test_scope")) == 1
    with sqlite3.connect(repo.db_path) as conn:
        assert conn.execute("SELECT COUNT(*) FROM lifecycle_events").fetchone()[0] == 1
        assert conn.execute("SELECT COUNT(*) FROM device_macs").fetchone()[0] == 1

    # Prune anything older than year 2021
    threshold = datetime(2021, 1, 1, tzinfo=timezone.utc)
    deleted_count = repo.prune_stale_records(threshold)
    assert deleted_count == 1

    # Verify cascading deletes worked
    assert len(repo.get_records_by_scope("test_scope")) == 0
    with sqlite3.connect(repo.db_path) as conn:
        assert conn.execute("SELECT COUNT(*) FROM lifecycle_events").fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM device_macs").fetchone()[0] == 0


def test_transaction_rollback(repo: Any) -> None:
    """Test that a failed transaction rolls back completely."""
    scope = _create_scope("192.168.1.0/24", "eth0")
    now = datetime.now(timezone.utc)

    # Valid record
    r1 = DeviceRecord(
        uuid4(),
        scope,
        frozenset(["aa:bb:cc:dd:ee:ff"]),
        frozenset(),
        now,
        now,
        PresenceState.PRESENT,
        frozenset(),
    )

    class BadIP:
        def __str__(self) -> str:
            raise sqlite3.Error("Forced failure")

    r2 = DeviceRecord(
        uuid4(),
        scope,
        frozenset(),
        frozenset([BadIP()]),  # type: ignore
        now,
        now,
        PresenceState.PRESENT,
        frozenset(),
    )
    envelope = ScanTransactionEnvelope("test_scope", [r1, r2], [], [], now)

    with pytest.raises(sqlite3.Error):
        repo.save_scan_transaction(envelope)

    # Verify nothing was saved (r1 should have rolled back)
    assert len(repo.get_records_by_scope("test_scope")) == 0
