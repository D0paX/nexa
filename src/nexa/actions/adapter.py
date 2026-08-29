"""Enforcement Adapter using nftables for Phase 4."""

import json
import logging
import subprocess
from ipaddress import IPv4Address
from typing import Dict, List

from nexa.domain.actions import (
    ActionCapability,
    EnforcementAdapter,
    EnforcementPlan,
    NormalizedState,
    QuarantinePolicy,
)

logger = logging.getLogger(__name__)


class AdapterException(Exception):
    """Raised when the enforcement adapter fails to apply state."""

    pass


class NftablesAdapter(EnforcementAdapter):
    """
    Translates EnforcementPlan into structural nftables commands.
    Ensures strict structural validation and zero shell execution.
    """

    def __init__(self, table_name: str = "nexa"):
        self.table_name = table_name

    def _validate_ip(self, ip_str: str) -> None:
        """Strict structural validation of IP address to prevent injection."""
        try:
            IPv4Address(ip_str)
        except ValueError as err:
            raise AdapterException(f"Invalid structural IP format: {ip_str}") from err

    def _execute_nft(self, args: List[str]) -> str:
        """Executes an nft command with strict structural boundaries."""
        cmd = ["nft"] + args
        try:
            # shell=False is CRITICAL for security boundary
            result = subprocess.run(
                cmd,
                check=True,
                shell=False,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                timeout=5.0,  # Bounded timeout
            )
            return result.stdout
        except subprocess.CalledProcessError as e:
            logger.error(f"nftables error: {e.stderr}")
            raise AdapterException(f"Failed to execute nft: {e.stderr}") from e
        except subprocess.TimeoutExpired as err:
            raise AdapterException("nft execution timed out.") from err
        except FileNotFoundError as err:
            raise AdapterException(
                "nft command not found. Is nftables installed?"
            ) from err

    def _ensure_base_structure(self) -> None:
        """Ensure the nexa table, quarantined_ips set, and hook chains exist."""
        self._execute_nft(["add", "table", "inet", self.table_name])

        self._execute_nft(
            [
                "add",
                "set",
                "inet",
                self.table_name,
                "quarantined_ips",
                "{",
                "type",
                "ipv4_addr;",
                "}",
            ]
        )

        self._execute_nft(["add", "chain", "inet", self.table_name, "quarantine_eval"])

        self._execute_nft(
            [
                "add",
                "chain",
                "inet",
                self.table_name,
                "quarantine_input",
                "{",
                "type",
                "filter",
                "hook",
                "input",
                "priority",
                "filter;",
                "policy",
                "accept;",
                "}",
            ]
        )
        self._execute_nft(
            [
                "add",
                "chain",
                "inet",
                self.table_name,
                "quarantine_forward",
                "{",
                "type",
                "filter",
                "hook",
                "forward",
                "priority",
                "filter;",
                "policy",
                "accept;",
                "}",
            ]
        )
        self._execute_nft(
            [
                "add",
                "chain",
                "inet",
                self.table_name,
                "quarantine_output",
                "{",
                "type",
                "filter",
                "hook",
                "output",
                "priority",
                "filter;",
                "policy",
                "accept;",
                "}",
            ]
        )

        # Ensure jump rules exist. Since 'nft add rule' appends and doesn't deduplicate
        # natively without handles, we flush our own specific hook chains and
        # recreate the jump rules idempotently.
        self._execute_nft(
            ["flush", "chain", "inet", self.table_name, "quarantine_input"]
        )
        self._execute_nft(
            ["flush", "chain", "inet", self.table_name, "quarantine_forward"]
        )
        self._execute_nft(
            ["flush", "chain", "inet", self.table_name, "quarantine_output"]
        )

        for chain in ["quarantine_input", "quarantine_forward", "quarantine_output"]:
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    chain,
                    "ip",
                    "saddr",
                    "@quarantined_ips",
                    "counter",
                    "jump",
                    "quarantine_eval",
                ]
            )
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    chain,
                    "ip",
                    "daddr",
                    "@quarantined_ips",
                    "counter",
                    "jump",
                    "quarantine_eval",
                ]
            )

    def _sync_policy_rules(self, policy: QuarantinePolicy) -> None:
        self._execute_nft(
            ["flush", "chain", "inet", self.table_name, "quarantine_eval"]
        )

        # 1. Connection Tracking (Established/Related)
        self._execute_nft(
            [
                "add",
                "rule",
                "inet",
                self.table_name,
                "quarantine_eval",
                "ct",
                "state",
                "established,related",
                "accept",
            ]
        )

        # 2. Explicit permits
        if policy.permit_dns:
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "udp",
                    "dport",
                    "53",
                    "accept",
                ]
            )
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "tcp",
                    "dport",
                    "53",
                    "accept",
                ]
            )

        if policy.permit_dhcp:
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "udp",
                    "dport",
                    "67",
                    "accept",
                ]
            )
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "udp",
                    "dport",
                    "68",
                    "accept",
                ]
            )

        if policy.permit_gateway:
            if not policy.gateway_ip:
                raise AdapterException(
                    "Gateway IP is missing but permit_gateway is True"
                )
            self._validate_ip(policy.gateway_ip)
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "ip",
                    "daddr",
                    policy.gateway_ip,
                    "accept",
                ]
            )
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "ip",
                    "saddr",
                    policy.gateway_ip,
                    "accept",
                ]
            )

        for v_ip in policy.permit_verifier_ips:
            self._validate_ip(v_ip)
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "ip",
                    "daddr",
                    v_ip,
                    "accept",
                ]
            )
            self._execute_nft(
                [
                    "add",
                    "rule",
                    "inet",
                    self.table_name,
                    "quarantine_eval",
                    "ip",
                    "saddr",
                    v_ip,
                    "accept",
                ]
            )

        # 3. Default drop
        self._execute_nft(
            [
                "add",
                "rule",
                "inet",
                self.table_name,
                "quarantine_eval",
                "counter",
                "drop",
            ]
        )

    def apply(self, plan: EnforcementPlan) -> None:
        """Apply an enforcement plan."""
        if plan.capability == ActionCapability.QUARANTINE_DEVICE:
            self._ensure_base_structure()
            if plan.quarantine_policy:
                self._sync_policy_rules(plan.quarantine_policy)

            for binding in plan.enforcement_bindings:
                self._validate_ip(binding.ip_address)

                comment = (
                    f"{binding.identity_id}:{binding.scope_id}:"
                    f"{binding.ip_address}:{binding.mac_address}"
                )

                self._execute_nft(
                    [
                        "add",
                        "element",
                        "inet",
                        self.table_name,
                        "quarantined_ips",
                        "{",
                        binding.ip_address,
                        "comment",
                        f'"{comment}"',
                        "}",
                    ]
                )

        elif plan.capability == ActionCapability.RELEASE_QUARANTINE:
            self.release(plan)
        elif plan.capability == ActionCapability.REQUIRE_REVERIFICATION:
            pass
        else:
            raise AdapterException(f"Unsupported capability: {plan.capability}")

    def release(self, plan: EnforcementPlan) -> None:
        """Revert the state changes applied by a plan."""
        if plan.capability in (
            ActionCapability.QUARANTINE_DEVICE,
            ActionCapability.RELEASE_QUARANTINE,
        ):
            for binding in plan.enforcement_bindings:
                self._validate_ip(binding.ip_address)

                try:
                    self._execute_nft(
                        [
                            "delete",
                            "element",
                            "inet",
                            self.table_name,
                            "quarantined_ips",
                            "{",
                            binding.ip_address,
                            "}",
                        ]
                    )
                except AdapterException:
                    pass
        elif plan.capability == ActionCapability.REQUIRE_REVERIFICATION:
            pass

    def inspect(self) -> Dict[str, NormalizedState]:
        """
        Inspect actual OS state and return normalized managed target bindings.
        Keys are a composite string of the TargetBinding elements.
        """
        try:
            # -j for JSON output
            output = self._execute_nft(["-j", "list", "table", "inet", self.table_name])
        except AdapterException:
            # If adapter fails, return unavailable
            return {"*": NormalizedState.ADAPTER_UNAVAILABLE}

        states = {}
        try:
            data = json.loads(output)
            nftables_data = data.get("nftables", [])

            has_set = False
            has_eval_chain = False
            quarantine_ips = []

            for item in nftables_data:
                if "set" in item:
                    set_data = item["set"]
                    if set_data.get("name") == "quarantined_ips":
                        has_set = True
                        if "elem" in set_data:
                            for elem in set_data["elem"]:
                                if isinstance(elem, dict) and "elem" in elem:
                                    val = elem["elem"]
                                    if "val" in val and "comment" in val:
                                        comment = val["comment"]
                                        comment = comment.strip('"')
                                        quarantine_ips.append(comment)
                elif "chain" in item:
                    if item["chain"].get("name") == "quarantine_eval":
                        has_eval_chain = True

            # If structure is missing, any elements we found are effectively ABSENT
            if not has_set or not has_eval_chain:
                return {}

            # Otherwise, active enforcement is in place
            for comment in quarantine_ips:
                states[comment] = NormalizedState.PRESENT

        except Exception as e:
            logger.error(f"Failed to parse nftables inspect output: {e}")

        return states
