"""Authorization engine for Phase 4 enforcement actions."""

from datetime import datetime, timezone

from nexa.domain.actions import ActionCapability, ActionRequest, OperatorApprovalMode
from nexa.domain.trust import TrustState


class AuthorizationException(Exception):
    """Raised when an action is denied authorization."""

    pass


class AuthorizationEngine:
    """Evaluates action requests against security policies before enforcement."""

    def __init__(self, freshness_max_age_seconds: int = 300) -> None:
        self.freshness_max_age_seconds = freshness_max_age_seconds

    def evaluate(
        self,
        request: ActionRequest,
        current_trust_state: TrustState,
        approval_mode: OperatorApprovalMode,
    ) -> bool:
        """
        Evaluate an ActionRequest for execution authorization.

        Must verify:
        - current TrustState
        - TargetSnapshot freshness
        - operator approval mode constraints
        - capability constraints

        Returns True if authorized, raises AuthorizationException otherwise.
        """
        # Fail closed
        if current_trust_state == TrustState.REVOKED:
            raise AuthorizationException("Identity is revoked. Action denied.")

        if current_trust_state == TrustState.UNKNOWN:
            raise AuthorizationException("Identity is unknown. Action denied.")

        # Freshness bounds check
        now = datetime.now(timezone.utc)
        target_age = (
            now - request.target_snapshot.observation_timestamp
        ).total_seconds()
        if target_age > self.freshness_max_age_seconds:
            raise AuthorizationException(
                f"TargetSnapshot is stale ({target_age}s > "
                f"{self.freshness_max_age_seconds}s). Action denied."
            )

        # Operator approval mode evaluation
        # Operator approval mode evaluation
        if approval_mode in (
            OperatorApprovalMode.OPERATOR_REQUIRED,
            OperatorApprovalMode.OPERATOR_ONLY,
        ):
            if not request.operator_id:
                raise AuthorizationException(
                    "Operator approval required but operator_id is missing."
                )

            # Additional constraint: Bind approval to specific fields in the context
            context = request.authorization_context
            if "approval" not in context:
                raise AuthorizationException(
                    "Missing operator approval block in authorization context."
                )

            approval = context["approval"]
            required_keys = [
                "action_id",
                "identity_id",
                "capability",
                "operator_id",
                "timestamp",
            ]
            for k in required_keys:
                if k not in approval:
                    raise AuthorizationException(
                        f"Approval binding missing required field: {k}"
                    )

            if str(approval["action_id"]) != str(request.action_id):
                raise AuthorizationException(
                    "Approval action_id does not match request."
                )
            if str(approval["identity_id"]) != str(request.identity_id):
                raise AuthorizationException(
                    "Approval identity_id does not match request."
                )
            if str(approval["capability"]) != request.capability.value:
                raise AuthorizationException(
                    "Approval capability does not match request."
                )
            if str(approval["operator_id"]) != str(request.operator_id):
                raise AuthorizationException(
                    "Approval operator_id does not match request operator."
                )

            # Timestamp check
            approval_time = datetime.fromisoformat(approval["timestamp"]).replace(
                tzinfo=timezone.utc
            )
            if (now - approval_time).total_seconds() > 3600:
                raise AuthorizationException(
                    "Operator approval is older than 1 hour (expired)."
                )

        # Capability specific checks
        if request.capability == ActionCapability.QUARANTINE_DEVICE:
            # Maybe restrict QUARANTINE_DEVICE based on trust state or scope if needed.
            # But the policy dictates what can happen.
            pass

        return True
