"""Action Executor for Phase 4."""

import logging
import uuid
from dataclasses import replace
from typing import Any, Optional

from nexa.actions.circuit_breaker import EnforcementCircuitBreaker
from nexa.actions.plan import build_enforcement_plan
from nexa.domain.actions import (
    ActionCapability,
    ActionExecution,
    ActionRepository,
    ActionRequest,
    EnforcementAdapter,
    EnforcementMode,
    EnforcementOwnership,
    EnforcementStateRepository,
    ExecutionState,
    NormalizedState,
)
from nexa.domain.trust import TrustState
from nexa.domain.trust_lifecycle import TrustSessionManager

logger = logging.getLogger(__name__)


class ExecutorException(Exception):
    """Base exception for ActionExecutor failures."""

    pass


class ActionExecutor:
    """
    Safely orchestrates the execution, tracking, and rollback of enforcement actions.
    """

    def __init__(
        self,
        repository: ActionRepository,
        enforcement_state_repo: EnforcementStateRepository,
        adapter: EnforcementAdapter,
        circuit_breaker: EnforcementCircuitBreaker,
        trust_manager: TrustSessionManager,
        event_aggregator: Any,  # Phase 3 Boundary
        enforcement_mode: EnforcementMode = EnforcementMode.AUDIT_ONLY,
    ):
        self.repository = repository
        self.enforcement_state_repo = enforcement_state_repo
        self.adapter = adapter
        self.circuit_breaker = circuit_breaker
        self.trust_manager = trust_manager
        self.event_aggregator = event_aggregator
        self.enforcement_mode = enforcement_mode

    def execute_action(self, request: ActionRequest) -> ActionExecution:
        """
        Executes an authorized ActionRequest.
        Provides idempotency, circuit breaking, and state tracking.
        """
        if self.circuit_breaker.is_paused():
            raise ExecutorException("Enforcement is paused. Action rejected.")

        existing = self.repository.get_execution(request.action_id)
        if existing:
            if existing.state in (ExecutionState.SUCCEEDED, ExecutionState.EXECUTING):
                return existing
            if existing.state == ExecutionState.FAILED:
                raise ExecutorException(
                    f"Action {request.action_id} previously failed."
                )
            execution = existing
            execution = self._transition(execution, ExecutionState.EXECUTING)
        else:
            execution = ActionExecution(
                action_id=request.action_id,
                request=request,
                state=ExecutionState.REQUESTED,
                operator_id=request.operator_id,
            )
            self.repository.save_execution(execution)
            execution = self._transition(execution, ExecutionState.AUTHORIZED)
            execution = self._transition(execution, ExecutionState.EXECUTING)

        try:
            plan = build_enforcement_plan(request)
            execution = replace(execution, enforcement_plan=plan)
            self.repository.save_execution(execution)
        except Exception as e:
            logger.error(
                f"Failed to build enforcement plan for {request.action_id}: {e}"
            )
            execution = self._transition(execution, ExecutionState.FAILED)
            self.circuit_breaker.record_action_failure()
            return execution

        try:
            trust_repo = getattr(
                self.trust_manager,
                "trust_repo",
                getattr(self.trust_manager, "_trust_repo", None),
            )
            if trust_repo:
                identity = trust_repo.get_identity(str(request.identity_id))
                if not identity or identity.state == TrustState.REVOKED:
                    logger.warning(
                        f"Identity {request.identity_id} is REVOKED. Aborting."
                    )
                    execution = self._transition(execution, ExecutionState.FAILED)
                    return execution
        except Exception as e:
            logger.warning(f"Could not verify trust state for revocation race: {e}")

        # Target-Scoped Serialization
        locks_acquired = []
        try:
            if plan.capability in (
                ActionCapability.QUARANTINE_DEVICE,
                ActionCapability.RELEASE_QUARANTINE,
            ):
                for binding in plan.enforcement_bindings:
                    acquired = self.enforcement_state_repo.acquire_target_lock(
                        binding.scope_id, binding.ip_address
                    )
                    if not acquired:
                        raise ExecutorException(
                            f"Failed to acquire lock for {binding.scope_id}:"
                            f"{binding.ip_address}"
                        )
                    locks_acquired.append((binding.scope_id, binding.ip_address))

            execution = self._execute_inner(execution, plan)

        except Exception as e:
            logger.error(f"Enforcement failed for {request.action_id}: {e}")
            execution = self._transition(execution, ExecutionState.FAILED)
            self.circuit_breaker.record_action_failure()
        finally:
            for scope_id, ip_address in locks_acquired:
                self.enforcement_state_repo.release_target_lock(scope_id, ip_address)

        return execution

    def _execute_inner(self, execution: ActionExecution, plan: Any) -> ActionExecution:
        request = execution.request

        if request.capability == ActionCapability.REQUIRE_REVERIFICATION:
            self.trust_manager.require_reverification(
                identity_id=str(request.identity_id),
                reason=f"ActionRequest {request.action_id}",
            )
            execution = self._transition(execution, ExecutionState.SUCCEEDED)
            self.circuit_breaker.record_action_success()
            return execution

        if self.enforcement_mode == EnforcementMode.AUDIT_ONLY:
            audit_refs = execution.audit_references.copy()
            audit_refs.append("execution_mode: AUDIT_ONLY")
            audit_refs.append("simulated: true")
            audit_refs.append("mutation_performed: false")
            execution = replace(execution, audit_references=audit_refs)
            execution = self._transition(execution, ExecutionState.SUCCEEDED)
            return execution

        # Apply Quarantine
        if plan.capability == ActionCapability.QUARANTINE_DEVICE:
            for binding in plan.enforcement_bindings:
                current_ownership = self.enforcement_state_repo.get_ownership(
                    binding.scope_id, binding.ip_address
                )
                binding_id = (
                    f"{binding.identity_id}:{binding.scope_id}:"
                    f"{binding.ip_address}:{binding.mac_address}"
                )

                if current_ownership:
                    if (
                        str(current_ownership.identity_id) != str(binding.identity_id)
                        or current_ownership.enforcement_binding_id != binding_id
                    ):
                        raise ExecutorException(
                            f"Conflict: IP {binding.ip_address} is already owned by "
                            f"{current_ownership.identity_id}"
                        )
                    # Already owned by us, treat as idempotent success if OS matches

            self.adapter.apply(plan)

            # Inspect actual state
            self.adapter.inspect()

            # Finalize ownership
            for binding in plan.enforcement_bindings:
                binding_id = (
                    f"{binding.identity_id}:{binding.scope_id}:"
                    f"{binding.ip_address}:{binding.mac_address}"
                )
                ownership = EnforcementOwnership(
                    identity_id=binding.identity_id,
                    scope_id=binding.scope_id,
                    ip_address=binding.ip_address,
                    mac_address=binding.mac_address,
                    enforcement_binding_id=binding_id,
                    state=NormalizedState.PRESENT,
                )
                self.enforcement_state_repo.save_ownership(ownership)

        # Release Quarantine
        elif plan.capability == ActionCapability.RELEASE_QUARANTINE:
            for binding in plan.enforcement_bindings:
                current_ownership = self.enforcement_state_repo.get_ownership(
                    binding.scope_id, binding.ip_address
                )
                binding_id = (
                    f"{binding.identity_id}:{binding.scope_id}:"
                    f"{binding.ip_address}:{binding.mac_address}"
                )

                if (
                    not current_ownership
                    or current_ownership.enforcement_binding_id != binding_id
                ):
                    self._dispatch_rollback_failure_event(
                        execution, f"Stale-IP Release Rejected for {binding.ip_address}"
                    )
                    raise ExecutorException(
                        f"Release rejected: IP {binding.ip_address} "
                        "is not owned by the requested binding"
                    )

            self.adapter.release(plan)

            for binding in plan.enforcement_bindings:
                self.enforcement_state_repo.remove_ownership(
                    binding.scope_id, binding.ip_address
                )

        execution = self._transition(execution, ExecutionState.SUCCEEDED)
        self.circuit_breaker.record_action_success()
        return execution

    def rollback_action(
        self, action_id: uuid.UUID, requesting_operator: Optional[str] = None
    ) -> ActionExecution:
        """
        Attempts to revert a previously successful action.
        """
        if self.circuit_breaker.is_paused():
            raise ExecutorException("Enforcement is paused. Rollback rejected.")

        execution = self.repository.get_execution(action_id)
        if not execution:
            raise ExecutorException(f"Action {action_id} not found.")

        if execution.state != ExecutionState.SUCCEEDED:
            raise ExecutorException(
                f"Cannot rollback action in state {execution.state.value}"
            )

        execution = self._transition(execution, ExecutionState.ROLLBACK_REQUESTED)

        locks_acquired = []
        try:
            plan = build_enforcement_plan(execution.request)

            if execution.request.capability != ActionCapability.REQUIRE_REVERIFICATION:
                if self.enforcement_mode == EnforcementMode.AUDIT_ONLY:
                    audit_refs = execution.audit_references.copy()
                    audit_refs.append("execution_mode: AUDIT_ONLY")
                    audit_refs.append("simulated: true")
                    audit_refs.append("mutation_performed: false")
                    audit_refs.append("rollback: true")
                    execution = replace(execution, audit_references=audit_refs)
                else:
                    for binding in plan.enforcement_bindings:
                        acquired = self.enforcement_state_repo.acquire_target_lock(
                            binding.scope_id, binding.ip_address
                        )
                        if not acquired:
                            raise ExecutorException(
                                f"Failed to acquire lock for {binding.scope_id}:"
                                f"{binding.ip_address}"
                            )
                        locks_acquired.append((binding.scope_id, binding.ip_address))

                    for binding in plan.enforcement_bindings:
                        current_ownership = self.enforcement_state_repo.get_ownership(
                            binding.scope_id, binding.ip_address
                        )
                        binding_id = (
                            f"{binding.identity_id}:{binding.scope_id}:"
                            f"{binding.ip_address}:{binding.mac_address}"
                        )

                        if (
                            not current_ownership
                            or current_ownership.enforcement_binding_id != binding_id
                        ):
                            raise ExecutorException(
                                f"Rollback rejected: IP {binding.ip_address} is "
                                f"not owned by the requested binding"
                            )

                    self.adapter.release(plan)

                    for binding in plan.enforcement_bindings:
                        self.enforcement_state_repo.remove_ownership(
                            binding.scope_id, binding.ip_address
                        )

            execution = self._transition(execution, ExecutionState.ROLLED_BACK)

        except Exception as e:
            logger.error(f"Rollback failed for {action_id}: {e}")
            execution = self._transition(execution, ExecutionState.ROLLBACK_FAILED)
            self.circuit_breaker.record_rollback_failure()
            self._dispatch_rollback_failure_event(execution, str(e))
        finally:
            for scope_id, ip_address in locks_acquired:
                self.enforcement_state_repo.release_target_lock(scope_id, ip_address)

        return execution

    def reconcile_crashes(self) -> None:
        """
        Deterministic recovery for actions stuck in EXECUTING.
        """
        active_executions = self.repository.get_active_executions()
        normalized_states = self.adapter.inspect()

        for execution in active_executions:
            if execution.state == ExecutionState.EXECUTING:
                logger.warning(f"Reconciling crashed action {execution.action_id}")
                execution = self._transition(execution, ExecutionState.RECONCILING)

                try:
                    plan = build_enforcement_plan(execution.request)
                    if plan.capability == ActionCapability.QUARANTINE_DEVICE:
                        reconciled_succeeded = True
                        for binding in plan.enforcement_bindings:
                            # Verify if nftables state exists and matches the
                            # expected binding ID
                            binding_id = (
                                f"{binding.identity_id}:{binding.scope_id}:"
                                f"{binding.ip_address}:{binding.mac_address}"
                            )
                            current_ownership = (
                                self.enforcement_state_repo.get_ownership(
                                    binding.scope_id, binding.ip_address
                                )
                            )

                            # B: mutation occurred, persistence missing
                            if (
                                not current_ownership
                                and normalized_states.get(binding_id)
                                == NormalizedState.PRESENT
                            ):
                                # Safe to claim ownership because binding_id matches
                                # our identity and target
                                pass

                            # A: intent persisted, mutation never occurred
                            elif (
                                not current_ownership
                                and normalized_states.get(binding_id)
                                != NormalizedState.PRESENT
                            ):
                                reconciled_succeeded = False
                                break

                            # B: mutation occurred, final persistence missing or E:
                            # metadata disagree
                            if (
                                current_ownership
                                and current_ownership.enforcement_binding_id
                                != binding_id
                            ):
                                reconciled_succeeded = False
                                break

                            if (
                                current_ownership
                                and normalized_states.get(binding_id)
                                != NormalizedState.PRESENT
                            ):
                                # D: ownership exists without firewall state
                                reconciled_succeeded = False
                                break

                        if reconciled_succeeded and plan.enforcement_bindings:
                            # Complete the finalization if needed
                            for binding in plan.enforcement_bindings:
                                binding_id = (
                                    f"{binding.identity_id}:{binding.scope_id}:"
                                    f"{binding.ip_address}:{binding.mac_address}"
                                )
                                current = self.enforcement_state_repo.get_ownership(
                                    binding.scope_id, binding.ip_address
                                )
                                if not current:
                                    ownership = EnforcementOwnership(
                                        identity_id=binding.identity_id,
                                        scope_id=binding.scope_id,
                                        ip_address=binding.ip_address,
                                        mac_address=binding.mac_address,
                                        enforcement_binding_id=binding_id,
                                        state=NormalizedState.PRESENT,
                                    )
                                    self.enforcement_state_repo.save_ownership(
                                        ownership
                                    )
                            self._transition(execution, ExecutionState.SUCCEEDED)
                        else:
                            self._transition(execution, ExecutionState.FAILED)
                    else:
                        self._transition(execution, ExecutionState.FAILED)
                except Exception as e:
                    logger.error(
                        f"Failed to reconcile crashed action {execution.action_id}: {e}"
                    )
                    self._transition(execution, ExecutionState.FAILED)

    def _transition(
        self, execution: ActionExecution, new_state: ExecutionState
    ) -> ActionExecution:
        """Update the execution state and persist."""
        execution = execution.transition_to(new_state)
        self.repository.save_execution(execution)
        return execution

    def _dispatch_rollback_failure_event(
        self, execution: ActionExecution, error_msg: str
    ) -> None:
        """Dispatch a Phase 3 SecurityEvent for rollback failure."""
        from nexa.domain.events import SecurityEvent, Severity

        event = SecurityEvent(
            event_id=uuid.uuid4(),
            event_class="SYSTEM_ERROR",
            severity=Severity.CRITICAL,
            identity_id=execution.request.identity_id,
            context={
                "description": (
                    f"Phase 4 Enforcement Rollback Failed for action "
                    f"{execution.action_id}: {error_msg}"
                ),
                "action_id": str(execution.action_id),
                "error": error_msg,
            },
        )
        try:
            self.event_aggregator.dispatch(event)
        except Exception as ex:
            logger.error(f"Failed to dispatch SecurityEvent: {ex}")
