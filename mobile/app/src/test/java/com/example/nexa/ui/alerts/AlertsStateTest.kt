package com.example.nexa.ui.alerts

import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.Presence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the alert rules.
 *
 * The central ones assert that alert lifecycle and notification delivery
 * never contaminate each other, because that confusion would tell an
 * operator an incident was handled when only a message was sent — or that
 * an incident failed when only a message did.
 */
class AlertsStateTest {

    private fun deviceTarget(
        mac: String = "00:11:22:33:44:55",
        scope: String = "VLAN_A",
        identity: AlertIdentityRef? = AlertIdentityRef("TID-1", TrustState.Trusted),
        freshness: DataFreshness = DataFreshness.Live
    ) = AlertTarget.DeviceTarget(
        device = AlertDeviceRef(
            deviceId = "DEV-1",
            label = "Device",
            mac = mac,
            ip = "10.0.0.1",
            scope = scope,
            presence = Presence.Present,
            recordFreshness = freshness,
            lastObservedLabel = "now"
        ),
        identity = identity
    )

    private fun alert(
        id: String = "ALRT-1",
        title: String = "Alert",
        severity: AlertSeverity = AlertSeverity.Warning,
        lifecycle: AlertLifecycle = AlertLifecycle.New,
        delivery: DeliveryState = DeliveryState.Delivered,
        target: AlertTarget = deviceTarget(),
        ageMinutes: Int = 5
    ) = AlertListItem(
        id = id,
        title = title,
        severity = severity,
        lifecycle = lifecycle,
        delivery = delivery,
        target = target,
        createdLabel = "${ageMinutes}m ago",
        updatedLabel = "${ageMinutes}m ago",
        ageMinutes = ageMinutes
    )

    // ============================================================
    // THE CENTRAL SEPARATION
    // ============================================================

    /**
     * Required by the checkpoint: a NEW alert whose notification FAILED is
     * still a NEW alert. Delivery failure must not leak onto the incident.
     */
    @Test
    fun `new alert with failed notification stays new`() {
        val a = alert(lifecycle = AlertLifecycle.New, delivery = DeliveryState.Failed)
        assertEquals(AlertLifecycle.New, a.lifecycle)
        assertTrue(a.lifecycle.isOpen)
        assertTrue(a.delivery.isFailure)
        // The row badge reports lifecycle, never delivery.
        assertEquals(AlertLifecycle.New.status, rowLifecycleBadge(a))
        // And the alert is still counted as open, unacknowledged work.
        val counts = summarize(listOf(a))
        assertEquals(1, counts.open)
        assertEquals(1, counts.unacknowledged)
        assertEquals(1, counts.deliveryFailures)
    }

    /**
     * Required by the checkpoint: a RESOLVED alert whose notification failed
     * is still RESOLVED. The incident outcome does not depend on messaging.
     */
    @Test
    fun `resolved alert with failed notification stays resolved`() {
        val a = alert(lifecycle = AlertLifecycle.Resolved, delivery = DeliveryState.Exhausted)
        assertEquals(AlertLifecycle.Resolved, a.lifecycle)
        assertFalse(a.lifecycle.isOpen)
        assertTrue(a.delivery.isFailure)
        assertEquals(AlertLifecycle.Resolved.status, rowLifecycleBadge(a))
        val counts = summarize(listOf(a))
        assertEquals(0, counts.open)
        assertEquals(0, counts.unacknowledged)
        assertEquals(1, counts.deliveryFailures)
    }

    /** Delivery success never closes an incident. */
    @Test
    fun `delivered notification does not resolve the alert`() {
        val a = alert(lifecycle = AlertLifecycle.New, delivery = DeliveryState.Delivered)
        assertEquals(AlertLifecycle.New, a.lifecycle)
        assertTrue(a.lifecycle.isOpen)
    }

    /** There is no shared vocabulary between the two axes. */
    @Test
    fun `lifecycle and delivery vocabularies do not overlap`() {
        val lifecycleWords = AlertLifecycle.entries.map { it.label.lowercase() }.toSet()
        val deliveryWords = DeliveryState.entries.map { it.label.lowercase() }.toSet()
        assertTrue(lifecycleWords.intersect(deliveryWords).isEmpty())
    }

    /** Every delivery description names the notification, not the alert. */
    @Test
    fun `delivery explanations always refer to the notification`() {
        DeliveryState.entries.forEach { state ->
            assertTrue(
                "delivery state $state must describe the notification",
                state.explanation.lowercase().contains("notification")
            )
        }
    }

    /** Failure wording says outright that the alert is unaffected. */
    @Test
    fun `delivery failure explanation states the alert is unaffected`() {
        assertTrue(DeliveryState.Failed.explanation.contains("alert itself is unaffected"))
        assertTrue(DeliveryState.Exhausted.explanation.contains("alert itself is unaffected"))
    }

    /** Unreadable delivery is not reported as "not delivered". */
    @Test
    fun `unavailable delivery is distinct from failure`() {
        assertFalse(DeliveryState.Unavailable.isFailure)
        assertTrue(DeliveryState.Unavailable.explanation.contains("could not be read"))
    }

    // ============================================================
    // Acknowledgement is not resolution
    // ============================================================

    @Test
    fun `acknowledged alert is still open`() {
        val a = alert(lifecycle = AlertLifecycle.Acknowledged)
        assertTrue(a.lifecycle.isOpen)
        assertEquals(1, summarize(listOf(a)).open)
    }

    @Test
    fun `acknowledged explanation denies resolution`() {
        assertTrue(AlertLifecycle.Acknowledged.explanation.contains("not resolved"))
    }

    @Test
    fun `ignored alert is closed but is not resolved`() {
        val a = alert(lifecycle = AlertLifecycle.Ignored)
        assertFalse(a.lifecycle.isOpen)
        assertFalse(a.lifecycle == AlertLifecycle.Resolved)
        assertTrue(AlertLifecycle.Ignored.explanation.contains("without being resolved"))
    }

    // ============================================================
    // Action matrix
    // ============================================================

    @Test
    fun `a new alert offers acknowledge and not resolve`() {
        val actions = availableAlertActions(alert(lifecycle = AlertLifecycle.New))
        assertTrue(actions.any { it.kind == AlertActionKind.Acknowledge })
        assertFalse(actions.any { it.kind == AlertActionKind.Resolve })
    }

    @Test
    fun `an acknowledged alert offers resolve and not acknowledge`() {
        val actions = availableAlertActions(alert(lifecycle = AlertLifecycle.Acknowledged))
        assertTrue(actions.any { it.kind == AlertActionKind.Resolve })
        assertFalse(actions.any { it.kind == AlertActionKind.Acknowledge })
    }

    /** Closed incidents offer no enforcement response from history. */
    @Test
    fun `resolved alert offers no enforcement action`() {
        val actions = availableAlertActions(alert(lifecycle = AlertLifecycle.Resolved))
        assertTrue(actions.none { it.enforcement })
        assertTrue(actions.none { it.kind == AlertActionKind.QuarantineTarget })
    }

    @Test
    fun `ignored alert offers no enforcement action`() {
        val actions = availableAlertActions(alert(lifecycle = AlertLifecycle.Ignored))
        assertTrue(actions.none { it.enforcement })
    }

    /** An unresolved target cannot be acted on. */
    @Test
    fun `alert with unknown target offers no response action`() {
        val actions = availableAlertActions(alert(target = AlertTarget.Unknown))
        assertTrue(actions.none { it.enforcement })
        assertTrue(actions.none { it.kind == AlertActionKind.ViewDevice })
    }

    /** Reverification needs an identity; an observed device is not enough. */
    @Test
    fun `reverification is not offered without an identity`() {
        val actions = availableAlertActions(alert(target = deviceTarget(identity = null)))
        assertTrue(actions.any { it.kind == AlertActionKind.QuarantineTarget })
        assertTrue(actions.none { it.kind == AlertActionKind.RequireReverification })
        assertTrue(actions.none { it.kind == AlertActionKind.ViewIdentity })
    }

    @Test
    fun `reverification is not offered for a revoked identity`() {
        val target = deviceTarget(identity = AlertIdentityRef("TID-9", TrustState.Revoked))
        val actions = availableAlertActions(alert(target = target))
        assertTrue(actions.none { it.kind == AlertActionKind.RequireReverification })
    }

    /** Every enforcement action carries a Phase 4 code; lifecycle ones do not. */
    @Test
    fun `enforcement actions carry action codes and lifecycle actions do not`() {
        val actions = availableAlertActions(alert(lifecycle = AlertLifecycle.New))
        actions.filter { it.enforcement }.forEach { assertNotNull(it.actionCode) }
        actions.filter { it.kind == AlertActionKind.Acknowledge || it.kind == AlertActionKind.Ignore }
            .forEach { assertNull(it.actionCode) }
    }

    /** Lifecycle operations must never masquerade as enforcement. */
    @Test
    fun `lifecycle actions are never marked as enforcement`() {
        AlertLifecycle.entries.forEach { lifecycle ->
            availableAlertActions(alert(lifecycle = lifecycle))
                .filter {
                    it.kind == AlertActionKind.Acknowledge ||
                        it.kind == AlertActionKind.Resolve ||
                        it.kind == AlertActionKind.Ignore
                }
                .forEach { assertFalse(it.enforcement) }
        }
    }

    // ============================================================
    // Prioritization
    // ============================================================

    @Test
    fun `open alerts outrank closed ones regardless of severity`() {
        val resolvedCritical = alert(id = "closed", severity = AlertSeverity.Critical, lifecycle = AlertLifecycle.Resolved)
        val openInfo = alert(id = "open", severity = AlertSeverity.Information, lifecycle = AlertLifecycle.New)
        val sorted = listOf(resolvedCritical, openInfo).applySort(AlertSort.Attention)
        assertEquals("open", sorted.first().id)
    }

    @Test
    fun `unacknowledged outranks acknowledged at the same severity`() {
        val ack = alert(id = "ack", severity = AlertSeverity.Critical, lifecycle = AlertLifecycle.Acknowledged, ageMinutes = 1)
        val new = alert(id = "new", severity = AlertSeverity.Critical, lifecycle = AlertLifecycle.New, ageMinutes = 60)
        val sorted = listOf(ack, new).applySort(AlertSort.Attention)
        assertEquals("new", sorted.first().id)
    }

    /**
     * The §30 warning: an old critical must not outrank a newer critical
     * merely by being critical. Recency breaks the tie.
     */
    @Test
    fun `a newer critical outranks an older critical`() {
        val old = alert(id = "old", severity = AlertSeverity.Critical, ageMinutes = 600)
        val fresh = alert(id = "fresh", severity = AlertSeverity.Critical, ageMinutes = 2)
        val sorted = listOf(old, fresh).applySort(AlertSort.Attention)
        assertEquals("fresh", sorted.first().id)
    }

    @Test
    fun `critical outranks warning within the open set`() {
        val warning = alert(id = "warn", severity = AlertSeverity.Warning, ageMinutes = 1)
        val critical = alert(id = "crit", severity = AlertSeverity.Critical, ageMinutes = 30)
        val sorted = listOf(warning, critical).applySort(AlertSort.Attention)
        assertEquals("crit", sorted.first().id)
    }

    @Test
    fun `newest sort ignores severity`() {
        val old = alert(id = "old", severity = AlertSeverity.Critical, ageMinutes = 100)
        val fresh = alert(id = "fresh", severity = AlertSeverity.Information, ageMinutes = 1)
        assertEquals("fresh", listOf(old, fresh).applySort(AlertSort.Newest).first().id)
    }

    @Test
    fun `sorting is deterministic for identical rank and age`() {
        val a = alert(id = "AAA")
        val b = alert(id = "BBB")
        assertEquals(listOf("AAA", "BBB"), listOf(b, a).applySort(AlertSort.Attention).map { it.id })
    }

    @Test
    fun `sorting hides nothing`() {
        val all = AlertsPreview.alerts
        assertEquals(all.size, all.applySort(AlertSort.Attention).size)
    }

    // ============================================================
    // Views, search, filters
    // ============================================================

    @Test
    fun `open and history views are complementary`() {
        val all = AlertsPreview.alerts
        val open = all.applyView(AlertScopeView.Open)
        val history = all.applyView(AlertScopeView.History)
        assertEquals(all.size, open.size + history.size)
        assertTrue(open.all { it.lifecycle.isOpen })
        assertTrue(history.none { it.lifecycle.isOpen })
    }

    @Test
    fun `search matches id title device mac scope and identity`() {
        val all = AlertsPreview.alerts
        assertTrue(all.applyQuery("ALRT-1092").isNotEmpty())
        assertTrue(all.applyQuery("port scan").isNotEmpty())
        assertTrue(all.applyQuery("Build Server").isNotEmpty())
        assertTrue(all.applyQuery("3C:22").isNotEmpty())
        assertTrue(all.applyQuery("VLAN_GUEST").isNotEmpty())
        assertTrue(all.applyQuery("TID-88F1").isNotEmpty())
    }

    @Test
    fun `search with no match returns empty`() {
        assertTrue(AlertsPreview.alerts.applyQuery("nothing-matches-this").isEmpty())
    }

    @Test
    fun `severity filter narrows to the selected severities`() {
        val critical = AlertsPreview.alerts.applyFilters(AlertFilters(severity = setOf(AlertSeverity.Critical)))
        assertTrue(critical.isNotEmpty())
        assertTrue(critical.all { it.severity == AlertSeverity.Critical })
    }

    @Test
    fun `lifecycle filter is independent of delivery filter`() {
        val newAlerts = AlertsPreview.alerts.applyFilters(AlertFilters(lifecycle = setOf(AlertLifecycle.New)))
        assertTrue(newAlerts.all { it.lifecycle == AlertLifecycle.New })
        // Filtering by lifecycle must not exclude delivery failures.
        assertTrue(newAlerts.any { it.delivery != DeliveryState.Delivered })
    }

    @Test
    fun `delivery filter selects on notification state only`() {
        val failed = AlertsPreview.alerts.applyFilters(AlertFilters(onlyDeliveryFailures = true))
        assertTrue(failed.isNotEmpty())
        assertTrue(failed.all { it.delivery.isFailure })
        // And it spans more than one alert lifecycle state.
        assertTrue(failed.map { it.lifecycle }.distinct().size > 1)
    }

    @Test
    fun `scope filter selects by network scope`() {
        val guest = AlertsPreview.alerts.applyFilters(AlertFilters(scopes = setOf("VLAN_GUEST")))
        assertTrue(guest.isNotEmpty())
        assertTrue(guest.all { it.target.deviceRef?.scope == "VLAN_GUEST" })
    }

    @Test
    fun `active filter count reflects each selected facet`() {
        val filters = AlertFilters(
            severity = setOf(AlertSeverity.Critical),
            lifecycle = setOf(AlertLifecycle.New, AlertLifecycle.Acknowledged),
            onlyDeliveryFailures = true
        )
        assertTrue(filters.isActive)
        assertEquals(4, filters.activeCount)
        assertFalse(AlertFilters().isActive)
    }

    @Test
    fun `resolve applies view query filters and sort together`() {
        val result = AlertsPreview.alerts.resolve(
            query = "",
            filters = AlertFilters(severity = setOf(AlertSeverity.Critical)),
            sort = AlertSort.Attention,
            view = AlertScopeView.Open
        )
        assertTrue(result.all { it.lifecycle.isOpen && it.severity == AlertSeverity.Critical })
    }

    // ============================================================
    // Target relationships
    // ============================================================

    @Test
    fun `stale target observation is detected`() {
        val stale = deviceTarget(freshness = DataFreshness.Stale("3h ago"))
        assertTrue(stale.isStale)
        assertFalse(deviceTarget().isStale)
    }

    @Test
    fun `preview contains an alert whose target observation is stale`() {
        assertTrue(AlertsPreview.alerts.any { it.target.isStale })
    }

    @Test
    fun `an observed device target may carry no identity`() {
        val withoutIdentity = AlertsPreview.alerts.first { it.target.identityRef == null && it.target.deviceRef != null }
        assertNotNull(withoutIdentity.target.deviceRef)
        assertNull(withoutIdentity.target.identityRef)
    }

    // ============================================================
    // Counts and states
    // ============================================================

    @Test
    fun `summary counts open critical unacknowledged and delivery failures separately`() {
        val counts = summarize(AlertsPreview.alerts)
        assertTrue(counts.open > 0)
        assertTrue(counts.critical > 0)
        assertTrue(counts.unacknowledged > 0)
        assertTrue(counts.deliveryFailures > 0)
        // Delivery failures are not a subset of the open count.
        assertTrue(AlertsPreview.alerts.any { !it.lifecycle.isOpen && it.delivery.isFailure })
    }

    @Test
    fun `empty alert load is content and not unavailable`() {
        val empty = AlertsPreview.empty()
        assertTrue(empty is AlertsUiState.Content)
        assertTrue((empty as AlertsUiState.Content).all.isEmpty())
        assertTrue(AlertsPreview.unavailable() is AlertsUiState.Unavailable)
        assertTrue(AlertsPreview.offline() is AlertsUiState.Offline)
    }

    @Test
    fun `degraded and stale scenarios keep their content`() {
        val degraded = AlertsPreview.degraded() as AlertsUiState.Content
        assertTrue(degraded.degraded)
        assertTrue(degraded.all.isNotEmpty())

        val stale = AlertsPreview.stale() as AlertsUiState.Content
        assertTrue(stale.freshness is DataFreshness.Stale)
        assertTrue(stale.all.isNotEmpty())
    }

    @Test
    fun `unknown alert id reports unavailable instead of inventing one`() {
        assertTrue(AlertsPreview.detailFor("ALRT-NOPE") is AlertDetailUiState.Unavailable)
    }

    @Test
    fun `preview covers the required state combinations`() {
        val all = AlertsPreview.alerts
        assertTrue(all.any { it.severity == AlertSeverity.Critical && it.lifecycle == AlertLifecycle.New && it.delivery == DeliveryState.Delivered })
        assertTrue(all.any { it.severity == AlertSeverity.Critical && it.lifecycle == AlertLifecycle.Acknowledged && it.delivery == DeliveryState.Failed })
        assertTrue(all.any { it.severity == AlertSeverity.Warning && it.lifecycle == AlertLifecycle.New && it.delivery == DeliveryState.Retrying })
        assertTrue(all.any { it.lifecycle == AlertLifecycle.Resolved && it.delivery == DeliveryState.Delivered })
        assertTrue(all.any { it.delivery == DeliveryState.Unavailable })
        assertTrue(all.any { it.lifecycle == AlertLifecycle.Ignored })
    }

    @Test
    fun `delivery detail exposes multiple attempts for a failed notification`() {
        val detail = AlertsPreview.detailFor("ALRT-1089") as AlertDetailUiState.Content
        assertEquals(DeliveryState.Failed, detail.data.delivery.state)
        assertTrue(detail.data.delivery.attempts.size > 1)
        // The alert behind it is still open and still critical.
        assertEquals(AlertLifecycle.Acknowledged, detail.data.alert.lifecycle)
        assertEquals(AlertSeverity.Critical, detail.data.alert.severity)
        assertTrue(detail.data.alert.lifecycle.isOpen)
    }

    // ============================================================
    // Presentation
    // ============================================================

    @Test
    fun `severity and lifecycle map to different presentation axes`() {
        val criticalNew = alert(severity = AlertSeverity.Critical, lifecycle = AlertLifecycle.New)
        val criticalAck = alert(severity = AlertSeverity.Critical, lifecycle = AlertLifecycle.Acknowledged)
        // Same severity, different lifecycle badge.
        assertEquals(criticalNew.severity.status, criticalAck.severity.status)
        assertFalse(rowLifecycleBadge(criticalNew) == rowLifecycleBadge(criticalAck))
    }

    @Test
    fun `delivery warning appears only for failing or retrying notifications`() {
        assertNull(rowDeliveryWarning(alert(delivery = DeliveryState.Delivered)))
        assertNull(rowDeliveryWarning(alert(delivery = DeliveryState.Sent)))
        assertNotNull(rowDeliveryWarning(alert(delivery = DeliveryState.Failed)))
        assertNotNull(rowDeliveryWarning(alert(delivery = DeliveryState.Retrying)))
        // And it is always labelled as a notification.
        assertTrue(rowDeliveryWarning(alert(delivery = DeliveryState.Failed))!!.startsWith("NOTIFY"))
    }

    @Test
    fun `closed alerts drop to the quietest surface`() {
        assertEquals(
            com.example.nexa.theme.GlassVariant.Standard,
            surfaceFor(alert(severity = AlertSeverity.Critical, lifecycle = AlertLifecycle.Resolved))
        )
        assertEquals(
            com.example.nexa.theme.GlassVariant.Strong,
            surfaceFor(alert(severity = AlertSeverity.Critical, lifecycle = AlertLifecycle.New))
        )
    }

    @Test
    fun `subtitle reports the target without claiming state`() {
        val subtitle = alertSubtitle(alert(target = deviceTarget(scope = "VLAN_BUILD")))
        assertTrue(subtitle.contains("VLAN_BUILD"))
        assertFalse(subtitle.lowercase().contains("critical"))
        assertEquals("Target unresolved", alertSubtitle(alert(target = AlertTarget.Unknown)))
    }
}
