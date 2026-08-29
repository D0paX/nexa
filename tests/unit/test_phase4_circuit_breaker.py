from nexa.actions.circuit_breaker import EnforcementCircuitBreaker


def test_circuit_breaker_halts_on_failure_threshold() -> None:
    cb = EnforcementCircuitBreaker(max_failure_threshold=3)

    cb.record_action_failure()
    cb.record_action_failure()
    assert cb.is_paused() is False

    cb.record_action_failure()
    assert cb.is_paused() is True
    assert cb.get_state().paused is True


def test_circuit_breaker_queue_saturation() -> None:
    cb = EnforcementCircuitBreaker(max_queue_depth=5)

    cb.record_queue_depth(5)

    assert cb.is_paused() is True
    assert cb.get_state().paused is True


def test_circuit_breaker_rollback_failure_threshold() -> None:
    cb = EnforcementCircuitBreaker(max_rollback_failures=2)

    cb.record_rollback_failure()
    assert cb.is_paused() is False

    cb.record_rollback_failure()
    assert cb.is_paused() is True
    assert cb.get_state().paused is True


def test_circuit_breaker_explicit_resume() -> None:
    cb = EnforcementCircuitBreaker(max_failure_threshold=1)

    cb.record_action_failure()
    assert cb.is_paused() is True

    cb.resume_enforcement()
    assert cb.is_paused() is False
    assert cb.get_state().paused is False


def test_circuit_breaker_global_pause_overrides_scope() -> None:
    cb = EnforcementCircuitBreaker()
    cb._trip("Global pause")

    assert cb.is_paused() is True
    assert cb.get_state().paused is True
