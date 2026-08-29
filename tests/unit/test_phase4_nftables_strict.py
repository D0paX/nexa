import uuid
from unittest.mock import MagicMock, patch

import pytest

from nexa.actions.adapter import AdapterException, NftablesAdapter
from nexa.domain.actions import (
    ActionCapability,
    EnforcementBinding,
    EnforcementPlan,
    QuarantinePolicy,
)


def test_nftables_strict_shell_false() -> None:
    adapter = NftablesAdapter()

    plan = EnforcementPlan(
        action_id=uuid.uuid4(),
        capability=ActionCapability.QUARANTINE_DEVICE,
        enforcement_bindings=[
            EnforcementBinding(
                identity_id=uuid.uuid4(),
                scope_id="GUEST",
                ip_address="192.168.1.100",
                mac_address="AA:BB:CC:DD:EE:FF",
            )
        ],
        quarantine_policy=QuarantinePolicy(
            permit_dns=True,
            permit_dhcp=True,
            permit_gateway=True,
            permit_verifier_ips=[],
            gateway_ip="192.168.1.1",
        ),
    )

    with patch("subprocess.run") as mock_run:
        adapter.apply(plan)

        assert mock_run.call_count > 1

        for args, _kwargs in mock_run.call_args_list:
            # Verify shell=False
            assert _kwargs.get("shell", False) is False

            # Verify literal arguments
            cmd_args = args[0]
            assert isinstance(cmd_args, list)
            assert cmd_args[0] == "nft"


def test_nftables_no_arbitrary_syntax_or_flush() -> None:
    adapter = NftablesAdapter()

    plan = EnforcementPlan(
        action_id=uuid.uuid4(),
        capability=ActionCapability.QUARANTINE_DEVICE,
        enforcement_bindings=[
            EnforcementBinding(
                identity_id=uuid.uuid4(),
                scope_id="GUEST",
                ip_address="192.168.1.100; flush ruleset",  # Malicious payload
                mac_address="AA:BB:CC:DD:EE:FF",
            )
        ],
        quarantine_policy=QuarantinePolicy(gateway_ip="192.168.1.1"),
    )

    with patch("subprocess.run") as mock_run:
        with pytest.raises(AdapterException):
            adapter.apply(plan)
        for args, _kwargs in mock_run.call_args_list:
            cmd_args = args[0]
            if "element" in cmd_args:
                raise AssertionError(
                    "Should not reach element addition with malicious IP"
                )


def test_nftables_inspect_normalized() -> None:
    adapter = NftablesAdapter()

    with patch("subprocess.run") as mock_run:
        mock_run.return_value = MagicMock(
            stdout='{"nftables": [{"set": {"name": "quarantined_ips", '
            '"elem": [{"elem": {"val": "192.168.1.100", "comment": '
            '"test_comment"}}]}}, {"chain": {"name": "quarantine_eval"}}]}'
        )
        state = adapter.inspect()
        assert "test_comment" in state


def test_nftables_rejects_include_directives() -> None:
    adapter = NftablesAdapter()

    plan = EnforcementPlan(
        action_id=uuid.uuid4(),
        capability=ActionCapability.QUARANTINE_DEVICE,
        enforcement_bindings=[
            EnforcementBinding(
                identity_id=uuid.uuid4(),
                scope_id="GUEST",
                ip_address='192.168.1.100" include "/etc/shadow"',  # Malicious
                mac_address="AA:BB:CC:DD:EE:FF",
            )
        ],
        quarantine_policy=QuarantinePolicy(gateway_ip="192.168.1.1"),
    )

    with patch("subprocess.run") as mock_run:
        with pytest.raises(AdapterException):
            adapter.apply(plan)
        for args, _kwargs in mock_run.call_args_list:
            cmd_args = args[0]
            if "element" in cmd_args:
                raise AssertionError(
                    "Should not reach element addition with malicious IP"
                )
