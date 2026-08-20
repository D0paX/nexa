"""
Unit tests for security and privacy bounds.
"""

import logging
from datetime import datetime, timezone
from ipaddress import IPv4Address, IPv4Network
from typing import Any, Generator
from uuid import uuid4

import pytest

from nexa.domain.device import ScanContext
from nexa.domain.network import NetworkContext
from nexa.domain.observation import DeviceObservation
from nexa.domain.scope import NetworkScope
from nexa.lifecycle.engine import LifecycleEngine
from nexa.persistence.sqlite_repository import SqliteDeviceRepository


class LogCaptureHandler(logging.Handler):
    def __init__(self) -> None:
        super().__init__()
        self.records: list[logging.LogRecord] = []

    def emit(self, record: logging.LogRecord) -> None:
        self.records.append(record)


@pytest.fixture
def capture_logs() -> Generator[LogCaptureHandler, None, None]:
    handler = LogCaptureHandler()
    logger = logging.getLogger()
    logger.addHandler(handler)
    logger.setLevel(logging.DEBUG)
    yield handler
    logger.removeHandler(handler)


def test_logging_redacts_raw_mac_and_ip(
    tmp_path: Any, capture_logs: LogCaptureHandler
) -> None:
    """
    Ensure that when persistence fails, the resulting logs do not
    leak the raw MAC address or IP address into the application log.
    """
    db_file = tmp_path / "nexa.db"

    # Force a failure by corrupting the database connection string or schema
    class BadRepo(SqliteDeviceRepository):
        def save_scan_transaction(self, envelope: Any) -> None:
            raise RuntimeError("Injected persistence error")

    bad_repo = BadRepo(str(db_file))
    engine = LifecycleEngine(bad_repo)

    network = IPv4Network("192.168.1.0/24")
    ctx = NetworkContext(
        interface_name="eth0",
        network=network,
        ipv4_address=IPv4Address("192.168.1.100"),
        gateway=IPv4Address("192.168.1.1"),
    )
    scope = NetworkScope.from_context(ctx)

    scan_ctx = ScanContext(
        scan_id=uuid4(), started_at=datetime.now(timezone.utc), network_scope=scope
    )

    sensitive_mac = "11:22:33:44:55:66"
    sensitive_ip = "192.168.1.42"

    obs = DeviceObservation(
        mac_address=sensitive_mac,
        ipv4_address=IPv4Address(sensitive_ip),
        observed_at=datetime.now(timezone.utc),
        interface_name="eth0",
    )

    # Process scan, which will trigger the injected error
    engine.process_scan(scan_ctx, [obs])

    # Check the captured logs
    for record in capture_logs.records:
        msg = record.getMessage()
        assert sensitive_mac not in msg, f"Leak: sensitive MAC found in log: {msg}"
        assert sensitive_ip not in msg, f"Leak: sensitive IP found in log: {msg}"
