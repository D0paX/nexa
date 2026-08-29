"""Enforcement Plan builder for Phase 4."""

from nexa.domain.actions import (
    ActionCapability,
    ActionRequest,
    EnforcementBinding,
    EnforcementPlan,
    QuarantinePolicy,
)


class PlanException(Exception):
    """Raised when an enforcement plan cannot be constructed."""

    pass


def build_enforcement_plan(request: ActionRequest) -> EnforcementPlan:
    """
    Constructs a structural EnforcementPlan from an authorized ActionRequest.

    This enforces the boundaries of what policy is sent to the EnforcementAdapter.
    """
    target_ip = request.target_snapshot.ip_address
    target_mac = request.target_snapshot.mac_address

    binding = EnforcementBinding(
        identity_id=request.identity_id,
        scope_id=request.target_snapshot.network_scope.interface_name,
        ip_address=target_ip,
        mac_address=target_mac,
    )

    if request.capability == ActionCapability.QUARANTINE_DEVICE:
        # Load the approved logical quarantine policy.
        # In a full system this might come from configuration
        # or the authorization context.
        # For now, we apply the default strict quarantine policy.
        gateway_ip_str = None
        if request.target_snapshot.network_scope.gateway:
            gateway_ip_str = str(request.target_snapshot.network_scope.gateway)

        policy = QuarantinePolicy(
            permit_dns=True,
            permit_dhcp=True,
            permit_gateway=True,
            permit_verifier_ips=[],  # Would be populated from Phase 2 config if needed
            gateway_ip=gateway_ip_str,
        )

        if policy.permit_gateway and not policy.gateway_ip:
            raise PlanException("Gateway IP is missing but permit_gateway is True")
        return EnforcementPlan(
            action_id=request.action_id,
            capability=request.capability,
            enforcement_bindings=[binding],
            quarantine_policy=policy,
        )

    elif request.capability == ActionCapability.RELEASE_QUARANTINE:
        return EnforcementPlan(
            action_id=request.action_id,
            capability=request.capability,
            enforcement_bindings=[binding],
            quarantine_policy=None,
        )

    elif request.capability == ActionCapability.REQUIRE_REVERIFICATION:
        # REQUIRE_REVERIFICATION doesn't require firewall state changes directly,
        # but if we route it through the adapter, it has no IPs to quarantine.
        return EnforcementPlan(
            action_id=request.action_id,
            capability=request.capability,
            enforcement_bindings=[],
            quarantine_policy=None,
        )

    raise PlanException(f"Unsupported capability: {request.capability}")
