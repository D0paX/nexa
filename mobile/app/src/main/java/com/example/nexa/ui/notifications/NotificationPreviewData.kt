package com.example.nexa.ui.notifications

import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.DeliveryAttempt
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.enforcement.ExecutionState

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE
 *
 * Every delivery record below is fabricated for interface development. No
 * notification was sent, no channel was contacted, and nothing here reflects
 * the state of any real alert, action or identity.
 *
 * The source snapshots are deliberately varied against the delivery states so
 * the pairings this checkpoint exists to keep apart all appear at once: a
 * critical alert whose notification failed, a resolved alert whose
 * notification was exhausted, a delivered notification about an action that
 * is still reconciling, and a delivered notification about an identity whose
 * verification is still pending.
 *
 * Attempt lists are ordered newest first and carry their own attempt numbers,
 * so a reader counting rows cannot end up reading the sequence backwards.
 */
object NotificationPreview {

    private const val PUSH = "Push (FCM)"

    // ============================================================
    // TARGETS — the same identifiers the other preview sources use
    // ============================================================

    private val corpLaptop = NotificationTarget.Device(
        deviceId = "DEV-1001",
        label = "Corp Laptop - Engineering",
        mac = "00:1A:2B:3C:4D:5E",
        ip = "192.168.1.105",
        scope = "VLAN_SECURE",
        observationFreshness = DataFreshness.Live
    )

    private val unknownDevice = NotificationTarget.Device(
        deviceId = "DEV-1002",
        label = "Unknown Device",
        mac = "00:5E:4D:3C:2B:1A",
        ip = "10.20.9.55",
        scope = "VLAN_GUEST",
        observationFreshness = DataFreshness.Live
    )

    private val buildServer = NotificationTarget.Device(
        deviceId = "DEV-1003",
        label = "Build Server",
        mac = "3C:22:FB:19:04:A1",
        ip = "10.20.4.11",
        scope = "VLAN_BUILD",
        observationFreshness = DataFreshness.Live
    )

    private val receptionTablet = NotificationTarget.Device(
        deviceId = "DEV-1004",
        label = "Reception Tablet",
        mac = "AA:BB:CC:DD:EE:FF",
        ip = "10.20.9.31",
        scope = "VLAN_GUEST",
        observationFreshness = DataFreshness.Stale("Last observed 3h ago")
    )

    private val labController = NotificationTarget.Device(
        deviceId = "DEV-1005",
        label = "Lab Controller",
        mac = "9C:2F:1D:44:0B:77",
        ip = "10.20.5.90",
        scope = "VLAN_SECURE",
        observationFreshness = DataFreshness.Live
    )

    private val displayIdentity = NotificationTarget.Identity(
        identityId = "TID-9E12",
        label = "Conference Display",
        scope = "VLAN_GUEST"
    )

    private val laptopIdentity = NotificationTarget.Identity(
        identityId = "TID-88F1",
        label = "Corp Laptop - Engineering",
        scope = "VLAN_SECURE"
    )

    private val tabletIdentity = NotificationTarget.Identity(
        identityId = "TID-51AA",
        label = "Reception Tablet",
        scope = "VLAN_GUEST"
    )

    // ============================================================
    // RECORDS
    // ============================================================

    private val primary: List<NotificationRecord> = listOf(

        // --- Critical alert, delivery failed. The incident is unaffected. ---
        record(
            id = "NTF-7002",
            subject = "Repeated Authentication Failure on Build Server",
            state = DeliveryState.Failed,
            ageMinutes = 18,
            attemptCount = 3,
            maxAttempts = 3,
            createdLabel = "21m ago",
            lastAttemptLabel = "17m ago",
            failureReason = "Device token rejected by the push service.",
            attempts = listOf(
                DeliveryAttempt(3, PUSH, DeliveryState.Failed, "17m ago", "Device token rejected."),
                DeliveryAttempt(2, PUSH, DeliveryState.Failed, "19m ago", "Device token rejected."),
                DeliveryAttempt(1, PUSH, DeliveryState.Sent, "21m ago")
            ),
            source = NotificationSource.Alert(
                alertId = "ALRT-1089",
                title = "Repeated Authentication Failure",
                severity = AlertSeverity.Critical,
                lifecycle = AlertLifecycle.Acknowledged
            ),
            target = buildServer
        ),

        // --- Resolved alert, delivery exhausted. The incident is still closed. ---
        record(
            id = "NTF-7001",
            subject = "Quarantine released for Corp Laptop - Engineering",
            state = DeliveryState.Exhausted,
            ageMinutes = 300,
            attemptCount = 4,
            maxAttempts = 4,
            createdLabel = "6h ago",
            lastAttemptLabel = "5h ago",
            failureReason = "No route to the registered push destination.",
            attempts = listOf(
                DeliveryAttempt(4, PUSH, DeliveryState.Exhausted, "5h ago", "No further attempts will be made."),
                DeliveryAttempt(3, PUSH, DeliveryState.Failed, "5h ago", "No route to destination."),
                DeliveryAttempt(2, PUSH, DeliveryState.Failed, "6h ago", "No route to destination."),
                DeliveryAttempt(1, PUSH, DeliveryState.Sent, "6h ago")
            ),
            source = NotificationSource.Alert(
                alertId = "ALRT-1078",
                title = "Quarantine released",
                severity = AlertSeverity.Information,
                lifecycle = AlertLifecycle.Resolved
            ),
            target = corpLaptop
        ),

        // --- Retrying, with a schedule the backend actually supplied. ---
        record(
            id = "NTF-7003",
            subject = "Untrusted MAC in Trusted VLAN",
            state = DeliveryState.Retrying,
            ageMinutes = 6,
            attemptCount = 2,
            maxAttempts = 3,
            createdLabel = "14m ago",
            lastAttemptLabel = "6m ago",
            nextRetryLabel = "in 4m",
            attempts = listOf(
                DeliveryAttempt(2, PUSH, DeliveryState.Retrying, "6m ago", "Retry scheduled."),
                DeliveryAttempt(1, PUSH, DeliveryState.Failed, "12m ago", "Transport timeout.")
            ),
            source = NotificationSource.Alert(
                alertId = "ALRT-1091",
                title = "Untrusted MAC in Trusted VLAN",
                severity = AlertSeverity.Warning,
                lifecycle = AlertLifecycle.New
            ),
            target = unknownDevice
        ),

        // --- Delivered, about an alert that is still new and still critical. ---
        record(
            id = "NTF-7004",
            subject = "Suspicious Port Scan",
            state = DeliveryState.Delivered,
            ageMinutes = 2,
            attemptCount = 1,
            maxAttempts = 3,
            createdLabel = "2m ago",
            lastAttemptLabel = "2m ago",
            attempts = listOf(DeliveryAttempt(1, PUSH, DeliveryState.Delivered, "2m ago")),
            source = NotificationSource.Alert(
                alertId = "ALRT-1092",
                title = "Suspicious Port Scan",
                severity = AlertSeverity.Critical,
                lifecycle = AlertLifecycle.New
            ),
            target = corpLaptop
        ),

        // --- Queued, not yet sent. ---
        record(
            id = "NTF-7005",
            subject = "Enforcement action did not complete",
            state = DeliveryState.Pending,
            ageMinutes = 8,
            attemptCount = 0,
            maxAttempts = 3,
            createdLabel = "8m ago",
            lastAttemptLabel = "No attempt yet",
            source = NotificationSource.Alert(
                alertId = "ALRT-1090",
                title = "Enforcement action did not complete",
                severity = AlertSeverity.Danger,
                lifecycle = AlertLifecycle.New
            ),
            target = buildServer
        ),

        // --- Sent, delivery not confirmed. ---
        record(
            id = "NTF-7006",
            subject = "Revoked identity observed on network",
            state = DeliveryState.Sent,
            ageMinutes = 180,
            attemptCount = 1,
            maxAttempts = 3,
            createdLabel = "3h ago",
            lastAttemptLabel = "3h ago",
            attempts = listOf(DeliveryAttempt(1, PUSH, DeliveryState.Sent, "3h ago")),
            source = NotificationSource.Alert(
                alertId = "ALRT-1087",
                title = "Revoked identity observed on network",
                severity = AlertSeverity.Warning,
                lifecycle = AlertLifecycle.New
            ),
            target = receptionTablet
        ),

        // --- The delivery record itself cannot be read. Not a failure. ---
        record(
            id = "NTF-7007",
            subject = "Device Offline",
            state = DeliveryState.Unavailable,
            ageMinutes = 240,
            attemptCount = 0,
            maxAttempts = null,
            createdLabel = "4h ago",
            lastAttemptLabel = "Unknown",
            source = NotificationSource.Alert(
                alertId = "ALRT-1080",
                title = "Device Offline",
                severity = AlertSeverity.Information,
                lifecycle = AlertLifecycle.Resolved
            ),
            target = receptionTablet
        ),

        // --- Delivered notification about an action that is still reconciling. ---
        record(
            id = "NTF-7008",
            subject = "Quarantine requested for Lab Controller",
            state = DeliveryState.Delivered,
            ageMinutes = 12,
            attemptCount = 1,
            maxAttempts = 3,
            createdLabel = "12m ago",
            lastAttemptLabel = "12m ago",
            attempts = listOf(DeliveryAttempt(1, PUSH, DeliveryState.Delivered, "12m ago")),
            source = NotificationSource.Action(
                actionId = "ACT-9127",
                actionCode = "QUARANTINE_DEVICE",
                executionState = ExecutionState.Reconciling,
                executionMode = ExecutionMode.Enforce
            ),
            target = labController
        ),

        // --- Failed notification about an action whose rollback also failed. ---
        record(
            id = "NTF-7009",
            subject = "Quarantine rollback failed on Build Server",
            state = DeliveryState.Failed,
            ageMinutes = 22,
            attemptCount = 2,
            maxAttempts = 3,
            createdLabel = "23m ago",
            lastAttemptLabel = "22m ago",
            failureReason = "Push service returned a temporary error.",
            attempts = listOf(
                DeliveryAttempt(2, PUSH, DeliveryState.Failed, "22m ago", "Temporary error."),
                DeliveryAttempt(1, PUSH, DeliveryState.Failed, "23m ago", "Temporary error.")
            ),
            source = NotificationSource.Action(
                actionId = "ACT-9110",
                actionCode = "QUARANTINE_DEVICE",
                executionState = ExecutionState.RollbackFailed,
                executionMode = ExecutionMode.Enforce
            ),
            target = buildServer
        ),

        // --- Delivered notification about a simulated action. ---
        record(
            id = "NTF-7010",
            subject = "Release simulation completed for Unknown Device",
            state = DeliveryState.Delivered,
            ageMinutes = 38,
            attemptCount = 1,
            maxAttempts = 3,
            createdLabel = "38m ago",
            lastAttemptLabel = "38m ago",
            attempts = listOf(DeliveryAttempt(1, PUSH, DeliveryState.Delivered, "38m ago")),
            source = NotificationSource.Action(
                actionId = "ACT-9004",
                actionCode = "RELEASE_QUARANTINE",
                executionState = ExecutionState.Succeeded,
                executionMode = ExecutionMode.AuditOnly
            ),
            target = unknownDevice
        ),

        // --- Retrying notification about an identity whose verification is pending. ---
        record(
            id = "NTF-7011",
            subject = "Reverification required for Conference Display",
            state = DeliveryState.Retrying,
            ageMinutes = 55,
            attemptCount = 2,
            maxAttempts = 4,
            createdLabel = "1h 2m ago",
            lastAttemptLabel = "55m ago",
            nextRetryLabel = "in 12m",
            attempts = listOf(
                DeliveryAttempt(2, PUSH, DeliveryState.Retrying, "55m ago", "Retry scheduled."),
                DeliveryAttempt(1, PUSH, DeliveryState.Failed, "1h ago", "Transport timeout.")
            ),
            source = NotificationSource.Trust(
                identityId = "TID-9E12",
                label = "Conference Display",
                trust = TrustState.Pending
            ),
            target = displayIdentity
        ),

        // --- Delivered notification about a trusted identity. ---
        record(
            id = "NTF-7012",
            subject = "Identity verification completed",
            state = DeliveryState.Delivered,
            ageMinutes = 41,
            attemptCount = 1,
            maxAttempts = 3,
            createdLabel = "41m ago",
            lastAttemptLabel = "41m ago",
            attempts = listOf(DeliveryAttempt(1, PUSH, DeliveryState.Delivered, "41m ago")),
            source = NotificationSource.Trust(
                identityId = "TID-88F1",
                label = "Corp Laptop - Engineering",
                trust = TrustState.Trusted
            ),
            target = laptopIdentity
        ),

        // --- Exhausted notification about a revoked identity. ---
        record(
            id = "NTF-7015",
            subject = "Identity trust revoked",
            state = DeliveryState.Exhausted,
            ageMinutes = 600,
            attemptCount = 3,
            maxAttempts = 3,
            createdLabel = "10h ago",
            lastAttemptLabel = "10h ago",
            failureReason = "Registered destination is no longer valid.",
            attempts = listOf(
                DeliveryAttempt(3, PUSH, DeliveryState.Exhausted, "10h ago", "No further attempts will be made."),
                DeliveryAttempt(2, PUSH, DeliveryState.Failed, "10h ago", "Destination invalid."),
                DeliveryAttempt(1, PUSH, DeliveryState.Failed, "10h ago", "Destination invalid.")
            ),
            source = NotificationSource.Trust(
                identityId = "TID-51AA",
                label = "Reception Tablet",
                trust = TrustState.Revoked
            ),
            target = tabletIdentity
        ),

        // --- A security event notification that failed. ---
        record(
            id = "NTF-7013",
            subject = "Observed address changed for Build Server",
            state = DeliveryState.Failed,
            ageMinutes = 45,
            attemptCount = 1,
            maxAttempts = 3,
            createdLabel = "45m ago",
            lastAttemptLabel = "45m ago",
            failureReason = "Push service unreachable.",
            attempts = listOf(
                DeliveryAttempt(1, PUSH, DeliveryState.Failed, "45m ago", "Push service unreachable.")
            ),
            source = NotificationSource.SecurityEvent(
                eventId = "EVT-4512",
                summary = "Observed address changed"
            ),
            target = buildServer
        ),

        // --- An old failure. Ranked below current problems, not above them. ---
        record(
            id = "NTF-7014",
            subject = "Device Offline",
            state = DeliveryState.Failed,
            ageMinutes = 12 * 24 * 60,
            attemptCount = 3,
            maxAttempts = 3,
            createdLabel = "12d ago",
            lastAttemptLabel = "12d ago",
            failureReason = "Device token rejected by the push service.",
            attempts = listOf(
                DeliveryAttempt(3, PUSH, DeliveryState.Failed, "12d ago", "Device token rejected."),
                DeliveryAttempt(2, PUSH, DeliveryState.Failed, "12d ago", "Device token rejected."),
                DeliveryAttempt(1, PUSH, DeliveryState.Sent, "12d ago")
            ),
            source = NotificationSource.Alert(
                alertId = "ALRT-0900",
                title = "Device Offline",
                severity = AlertSeverity.Information,
                lifecycle = AlertLifecycle.Resolved
            ),
            target = receptionTablet
        )
    )

    /**
     * Delivered history.
     *
     * A working notification channel produces far more successful deliveries
     * than failures, and the list has to look like that or the screen gives a
     * false impression of how the channel is doing.
     */
    private val deliveredHistory: List<NotificationRecord> = listOf(
        Triple("NTF-6990", "Suspicious Port Scan", 62),
        Triple("NTF-6989", "Device Offline", 74),
        Triple("NTF-6988", "Quarantine requested for Reception Tablet", 88),
        Triple("NTF-6987", "Untrusted MAC in Trusted VLAN", 96),
        Triple("NTF-6986", "Identity verification completed", 120),
        Triple("NTF-6985", "Enforcement binding created", 141),
        Triple("NTF-6984", "Repeated benign scan from monitoring host", 165),
        Triple("NTF-6983", "Device Offline", 190),
        Triple("NTF-6982", "Quarantine released", 214),
        Triple("NTF-6981", "Reverification required", 250),
        Triple("NTF-6980", "Suspicious Port Scan", 288),
        Triple("NTF-6979", "Enforcement binding removed", 330)
    ).mapIndexed { index, (id, subject, age) ->
        record(
            id = id,
            subject = subject,
            state = DeliveryState.Delivered,
            ageMinutes = age,
            attemptCount = 1,
            maxAttempts = 3,
            createdLabel = "${age / 60}h ${age % 60}m ago",
            lastAttemptLabel = "${age / 60}h ${age % 60}m ago",
            attempts = listOf(
                DeliveryAttempt(1, PUSH, DeliveryState.Delivered, "${age / 60}h ${age % 60}m ago")
            ),
            source = NotificationSource.Alert(
                alertId = "ALRT-09${70 - index}",
                title = subject,
                severity = AlertSeverity.Information,
                lifecycle = AlertLifecycle.Resolved
            ),
            target = if (index % 2 == 0) corpLaptop else receptionTablet
        )
    }

    val records: List<NotificationRecord> = primary + deliveredHistory

    val scopes: List<String> = records.mapNotNull { it.target.scopeOrNull }.distinct().sorted()

    // ============================================================
    // SCENARIOS
    // ============================================================

    val scenario: NotificationCenterUiState = notificationContent(records)

    /** No delivery records — which says nothing about the event store. */
    val empty: NotificationCenterUiState = notificationContent(emptyList())

    /** Delivery visibility could not be retrieved. Not "nothing was sent". */
    val unavailable: NotificationCenterUiState = NotificationCenterUiState.Unavailable

    val offline: NotificationCenterUiState = NotificationCenterUiState.Offline

    val stale: NotificationCenterUiState = notificationContent(
        all = records,
        freshness = DataFreshness.Stale("Delivery visibility last confirmed 22 minutes ago")
    )

    val degraded: NotificationCenterUiState = notificationContent(
        all = records.filter { it.delivery.ageMinutes < 120 },
        freshness = DataFreshness.Stale("Delivery visibility last confirmed 4 minutes ago"),
        coverage = NotificationCoverage.Partial(
            "The delivery service returned a partial range. Records older than two hours are missing, so failures in that window would not appear here."
        )
    )

    // ============================================================
    // DETAIL
    // ============================================================

    fun detailFor(deliveryId: String): NotificationDetailUiState {
        val record = records.firstOrNull { it.id.equals(deliveryId, ignoreCase = true) }
            ?: return NotificationDetailUiState.Unavailable

        return NotificationDetailUiState.Content(
            NotificationDetailData(
                record = record,
                deliveryFields = notificationDeliveryFields(record),
                sourceFields = notificationSourceFields(record),
                attempts = record.delivery.attempts,
                links = notificationLinks(record),
                freshness = DataFreshness.Live
            )
        )
    }

    // ============================================================

    private fun record(
        id: String,
        subject: String,
        state: DeliveryState,
        ageMinutes: Int,
        attemptCount: Int,
        maxAttempts: Int?,
        createdLabel: String,
        lastAttemptLabel: String,
        source: NotificationSource,
        target: NotificationTarget = NotificationTarget.None,
        nextRetryLabel: String? = null,
        failureReason: String? = null,
        attempts: List<DeliveryAttempt> = emptyList()
    ): NotificationRecord = NotificationRecord(
        id = id,
        subject = subject,
        delivery = NotificationDeliverySummary(
            deliveryId = id,
            state = state,
            channel = NotificationChannel.Push,
            attemptCount = attemptCount,
            maxAttempts = maxAttempts,
            createdLabel = createdLabel,
            lastAttemptLabel = lastAttemptLabel,
            nextRetryLabel = nextRetryLabel,
            failureReason = failureReason,
            attempts = attempts,
            ageMinutes = ageMinutes
        ),
        source = source,
        target = target
    )
}
