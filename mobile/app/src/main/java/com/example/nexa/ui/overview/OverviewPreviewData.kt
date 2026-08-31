package com.example.nexa.ui.overview

import com.example.nexa.theme.NexaStatus

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE.
 *
 * Every value in this file is fabricated for development. Nothing here talks
 * to Phase 1-4; no backend contract is implied or invented. It exists so the
 * command center's states can be built and reviewed before integration, and
 * it is deliberately the only file in the feature that contains literals.
 *
 * When the real read model arrives it replaces [OverviewPreview.scenario]
 * wholesale — the screen, the state model and the derivation rules are
 * already integration-shaped and do not change.
 */
object OverviewPreview {

    /**
     * The scenario the app renders during development.
     *
     * Switch this to review a state on the emulator; it has no effect on the
     * shipped behavior once a real source is wired in.
     */
    val scenario: OverviewUiState get() = enforcing()

    // --- Healthy, actively enforcing ---
    fun enforcing(): OverviewUiState {
        val enforcement = EnforcementState(
            enabled = true,
            circuitBreaker = CircuitBreakerState.Closed,
            executionMode = ExecutionMode.AuditOnly,
            quarantinedDevices = 3,
            pendingActions = 0,
            failedActions = 0,
            reconciliationIssues = 0
        )
        // No critical alert here on purpose: a critical condition outranks
        // Enforcing, so a scenario claiming to be Enforcing must not carry one.
        val alerts = AlertSummary(total = 2, critical = 0, warning = 2, unacknowledged = 2)
        return content(enforcement, alerts, DataFreshness.Live)
    }

    // --- Nothing outstanding at all ---
    fun secure(): OverviewUiState {
        val enforcement = EnforcementState(
            enabled = true,
            circuitBreaker = CircuitBreakerState.Closed,
            executionMode = ExecutionMode.AuditOnly,
            quarantinedDevices = 0,
            pendingActions = 0,
            failedActions = 0,
            reconciliationIssues = 0
        )
        val alerts = AlertSummary(total = 0, critical = 0, warning = 0, unacknowledged = 0)
        return content(
            enforcement = enforcement,
            alerts = alerts,
            freshness = DataFreshness.Live,
            activity = emptyList(),
            devices = DeviceSummary(active = 42, online = 42, offline = 0, untrusted = 0, quarantined = 0)
        )
    }

    // --- Enforcement halted by the circuit breaker ---
    fun paused(): OverviewUiState {
        val enforcement = EnforcementState(
            enabled = true,
            circuitBreaker = CircuitBreakerState.Open,
            executionMode = ExecutionMode.AuditOnly,
            quarantinedDevices = 3,
            pendingActions = 2,
            failedActions = 0,
            reconciliationIssues = 0
        )
        val alerts = AlertSummary(total = 2, critical = 0, warning = 2, unacknowledged = 1)
        return content(enforcement, alerts, DataFreshness.Live)
    }

    // --- Reduced capability ---
    fun degraded(): OverviewUiState {
        val enforcement = EnforcementState(
            enabled = true,
            circuitBreaker = CircuitBreakerState.HalfOpen,
            executionMode = ExecutionMode.AuditOnly,
            quarantinedDevices = 1,
            pendingActions = 1,
            failedActions = 0,
            reconciliationIssues = 2
        )
        val alerts = AlertSummary(total = 2, critical = 0, warning = 2, unacknowledged = 2)
        return content(enforcement, alerts, DataFreshness.Live)
    }

    // --- Something needs a person now ---
    fun critical(): OverviewUiState {
        val enforcement = EnforcementState(
            enabled = true,
            circuitBreaker = CircuitBreakerState.Closed,
            executionMode = ExecutionMode.AuditOnly,
            quarantinedDevices = 4,
            pendingActions = 1,
            failedActions = 1,
            reconciliationIssues = 0
        )
        val alerts = AlertSummary(total = 5, critical = 2, warning = 2, unacknowledged = 4)
        return content(enforcement, alerts, DataFreshness.Live)
    }

    // --- Real data, too old to act on ---
    fun stale(): OverviewUiState {
        val enforcement = EnforcementState(
            enabled = true,
            circuitBreaker = CircuitBreakerState.Closed,
            executionMode = ExecutionMode.AuditOnly,
            quarantinedDevices = 3,
            pendingActions = 0,
            failedActions = 0,
            reconciliationIssues = 0
        )
        val alerts = AlertSummary(total = 3, critical = 1, warning = 1, unacknowledged = 2)
        return content(enforcement, alerts, DataFreshness.Stale("Last confirmed 4 min ago"))
    }

    fun offline(): OverviewUiState = OverviewUiState.Offline

    fun unavailable(): OverviewUiState = OverviewUiState.Unavailable

    fun loading(): OverviewUiState = OverviewUiState.Loading

    // --- Assembly ---

    private fun content(
        enforcement: EnforcementState,
        alerts: AlertSummary,
        freshness: DataFreshness,
        activity: List<ActivityEntry> = recentActivity,
        devices: DeviceSummary = DeviceSummary(
            active = 42,
            online = 38,
            offline = 4,
            untrusted = 2,
            quarantined = enforcement.quarantinedDevices
        )
    ): OverviewUiState {
        val posture = derivePosture(enforcement, alerts, freshness)
        return OverviewUiState.Content(
            OverviewData(
                posture = posture,
                postureDetail = postureDetail(posture, enforcement),
                enforcement = enforcement,
                attention = buildAttentionItems(enforcement, alerts, criticalAlertEntries.take(alerts.critical)),
                devices = devices,
                alerts = alerts,
                activity = activity,
                freshness = freshness
            )
        )
    }

    private val criticalAlertEntries = listOf(
        ActivityEntry(
            id = "ALRT-1092",
            kind = ActivityKind.AlertRaised,
            title = "Suspicious Port Scan",
            target = "00:1A:2B:3C:4D:5E",
            timeAgo = "2m ago",
            status = NexaStatus.Critical
        ),
        ActivityEntry(
            id = "ALRT-1089",
            kind = ActivityKind.AlertRaised,
            title = "Repeated Authentication Failure",
            target = "00:9F:2C:1D:4E:7B",
            timeAgo = "21m ago",
            status = NexaStatus.Critical
        )
    )

    private val recentActivity = listOf(
        ActivityEntry(
            id = "ACT-4410",
            kind = ActivityKind.AlertRaised,
            title = "Suspicious Port Scan",
            target = "00:1A:2B:3C:4D:5E",
            timeAgo = "2m ago",
            status = NexaStatus.Critical
        ),
        ActivityEntry(
            id = "ACT-4409",
            kind = ActivityKind.EnforcementCompleted,
            title = "Quarantine applied",
            target = "00:1A:2B:3C:4D:5E",
            timeAgo = "2m ago",
            status = NexaStatus.Simulation
        ),
        ActivityEntry(
            id = "ACT-4407",
            kind = ActivityKind.TrustChanged,
            title = "Trust session expired",
            target = "00:5E:4D:3C:2B:1A",
            timeAgo = "14m ago",
            status = NexaStatus.Warning
        ),
        ActivityEntry(
            id = "ACT-4404",
            kind = ActivityKind.ReleaseCompleted,
            title = "Quarantine released",
            target = "AA:BB:CC:DD:EE:FF",
            timeAgo = "38m ago",
            status = NexaStatus.Secure
        ),
        ActivityEntry(
            id = "ACT-4401",
            kind = ActivityKind.DeviceAppeared,
            title = "New device observed",
            target = "3C:22:FB:19:04:A1",
            timeAgo = "1h ago",
            status = NexaStatus.Information
        )
    )
}
