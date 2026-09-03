package com.example.nexa.ui.audit

import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE
 *
 * Every record below is fabricated for interface development. None of it was
 * produced by an observation, an alert, a trust operation or an execution, and
 * nothing here executes anything. It exists so the history surface can be
 * built and reviewed before a real event source is connected.
 *
 * The sequences are deliberately believable: an alert is raised before it is
 * acknowledged, a request is authorized before it executes, a rollback follows
 * a failure. Simulated runs stay simulated all the way through, and never
 * produce a reconciliation or a binding — a run that mutates nothing has
 * nothing to reconcile.
 */
object AuditPreview {

    // ============================================================
    // TARGETS — the same identifiers the other preview sources use
    // ============================================================

    private val unknownDevice = AuditTarget.Device(
        deviceId = "DEV-1002",
        label = "Unknown Device",
        mac = "00:5E:4D:3C:2B:1A",
        ip = "10.20.9.55",
        scope = "VLAN_GUEST"
    )

    private val buildServer = AuditTarget.Device(
        deviceId = "DEV-1003",
        label = "Build Server",
        mac = "3C:22:FB:19:04:A1",
        ip = "10.20.7.12",
        scope = "VLAN_BUILD"
    )

    private val tablet = AuditTarget.Device(
        deviceId = "DEV-1004",
        label = "Reception Tablet",
        mac = "AA:BB:CC:DD:EE:FF",
        ip = "10.20.9.31",
        scope = "VLAN_GUEST"
    )

    private val labController = AuditTarget.Device(
        deviceId = "DEV-1005",
        label = "Lab Controller",
        mac = "9C:2F:1D:44:0B:77",
        ip = "10.20.5.90",
        scope = "VLAN_SECURE"
    )

    private val displayIdentity = AuditTarget.Identity(
        identityId = "TID-9E12",
        label = "Conference Display",
        scope = "VLAN_GUEST"
    )

    private val breaker = AuditTarget.Subsystem("Enforcement circuit breaker")

    private const val QUARANTINE = "QUARANTINE_DEVICE"
    private const val RELEASE = "RELEASE_QUARANTINE"
    private const val REVERIFY = "REQUIRE_REVERIFICATION"

    // ============================================================
    // SEQUENCES
    // ============================================================

    /**
     * A live quarantine, end to end.
     *
     * Observation, alert, notification, acknowledgement, request,
     * authorization, execution, binding, reconciliation, success, resolution.
     * Reconciliation precedes success because Phase 4 confirms the resulting
     * state before it reports one.
     */
    private val liveQuarantineSequence = listOf(
        entry(
            id = "EVT-4401", type = AuditEventType.DeviceObserved, target = unknownDevice,
            outcome = AuditOutcome.Informational, ageMinutes = 96, sequence = 950,
            source = AuditSource.Observation,
            note = "First observation in this network scope."
        ),
        entry(
            id = "EVT-4402", type = AuditEventType.AlertRaised, target = unknownDevice,
            outcome = AuditOutcome.Informational, ageMinutes = 92, sequence = 953,
            source = AuditSource.SecurityEvent, alertId = "ALRT-1092",
            correlationId = "ALRT-1092"
        ),
        entry(
            id = "EVT-4403", type = AuditEventType.NotificationSent, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 91, sequence = 954,
            source = AuditSource.NotificationService, alertId = "ALRT-1092",
            correlationId = "ALRT-1092", note = "Channel: operator console"
        ),
        entry(
            id = "EVT-4404", type = AuditEventType.NotificationDelivered, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 91, sequence = 955,
            source = AuditSource.NotificationService, alertId = "ALRT-1092",
            correlationId = "ALRT-1092"
        ),
        entry(
            id = "EVT-4405", type = AuditEventType.AlertAcknowledged, target = unknownDevice,
            outcome = AuditOutcome.Informational, ageMinutes = 88, sequence = 958,
            source = AuditSource.Alert, alertId = "ALRT-1092", correlationId = "ALRT-1092",
            previousState = "NEW", resultingState = "ACKNOWLEDGED"
        ),
        entry(
            id = "EVT-4406", type = AuditEventType.ActionRequested, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 86, sequence = 961,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8871", actionCode = QUARANTINE, alertId = "ALRT-1092"
        ),
        entry(
            id = "EVT-4407", type = AuditEventType.ActionAuthorized, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 86, sequence = 962,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8871", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4408", type = AuditEventType.ActionExecuting, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 85, sequence = 963,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8871", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4409", type = AuditEventType.EnforcementBindingCreated, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 85, sequence = 964,
            source = AuditSource.EnforcementSubsystem, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8871", actionCode = QUARANTINE,
            resultingState = "QUARANTINED", note = "Binding owner scope: VLAN_GUEST"
        ),
        entry(
            id = "EVT-4410", type = AuditEventType.ActionReconciled, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 84, sequence = 965,
            source = AuditSource.EnforcementSubsystem, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8871", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4411", type = AuditEventType.ActionSucceeded, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 84, sequence = 966,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8871", actionCode = QUARANTINE,
            previousState = "NORMAL", resultingState = "QUARANTINED"
        ),
        entry(
            id = "EVT-4412", type = AuditEventType.AlertResolved, target = unknownDevice,
            outcome = AuditOutcome.Informational, ageMinutes = 80, sequence = 970,
            source = AuditSource.Alert, alertId = "ALRT-1092", correlationId = "ALRT-1092",
            previousState = "ACKNOWLEDGED", resultingState = "RESOLVED"
        )
    )

    /**
     * A simulated release.
     *
     * Reaches a successful outcome without a binding removal or a
     * reconciliation, because neither can exist for a run that touched no
     * kernel state. The device stayed quarantined throughout.
     */
    private val simulatedReleaseSequence = listOf(
        entry(
            id = "EVT-4430", type = AuditEventType.ActionRequested, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 40, sequence = 1002,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.AuditOnly,
            correlationId = "ACT-9004", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4431", type = AuditEventType.ActionAuthorized, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 39, sequence = 1003,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.AuditOnly,
            correlationId = "ACT-9004", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4432", type = AuditEventType.ActionExecuting, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 39, sequence = 1004,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.AuditOnly,
            correlationId = "ACT-9004", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4433", type = AuditEventType.ActionSucceeded, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 38, sequence = 1005,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.AuditOnly,
            correlationId = "ACT-9004", actionCode = RELEASE,
            previousState = "QUARANTINED", resultingState = "QUARANTINED"
        )
    )

    /** A simulated quarantine that failed. Nothing was mutated either way. */
    private val simulatedFailureSequence = listOf(
        entry(
            id = "EVT-4440", type = AuditEventType.ActionRequested, target = labController,
            outcome = AuditOutcome.Pending, ageMinutes = 43, sequence = 999,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.AuditOnly,
            correlationId = "ACT-9008", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4441", type = AuditEventType.ActionFailed, target = labController,
            outcome = AuditOutcome.Failed, ageMinutes = 42, sequence = 1000,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.AuditOnly,
            correlationId = "ACT-9008", actionCode = QUARANTINE,
            note = "Simulated rule set did not converge."
        )
    )

    /** A live failure with a successful rollback. */
    private val rollbackSequence = listOf(
        entry(
            id = "EVT-4450", type = AuditEventType.ActionRequested, target = tablet,
            outcome = AuditOutcome.Pending, ageMinutes = 32, sequence = 1017,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9102", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4451", type = AuditEventType.ActionAuthorized, target = tablet,
            outcome = AuditOutcome.Pending, ageMinutes = 31, sequence = 1018,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9102", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4452", type = AuditEventType.ActionExecuting, target = tablet,
            outcome = AuditOutcome.Pending, ageMinutes = 31, sequence = 1019,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9102", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4453", type = AuditEventType.ActionFailed, target = tablet,
            outcome = AuditOutcome.Failed, ageMinutes = 30, sequence = 1020,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9102", actionCode = QUARANTINE,
            note = "Rule commit rejected by the enforcement backend."
        ),
        entry(
            id = "EVT-4454", type = AuditEventType.RollbackRequested, target = tablet,
            outcome = AuditOutcome.Pending, ageMinutes = 29, sequence = 1021,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9102", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4455", type = AuditEventType.RollbackCompleted, target = tablet,
            outcome = AuditOutcome.Succeeded, ageMinutes = 28, sequence = 1022,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9102", actionCode = QUARANTINE,
            resultingState = "NORMAL"
        )
    )

    /** A live failure whose rollback also failed — the worst outcome recorded. */
    private val rollbackFailureSequence = listOf(
        entry(
            id = "EVT-4460", type = AuditEventType.ActionRequested, target = buildServer,
            outcome = AuditOutcome.Pending, ageMinutes = 26, sequence = 1026,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9110", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4461", type = AuditEventType.ActionExecuting, target = buildServer,
            outcome = AuditOutcome.Pending, ageMinutes = 25, sequence = 1027,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9110", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4462", type = AuditEventType.ActionFailed, target = buildServer,
            outcome = AuditOutcome.Failed, ageMinutes = 24, sequence = 1028,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9110", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4463", type = AuditEventType.RollbackRequested, target = buildServer,
            outcome = AuditOutcome.Pending, ageMinutes = 23, sequence = 1029,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9110", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4464", type = AuditEventType.RollbackFailed, target = buildServer,
            outcome = AuditOutcome.Failed, ageMinutes = 22, sequence = 1030,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9110", actionCode = QUARANTINE,
            note = "Prior state not restored. Operator attention required."
        )
    )

    /** A live run whose outcome NEXA never learned. It stays unknown. */
    private val unknownOutcomeSequence = listOf(
        entry(
            id = "EVT-4470", type = AuditEventType.ActionRequested, target = labController,
            outcome = AuditOutcome.Pending, ageMinutes = 14, sequence = 1043,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9127", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4471", type = AuditEventType.ActionExecuting, target = labController,
            outcome = AuditOutcome.Pending, ageMinutes = 13, sequence = 1044,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9127", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4472", type = AuditEventType.ActionOutcomeUnknown, target = labController,
            outcome = AuditOutcome.Unknown, ageMinutes = 12, sequence = 1045,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9127", actionCode = QUARANTINE,
            note = "Executor did not report a terminal state before the connection was lost."
        )
    )

    /** A request the authorization engine refused. Execution never started. */
    private val deniedSequence = listOf(
        entry(
            id = "EVT-4480", type = AuditEventType.ActionRequested, target = buildServer,
            outcome = AuditOutcome.Pending, ageMinutes = 59, sequence = 986,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8990", actionCode = QUARANTINE
        ),
        entry(
            id = "EVT-4481", type = AuditEventType.ActionDenied, target = buildServer,
            outcome = AuditOutcome.Failed, ageMinutes = 58, sequence = 987,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-8990", actionCode = QUARANTINE,
            note = "Requested scope is outside the caller's enforcement ownership."
        )
    )

    /**
     * The trust lifecycle of one identity.
     *
     * Creation and verification happened the previous day; the reverification,
     * the trust change and the revocation happened today. No key material
     * appears at any point — none is carried by this model.
     */
    private val trustSequence = listOf(
        entry(
            id = "EVT-4300", type = AuditEventType.IdentityCreated, target = displayIdentity,
            outcome = AuditOutcome.Informational, ageMinutes = 1622, sequence = 799,
            source = AuditSource.TrustService, correlationId = "TID-9E12"
        ),
        entry(
            id = "EVT-4301", type = AuditEventType.VerificationCompleted, target = displayIdentity,
            outcome = AuditOutcome.Succeeded, ageMinutes = 1620, sequence = 800,
            source = AuditSource.TrustService, correlationId = "TID-9E12",
            resultingState = "TRUSTED"
        ),
        entry(
            id = "EVT-4302", type = AuditEventType.ReverificationRequested, target = displayIdentity,
            outcome = AuditOutcome.Pending, ageMinutes = 55, sequence = 989,
            source = AuditSource.TrustService, correlationId = "TID-9E12",
            actionCode = REVERIFY
        ),
        entry(
            id = "EVT-4303", type = AuditEventType.TrustChanged, target = displayIdentity,
            outcome = AuditOutcome.Informational, ageMinutes = 54, sequence = 990,
            source = AuditSource.TrustService, correlationId = "TID-9E12",
            previousState = "TRUSTED", resultingState = "UNVERIFIED"
        ),
        entry(
            id = "EVT-4304", type = AuditEventType.CredentialSuperseded, target = displayIdentity,
            outcome = AuditOutcome.Informational, ageMinutes = 50, sequence = 992,
            source = AuditSource.TrustService, correlationId = "TID-9E12",
            note = "Previous credential is no longer accepted."
        ),
        entry(
            id = "EVT-4305", type = AuditEventType.IdentityRevoked, target = displayIdentity,
            outcome = AuditOutcome.Informational, ageMinutes = 18, sequence = 1038,
            source = AuditSource.TrustService, correlationId = "TID-9E12",
            previousState = "UNVERIFIED", resultingState = "REVOKED"
        )
    )

    /**
     * An alert whose notification never got through.
     *
     * The alert stays raised. Delivery failing is a fact about the
     * notification, and produces no alert-lifecycle record at all.
     */
    private val notificationFailureSequence = listOf(
        entry(
            id = "EVT-4490", type = AuditEventType.AlertRaised, target = tablet,
            outcome = AuditOutcome.Informational, ageMinutes = 36, sequence = 1009,
            source = AuditSource.SecurityEvent, alertId = "ALRT-1091",
            correlationId = "ALRT-1091"
        ),
        entry(
            id = "EVT-4491", type = AuditEventType.NotificationSent, target = tablet,
            outcome = AuditOutcome.Pending, ageMinutes = 35, sequence = 1010,
            source = AuditSource.NotificationService, alertId = "ALRT-1091",
            correlationId = "ALRT-1091"
        ),
        entry(
            id = "EVT-4492", type = AuditEventType.NotificationRetrying, target = tablet,
            outcome = AuditOutcome.Pending, ageMinutes = 34, sequence = 1011,
            source = AuditSource.NotificationService, alertId = "ALRT-1091",
            correlationId = "ALRT-1091", note = "Attempt 2 of 3"
        ),
        entry(
            id = "EVT-4493", type = AuditEventType.NotificationFailed, target = tablet,
            outcome = AuditOutcome.Failed, ageMinutes = 33, sequence = 1012,
            source = AuditSource.NotificationService, alertId = "ALRT-1091",
            correlationId = "ALRT-1091", note = "No further attempts will be made."
        )
    )

    /** A live release, end to end. */
    private val liveReleaseSequence = listOf(
        entry(
            id = "EVT-4500", type = AuditEventType.ActionRequested, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 7, sequence = 1055,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9130", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4501", type = AuditEventType.ActionAuthorized, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 6, sequence = 1056,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9130", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4502", type = AuditEventType.ActionExecuting, target = unknownDevice,
            outcome = AuditOutcome.Pending, ageMinutes = 6, sequence = 1057,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9130", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4503", type = AuditEventType.EnforcementBindingRemoved, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 5, sequence = 1058,
            source = AuditSource.EnforcementSubsystem, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9130", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4504", type = AuditEventType.ActionReconciled, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 5, sequence = 1059,
            source = AuditSource.EnforcementSubsystem, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9130", actionCode = RELEASE
        ),
        entry(
            id = "EVT-4505", type = AuditEventType.ActionSucceeded, target = unknownDevice,
            outcome = AuditOutcome.Succeeded, ageMinutes = 4, sequence = 1060,
            source = AuditSource.ActionPipeline, executionMode = ExecutionMode.Enforce,
            correlationId = "ACT-9130", actionCode = RELEASE,
            previousState = "QUARANTINED", resultingState = "NORMAL"
        )
    )

    /** Observation and subsystem records that stand on their own. */
    private val standaloneEvents = listOf(
        entry(
            id = "EVT-4510", type = AuditEventType.CircuitBreakerOpened, target = breaker,
            outcome = AuditOutcome.Informational, ageMinutes = 70, sequence = 980,
            source = AuditSource.EnforcementSubsystem,
            resultingState = "OPEN", note = "Consecutive execution failures exceeded the threshold."
        ),
        entry(
            id = "EVT-4511", type = AuditEventType.CircuitBreakerClosed, target = breaker,
            outcome = AuditOutcome.Informational, ageMinutes = 60, sequence = 985,
            source = AuditSource.EnforcementSubsystem,
            previousState = "OPEN", resultingState = "CLOSED"
        ),
        entry(
            id = "EVT-4512", type = AuditEventType.DeviceAddressChanged, target = buildServer,
            outcome = AuditOutcome.Informational, ageMinutes = 45, sequence = 997,
            source = AuditSource.Observation,
            previousState = "10.20.7.9", resultingState = "10.20.7.12"
        ),
        entry(
            id = "EVT-4513", type = AuditEventType.CrashReconciliationCompleted, target = breaker,
            outcome = AuditOutcome.Succeeded, ageMinutes = 44, sequence = 998,
            source = AuditSource.EnforcementSubsystem, executionMode = ExecutionMode.Enforce,
            note = "3 recorded bindings checked against system state."
        ),
        entry(
            id = "EVT-4514", type = AuditEventType.AlertIgnored, target = tablet,
            outcome = AuditOutcome.Informational, ageMinutes = 20, sequence = 1035,
            source = AuditSource.Alert, alertId = "ALRT-1078", correlationId = "ALRT-1078",
            previousState = "NEW", resultingState = "IGNORED"
        ),
        entry(
            id = "EVT-4515", type = AuditEventType.DeviceObserved, target = labController,
            outcome = AuditOutcome.Informational, ageMinutes = 9, sequence = 1050,
            source = AuditSource.Observation
        )
    )

    // ============================================================
    // ASSEMBLED HISTORY
    // ============================================================

    val entries: List<AuditEntry> = (
        liveQuarantineSequence +
            simulatedReleaseSequence +
            simulatedFailureSequence +
            rollbackSequence +
            rollbackFailureSequence +
            unknownOutcomeSequence +
            deniedSequence +
            trustSequence +
            notificationFailureSequence +
            liveReleaseSequence +
            standaloneEvents
        ).applySort(AuditSort.Newest)

    /** The scopes present in the loaded history, for the filter sheet. */
    val scopes: List<String> = entries.mapNotNull { it.target.scopeOrNull }.distinct().sorted()

    // ============================================================
    // SCENARIOS
    // ============================================================

    val scenario: AuditUiState = auditContent(entries)

    /** History is genuinely empty — and says nothing about system posture. */
    val empty: AuditUiState = auditContent(emptyList())

    /** History could not be retrieved. Not "nothing happened". */
    val unavailable: AuditUiState = AuditUiState.Unavailable

    val offline: AuditUiState = AuditUiState.Offline

    /** Real history, but old enough that it should not be read as current. */
    val stale: AuditUiState = auditContent(
        all = entries,
        freshness = DataFreshness.Stale("Last confirmed 46 minutes ago")
    )

    /** Only part of the record is available, and the screen says so. */
    val degraded: AuditUiState = auditContent(
        all = entries.filter { it.ageMinutes < 60 },
        freshness = DataFreshness.Stale("Last confirmed 8 minutes ago"),
        coverage = AuditCoverage.Partial(
            "The event store returned a partial range. Records older than one hour are missing from this feed."
        )
    )

    // ============================================================
    // DETAIL
    // ============================================================

    fun detailFor(eventId: String): AuditDetailUiState {
        val entry = entries.firstOrNull { it.id == eventId }
            ?: return AuditDetailUiState.Unavailable

        // Sibling records of the same action or alert, in the order they
        // actually happened — the sequence is the point of opening one.
        val related = entry.correlationId?.let { correlation ->
            entries.filter { it.correlationId == correlation && it.id != entry.id }
                .applySort(AuditSort.Oldest)
        }.orEmpty()

        return AuditDetailUiState.Content(
            AuditDetailData(
                entry = entry,
                fields = auditDetailFields(entry),
                related = related,
                links = auditLinks(entry),
                freshness = DataFreshness.Live
            )
        )
    }

    // ============================================================
    // TIME
    //
    // Formatted from a fixed preview clock rather than the device's, so the
    // timeline is deterministic and no record's time depends on when the
    // screen happened to be rendered.
    // ============================================================

    private const val PREVIEW_DAY = 31
    private const val PREVIEW_MONTH = "Aug"
    private const val PREVIEW_YEAR = 2026
    private const val PREVIEW_ZONE = "UTC+02:00"
    private const val NOW_MINUTE_OF_DAY = 16 * 60 + 40
    private const val MINUTES_PER_DAY = 24 * 60

    private fun dayOffset(ageMinutes: Int): Int {
        val minutes = NOW_MINUTE_OF_DAY - ageMinutes
        return if (minutes >= 0) 0 else (-minutes + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY
    }

    private fun timestampLabel(ageMinutes: Int): String {
        val minutes = NOW_MINUTE_OF_DAY - ageMinutes
        val minuteOfDay = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val day = PREVIEW_DAY - dayOffset(ageMinutes)
        return String.format(
            "%04d-%s-%02d %02d:%02d %s",
            PREVIEW_YEAR, PREVIEW_MONTH, day, minuteOfDay / 60, minuteOfDay % 60, PREVIEW_ZONE
        )
    }

    private fun dayLabel(ageMinutes: Int): String {
        val offset = dayOffset(ageMinutes)
        val day = PREVIEW_DAY - offset
        val date = "$day $PREVIEW_MONTH $PREVIEW_YEAR"
        return when (offset) {
            0 -> "Today · $date"
            1 -> "Yesterday · $date"
            else -> date
        }
    }

    private fun relativeLabel(ageMinutes: Int): String = when {
        ageMinutes < 60 -> "${ageMinutes}m ago"
        ageMinutes < MINUTES_PER_DAY -> "${ageMinutes / 60}h ${ageMinutes % 60}m ago"
        else -> "${ageMinutes / MINUTES_PER_DAY}d ${(ageMinutes % MINUTES_PER_DAY) / 60}h ago"
    }

    private fun entry(
        id: String,
        type: AuditEventType,
        target: AuditTarget,
        outcome: AuditOutcome,
        ageMinutes: Int,
        sequence: Long,
        source: AuditSource,
        executionMode: ExecutionMode? = null,
        correlationId: String? = null,
        actionCode: String? = null,
        previousState: String? = null,
        resultingState: String? = null,
        alertId: String? = null,
        note: String? = null
    ): AuditEntry = AuditEntry(
        id = id,
        type = type,
        target = target,
        outcome = outcome,
        occurredAtLabel = timestampLabel(ageMinutes),
        dayLabel = dayLabel(ageMinutes),
        relativeLabel = relativeLabel(ageMinutes),
        ageMinutes = ageMinutes,
        sequence = sequence,
        source = source,
        executionMode = executionMode,
        correlationId = correlationId,
        actionCode = actionCode,
        previousState = previousState,
        resultingState = resultingState,
        alertId = alertId,
        note = note
    )
}
