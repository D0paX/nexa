package com.example.nexa.ui.alerts

import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.ActivityKind
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.Presence

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE.
 *
 * Fabricated incident load for development. No Phase 3 API is implied and
 * nothing here reflects real event aggregation or real notification
 * delivery.
 *
 * The combinations are deliberately awkward rather than tidy, because the
 * awkward ones are where a careless interface breaks: a critical alert whose
 * notification failed, a resolved alert whose notification also failed, an
 * acknowledged critical that is still open, an alert whose target
 * observation is stale, and an alert whose delivery state cannot be read at
 * all.
 */
object AlertsPreview {

    val scenario: AlertsUiState get() = content()

    fun content(): AlertsUiState = contentFrom(alerts)

    fun empty(): AlertsUiState = contentFrom(emptyList())

    fun historyOnly(): AlertsUiState = contentFrom(alerts.filter { !it.lifecycle.isOpen })

    fun degraded(): AlertsUiState = (content() as AlertsUiState.Content).copy(degraded = true)

    fun stale(): AlertsUiState = (content() as AlertsUiState.Content)
        .copy(freshness = DataFreshness.Stale("Last confirmed 9 min ago"))

    fun offline(): AlertsUiState = AlertsUiState.Offline

    fun unavailable(): AlertsUiState = AlertsUiState.Unavailable

    private fun contentFrom(source: List<AlertListItem>): AlertsUiState = AlertsUiState.Content(
        all = source,
        visible = source.resolve("", AlertFilters(), AlertSort.Attention, AlertScopeView.Open),
        summary = summarize(source),
        query = "",
        filters = AlertFilters(),
        sort = AlertSort.Attention,
        view = AlertScopeView.Open,
        freshness = DataFreshness.Live,
        degraded = false
    )

    // --- Targets ---

    private val corpLaptop = AlertTarget.DeviceTarget(
        device = AlertDeviceRef(
            deviceId = "DEV-1001",
            label = "Corp Laptop - Engineering",
            mac = "00:1A:2B:3C:4D:5E",
            ip = "192.168.1.105",
            scope = "VLAN_SECURE",
            presence = Presence.Present,
            recordFreshness = DataFreshness.Live,
            lastObservedLabel = "2m ago"
        ),
        identity = AlertIdentityRef("TID-88F1", TrustState.Trusted)
    )

    /** Observed, but with no cryptographic identity at all. */
    private val unknownDevice = AlertTarget.DeviceTarget(
        device = AlertDeviceRef(
            deviceId = "DEV-1002",
            label = "Unknown Device",
            mac = "00:5E:4D:3C:2B:1A",
            ip = "192.168.1.140",
            scope = "VLAN_SECURE",
            presence = Presence.Present,
            recordFreshness = DataFreshness.Live,
            lastObservedLabel = "14m ago"
        ),
        identity = null
    )

    /** The observation behind this target is no longer current. */
    private val staleTablet = AlertTarget.DeviceTarget(
        device = AlertDeviceRef(
            deviceId = "DEV-1004",
            label = "Reception Tablet",
            mac = "AA:BB:CC:DD:EE:FF",
            ip = "192.168.9.30",
            scope = "VLAN_GUEST",
            presence = Presence.Absent,
            recordFreshness = DataFreshness.Stale("Last seen 3h ago"),
            lastObservedLabel = "3h ago"
        ),
        identity = AlertIdentityRef("TID-51AA", TrustState.Revoked)
    )

    private val buildServer = AlertTarget.DeviceTarget(
        device = AlertDeviceRef(
            deviceId = "DEV-1003",
            label = "Build Server",
            mac = "3C:22:FB:19:04:A1",
            ip = "10.20.4.11",
            scope = "VLAN_BUILD",
            presence = Presence.Present,
            recordFreshness = DataFreshness.Live,
            lastObservedLabel = "1m ago"
        ),
        identity = AlertIdentityRef("TID-2B0C", TrustState.Trusted)
    )

    // --- Alerts ---

    val alerts: List<AlertListItem> = listOf(
        // Critical, new, notification delivered.
        AlertListItem(
            id = "ALRT-1092",
            title = "Suspicious Port Scan",
            severity = AlertSeverity.Critical,
            lifecycle = AlertLifecycle.New,
            delivery = DeliveryState.Delivered,
            target = corpLaptop,
            createdLabel = "2m ago",
            updatedLabel = "2m ago",
            ageMinutes = 2
        ),
        // Critical, acknowledged, notification FAILED. Still open, still critical.
        AlertListItem(
            id = "ALRT-1089",
            title = "Repeated Authentication Failure",
            severity = AlertSeverity.Critical,
            lifecycle = AlertLifecycle.Acknowledged,
            delivery = DeliveryState.Failed,
            target = buildServer,
            createdLabel = "21m ago",
            updatedLabel = "18m ago",
            ageMinutes = 21
        ),
        // Warning, new, notification retrying.
        AlertListItem(
            id = "ALRT-1091",
            title = "Untrusted MAC in Trusted VLAN",
            severity = AlertSeverity.Warning,
            lifecycle = AlertLifecycle.New,
            delivery = DeliveryState.Retrying,
            target = unknownDevice,
            createdLabel = "14m ago",
            updatedLabel = "6m ago",
            ageMinutes = 14
        ),
        // Danger, new, delivery state unreadable.
        AlertListItem(
            id = "ALRT-1090",
            title = "Enforcement action did not complete",
            severity = AlertSeverity.Danger,
            lifecycle = AlertLifecycle.New,
            delivery = DeliveryState.Unavailable,
            target = buildServer,
            createdLabel = "8m ago",
            updatedLabel = "8m ago",
            ageMinutes = 8
        ),
        // Warning, new, target observation stale.
        AlertListItem(
            id = "ALRT-1087",
            title = "Revoked identity observed on network",
            severity = AlertSeverity.Warning,
            lifecycle = AlertLifecycle.New,
            delivery = DeliveryState.Sent,
            target = staleTablet,
            createdLabel = "3h ago",
            updatedLabel = "3h ago",
            ageMinutes = 180
        ),
        // Information, resolved, delivered — history.
        AlertListItem(
            id = "ALRT-1080",
            title = "Device Offline",
            severity = AlertSeverity.Information,
            lifecycle = AlertLifecycle.Resolved,
            delivery = DeliveryState.Delivered,
            target = staleTablet,
            createdLabel = "5h ago",
            updatedLabel = "4h ago",
            ageMinutes = 300
        ),
        // Resolved incident whose notification failed. Still resolved.
        AlertListItem(
            id = "ALRT-1078",
            title = "Quarantine released",
            severity = AlertSeverity.Information,
            lifecycle = AlertLifecycle.Resolved,
            delivery = DeliveryState.Exhausted,
            target = corpLaptop,
            createdLabel = "6h ago",
            updatedLabel = "5h ago",
            ageMinutes = 360
        ),
        // Ignored, not resolved.
        AlertListItem(
            id = "ALRT-1075",
            title = "Repeated benign scan from monitoring host",
            severity = AlertSeverity.Information,
            lifecycle = AlertLifecycle.Ignored,
            delivery = DeliveryState.Delivered,
            target = unknownDevice,
            createdLabel = "9h ago",
            updatedLabel = "8h ago",
            ageMinutes = 540
        )
    )

    val scopes: List<String> = alerts.mapNotNull { it.target.deviceRef?.scope }.distinct().sorted()

    fun detailFor(alertId: String): AlertDetailUiState {
        val alert = alerts.firstOrNull { it.id.equals(alertId, ignoreCase = true) }
            ?: return AlertDetailUiState.Unavailable

        val description = when (alert.id) {
            "ALRT-1092" -> "A sustained port scan was observed from this device against hosts in VLAN_SECURE. The pattern matched the aggregation rule for reconnaissance activity."
            "ALRT-1089" -> "Repeated authentication failures were recorded for this device's identity within the aggregation window."
            "ALRT-1091" -> "A MAC address with no cryptographic identity was observed inside a trusted VLAN. Observation alone does not establish what this device is."
            "ALRT-1090" -> "An enforcement action for this target did not reach a confirmed state. The resulting firewall state is not confirmed."
            "ALRT-1087" -> "Network traffic was attributed to an identity whose trust has been revoked. The observation behind this attribution is no longer current."
            "ALRT-1080" -> "The device stopped responding to observation and was reported offline."
            "ALRT-1078" -> "An enforcement binding for this target was released and normal access was restored."
            else -> "Repeated low-severity activity from a known monitoring host."
        }

        val delivery = when (alert.delivery) {
            DeliveryState.Failed -> DeliverySummary(
                state = DeliveryState.Failed,
                lastAttemptLabel = "Last attempt 17m ago",
                attempts = listOf(
                    DeliveryAttempt("Push (FCM)", DeliveryState.Failed, "17m ago", "Device token rejected."),
                    DeliveryAttempt("Push (FCM)", DeliveryState.Failed, "19m ago", "Device token rejected."),
                    DeliveryAttempt("Push (FCM)", DeliveryState.Sent, "21m ago")
                ),
                detail = "Delivery failed after 3 attempts. This does not change the state of the alert."
            )
            DeliveryState.Retrying -> DeliverySummary(
                state = DeliveryState.Retrying,
                lastAttemptLabel = "Last attempt 6m ago",
                attempts = listOf(
                    DeliveryAttempt("Push (FCM)", DeliveryState.Retrying, "6m ago", "Retry scheduled."),
                    DeliveryAttempt("Push (FCM)", DeliveryState.Failed, "12m ago", "Transport timeout.")
                )
            )
            DeliveryState.Exhausted -> DeliverySummary(
                state = DeliveryState.Exhausted,
                lastAttemptLabel = "Last attempt 5h ago",
                attempts = listOf(
                    DeliveryAttempt("Push (FCM)", DeliveryState.Exhausted, "5h ago", "No further attempts will be made.")
                ),
                detail = "The incident was resolved regardless of the notification outcome."
            )
            DeliveryState.Unavailable -> DeliverySummary(
                state = DeliveryState.Unavailable,
                lastAttemptLabel = "Unknown",
                detail = "NEXA cannot read the delivery record for this alert."
            )
            else -> DeliverySummary(
                state = alert.delivery,
                lastAttemptLabel = alert.createdLabel,
                attempts = listOf(DeliveryAttempt("Push (FCM)", alert.delivery, alert.createdLabel))
            )
        }

        val timeline = buildList {
            add(ActivityEntry("${alert.id}-created", ActivityKind.AlertRaised, "Alert created", alert.id, alert.createdLabel, NexaStatus.Critical))
            if (alert.lifecycle == AlertLifecycle.Acknowledged || alert.lifecycle == AlertLifecycle.Resolved) {
                add(ActivityEntry("${alert.id}-ack", ActivityKind.AlertAcknowledged, "Acknowledged by operator", alert.id, alert.updatedLabel, NexaStatus.Information))
            }
            if (alert.id == "ALRT-1089") {
                add(ActivityEntry("${alert.id}-act", ActivityKind.EnforcementStarted, "Response action requested", alert.id, "17m ago", NexaStatus.Information))
            }
            if (alert.lifecycle == AlertLifecycle.Resolved) {
                add(ActivityEntry("${alert.id}-res", ActivityKind.AlertAcknowledged, "Resolved", alert.id, alert.updatedLabel, NexaStatus.Secure))
            }
            if (alert.lifecycle == AlertLifecycle.Ignored) {
                add(ActivityEntry("${alert.id}-ign", ActivityKind.AlertAcknowledged, "Ignored by operator", alert.id, alert.updatedLabel, NexaStatus.Paused))
            }
        }

        return AlertDetailUiState.Content(
            AlertDetailData(
                alert = alert,
                description = description,
                delivery = delivery,
                timeline = timeline,
                actions = availableAlertActions(alert),
                freshness = DataFreshness.Live
            )
        )
    }
}
