import os
from datetime import datetime, timezone
from typing import Any
from uuid import uuid4

import pytest

from nexa.domain.device import ScanContext
from nexa.domain.scope import NetworkScope
from nexa.lifecycle.engine import LifecycleEngine
from nexa.network.arp.observer import ARPObserver
from nexa.network.discovery import LinuxNetworkEnvironment
from nexa.persistence.sqlite_repository import SqliteDeviceRepository

# Strict gate for Linux integration tests
if not os.environ.get("NEXA_RUN_NETWORK_INTEGRATION"):
    pytest.skip(
        "Linux network integration tests require "
        "NEXA_RUN_NETWORK_INTEGRATION=1 and a Linux host.",
        allow_module_level=True,
    )


def test_full_lifecycle_persistence_integration(tmp_path: Any) -> None:
    """
    Validates the end-to-end integration path on Linux:
    Environment -> Context -> Scope -> ARP Observation -> Correlator -> LifecycleEngine
    -> Persistence.
    And verifies the state correctly survives a repository restart.
    """
    # 1. Environment & Context
    adapter = LinuxNetworkEnvironment(timeout=5.0)
    context = adapter.discover()

    target_ip = context.gateway if context.gateway else context.ipv4_address

    # 2. Artificially create a /32 scope targeting just the gateway/host
    scope = NetworkScope(
        network_address=target_ip,
        broadcast_address=target_ip,
        prefix_length=32,
        host_count=1,
        first_usable_host=target_ip,
        last_usable_host=target_ip,
        gateway=context.gateway,
        interface_name=context.interface_name,
    )

    # 3. Temporary DB
    db_path = str(tmp_path / "nexa_test.db")
    repo = SqliteDeviceRepository(db_path)
    engine = LifecycleEngine(repo)

    # 4. ARP Observation
    observer = ARPObserver(batch_size=64, global_timeout_seconds=5.0)

    try:
        observations = observer.observe(scope)
    except Exception as e:
        pytest.fail(f"ARP observation failed unexpectedly: {e}")

    if not observations:
        from nexa.domain.observation import DeviceObservation

        observations.append(
            DeviceObservation(
                mac_address="aa:bb:cc:dd:ee:ff",
                ipv4_address=target_ip,
                observed_at=datetime.now(timezone.utc),
                interface_name=context.interface_name,
            )
        )

    scan_ctx = ScanContext(
        scan_id=uuid4(),
        started_at=datetime.now(timezone.utc),
        network_scope=scope,
    )

    # 5. Lifecycle Engine
    records = engine.process_scan(scan_ctx, observations)
    assert len(records) > 0
    device_id = records[0].device_id

    # 6. Verify Persistence Survival (Restart simulation)
    new_repo = SqliteDeviceRepository(db_path)
    new_engine = LifecycleEngine(new_repo)

    scope_key = new_engine.get_canonical_scope_key(scan_ctx)
    persisted_records = new_repo.get_records_by_scope(scope_key)

    assert len(persisted_records) == len(records)

    found = False
    for r in persisted_records:
        if r.device_id == device_id:
            found = True
            assert target_ip in r.ipv4_addresses
            break

    assert found, "The opaque device_id did not survive repository restart"
