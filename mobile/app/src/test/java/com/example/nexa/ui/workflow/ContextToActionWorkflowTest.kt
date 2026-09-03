package com.example.nexa.ui.workflow

import com.example.nexa.DeviceDetail
import com.example.nexa.ui.audit.AuditCategory
import com.example.nexa.ui.audit.AuditPreview
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.deeplink.DeepLinkResolution
import com.example.nexa.ui.deeplink.NexaDeepLinkResolver
import com.example.nexa.ui.deeplink.PreviewDeepLinkCatalog
import com.example.nexa.ui.deeplink.toNavKey
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceListItem
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.DevicesUiState
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.resolve
import com.example.nexa.ui.enforcement.ActionAvailability
import com.example.nexa.ui.enforcement.ActionContext
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.enforcement.EnforcementPreview
import com.example.nexa.ui.enforcement.availabilityOf
import com.example.nexa.ui.notifications.NotificationTarget
import com.example.nexa.push.PushInbox
import com.example.nexa.push.PushParseResult
import com.example.nexa.push.PushPayloadParser
import com.example.nexa.push.debug.PushFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * INTEGRATION — how an operator arrives somewhere, and what arriving buys them.
 *
 * Three ways into a context: a notification, a link, and a filtered list. All
 * three are navigation. None of them is evidence about the target, and none of
 * them contributes anything to whether an action may be requested once the
 * operator is there.
 *
 * That is the property under test. Each journey takes the real route — the push
 * parser and inbox, the deep-link parser and resolver, the search and filter
 * pipeline — and then asks the eligibility matrix the same question it would
 * have been asked had the operator walked there themselves. The answer has to
 * be identical.
 */
class ContextToActionWorkflowTest {

    private val resolver = NexaDeepLinkResolver(PreviewDeepLinkCatalog)

    @Before
    fun setUp() {
        PushInbox.clear()
        EnforcementPreview.reset()
    }

    @After
    fun tearDown() {
        PushInbox.clear()
        EnforcementPreview.reset()
    }

    /**
     * The context an operator would get for a device, built from the device's
     * own state — which is the only thing any of these routes may contribute.
     */
    private fun contextFor(
        device: DeviceListItem,
        action: EnforcementAction = EnforcementAction.QuarantineDevice
    ): ActionContext = EnforcementPreview.context(
        action = action,
        authorization = AuthorizationState.Authorized,
        mode = ExecutionMode.Enforce,
        enforcement = device.enforcement,
        target = EnforcementPreview.target(
            deviceId = device.id,
            label = device.label,
            mac = device.mac,
            ip = device.ip,
            scope = device.scope,
            presence = device.presence,
            identityId = device.identityId,
            trust = device.trust,
            freshness = device.freshness,
            lastObserved = device.lastSeenLabel
        )
    )

    private fun ActionContext.offered() = availabilityOf(this) is ActionAvailability.Available

    // ============================================================
    // JOURNEY H — NOTIFICATION → CONTEXT → ACTION
    // ============================================================

    /**
     * A message arrives, is validated, becomes a record, and names a target.
     * Everything about that is delivery; none of it is a finding about the
     * device or a permission to act on it.
     */
    @Test
    fun journeyH_a_notification_carries_context_and_confers_nothing() {
        val parsed = PushPayloadParser.parse(PushFixtures.criticalAlert)
        assertTrue(parsed is PushParseResult.Accepted)
        val payload = (parsed as PushParseResult.Accepted).payload

        assertTrue("the message was not recorded", PushInbox.onIncomingPush(payload))
        val record = PushInbox.records.value.first { it.id == payload.notificationId }

        // It names a target, and that is the whole of its contribution.
        val target = record.target
        assertTrue(
            "the notification named no context to open",
            target !is NotificationTarget.None
        )

        // What it named, it named structurally: a device target carries an
        // address, a scope and how current the observation is. There is no
        // field on it for trust, for authorization or for an execution mode,
        // so a message cannot carry permission even if a publisher tried to
        // put it there.
        if (target is NotificationTarget.Device) {
            val fields = NotificationTarget.Device::class.java.declaredFields.map { it.name }
            listOf("trust", "authorization", "executionMode", "authorized").forEach { forbidden ->
                assertFalse(
                    "a notification target carries a $forbidden field",
                    fields.any { it.equals(forbidden, ignoreCase = true) }
                )
            }
        }

        // And the action is prepared from the inventory record, which the
        // message had no part in writing.
        val named = (target as? NotificationTarget.Device)?.deviceId
        val device = DevicesPreview.inventory.firstOrNull { it.id == named }
            ?: DevicesPreview.inventory.first()
        assertEquals(
            "the prepared target was taken from the message rather than the inventory",
            device.mac,
            contextFor(device).target.mac
        )
    }

    /**
     * A message about a device NEXA cannot currently see does not make that
     * device actionable, however urgent the message was.
     */
    @Test
    fun journeyH_a_critical_notification_does_not_unblock_a_stale_target() {
        val parsed = PushPayloadParser.parse(PushFixtures.criticalAlert)
        val payload = (parsed as PushParseResult.Accepted).payload
        PushInbox.onIncomingPush(payload)

        val stale = contextFor(
            DevicesPreview.inventory.first().copy(
                freshness = DataFreshness.Stale("3h ago"),
                lastSeenLabel = "3h ago",
                enforcement = DeviceEnforcement.Normal
            )
        )
        assertFalse("a critical alert made a stale target actionable", stale.offered())
    }

    /** Delivery state is its own axis and never reads as an outcome. */
    @Test
    fun journeyH_a_delivered_message_is_not_a_completed_action() {
        val payload = (PushPayloadParser.parse(PushFixtures.criticalAlert)
            as PushParseResult.Accepted).payload
        PushInbox.onIncomingPush(payload)
        val record = PushInbox.records.value.first()

        // Nothing in the delivery record claims an execution happened.
        val text = (record.subject + " " + record.delivery.state.name).lowercase()
        assertFalse(text.contains("succeeded"))
        assertFalse(text.contains("enforced"))
    }

    /** The same message twice is one record, so a retry cannot double a count. */
    @Test
    fun journeyH_a_retried_message_does_not_become_two_records() {
        val payload = (PushPayloadParser.parse(PushFixtures.criticalAlert)
            as PushParseResult.Accepted).payload
        assertTrue(PushInbox.onIncomingPush(payload))
        assertFalse(PushInbox.onIncomingPush(payload))
        assertEquals(1, PushInbox.records.value.count { it.id == payload.notificationId })
    }

    // ============================================================
    // JOURNEY I — DEEP LINK → CONTEXT → ACTION
    // ============================================================

    /**
     * The link names a device record. The route is built from the inventory,
     * and the action is then evaluated from the device the inventory returned
     * — never from anything the link said.
     */
    @Test
    fun journeyI_a_link_opens_a_context_and_the_action_is_judged_on_its_own() {
        val device = DevicesPreview.inventory.first()
        val resolution = resolver.resolve("nexa://v1/device/${device.id}")
        assertTrue(resolution is DeepLinkResolution.Resolved)
        assertEquals(DeviceDetail(device.mac), resolution.toNavKey())

        // The device the route landed on, looked up the way the screen does.
        val landed = DevicesPreview.inventory.first { it.mac == device.mac }
        assertEquals(device.id, landed.id)

        val context = contextFor(landed)
        assertEquals(
            "the link changed the eligibility answer",
            availabilityOf(contextFor(device)),
            availabilityOf(context)
        )
    }

    /**
     * A link that resolves to nothing produces no target to act on, so there
     * is nothing to prepare an action against.
     */
    @Test
    fun journeyI_an_unresolvable_link_yields_no_target() {
        val resolution = resolver.resolve("nexa://v1/device/DEV-DOES-NOT-EXIST")
        assertTrue(resolution is DeepLinkResolution.ObjectUnavailable)
        assertNull(
            "an unknown device id produced an inventory record",
            DevicesPreview.inventory.firstOrNull { it.id == "DEV-DOES-NOT-EXIST" }
        )
    }

    /**
     * An action-shaped link does not reach an action. Covered in depth by the
     * deep-link security suite; asserted here because it is the step this
     * journey would be worthless without.
     */
    @Test
    fun journeyI_no_link_shape_reaches_an_action() {
        listOf(
            "nexa://v1/device/DEV-1001/quarantine",
            "nexa://v1/quarantine/DEV-1001",
            "nexa://v1/action/ACT-1",
            "nexa://v1/execute/DEV-1001"
        ).forEach { raw ->
            val key = resolver.resolve(raw).toNavKey()
            assertFalse(
                "$raw produced a device route",
                key is DeviceDetail
            )
        }
    }

    // ============================================================
    // JOURNEY J — SEARCH / FILTER → CONTEXT → ACTION
    // ============================================================

    /**
     * Filtering is presentation. A device that survives a Trusted filter is
     * not more authorized than the same device in an unfiltered list.
     */
    @Test
    fun journeyJ_a_filter_does_not_grant_permission() {
        val filtered = DevicesPreview.inventory.resolve(
            "",
            DeviceFilters(trust = setOf(TrustState.Trusted)),
            DeviceSort.Name
        )
        assertTrue("the filter matched nothing to test with", filtered.isNotEmpty())

        filtered.forEach { device ->
            val viaFilter = contextFor(device)
            val direct = contextFor(DevicesPreview.inventory.first { it.id == device.id })
            assertEquals(
                "${device.id} was judged differently after being filtered",
                availabilityOf(direct),
                availabilityOf(viaFilter)
            )
        }
    }

    /** And a search does not either, including one that returns a single row. */
    @Test
    fun journeyJ_a_search_does_not_grant_permission() {
        val term = DevicesPreview.inventory.first().label.substringBefore(' ').lowercase()
        val found = DevicesPreview.inventory.resolve(term, DeviceFilters(), DeviceSort.Name)
        assertTrue("the search matched nothing to test with", found.isNotEmpty())

        found.forEach { device ->
            assertEquals(
                "${device.id} was judged differently after being searched for",
                availabilityOf(contextFor(DevicesPreview.inventory.first { it.id == device.id })),
                availabilityOf(contextFor(device))
            )
        }
    }

    /**
     * The case that matters: a record that reaches the top of a filtered list
     * while being stale, or unidentified, is still refused when an action is
     * prepared against it.
     */
    @Test
    fun journeyJ_a_filtered_but_unsafe_record_is_still_refused() {
        val unsafe = listOf(
            "stale" to DevicesPreview.inventory.first().copy(
                freshness = DataFreshness.Stale("3h ago"),
                enforcement = DeviceEnforcement.Normal
            ),
            "unknown observation" to DevicesPreview.inventory.first().copy(
                freshness = DataFreshness.Unknown,
                enforcement = DeviceEnforcement.Normal
            ),
            "unknown enforcement" to DevicesPreview.inventory.first().copy(
                freshness = DataFreshness.Live,
                enforcement = DeviceEnforcement.Unknown
            )
        )
        unsafe.forEach { (name, device) ->
            val filtered = listOf(device).resolve("", DeviceFilters(), DeviceSort.Name)
            assertEquals("the fixture did not survive filtering", 1, filtered.size)
            assertFalse(
                "a $name record was actionable because it was on screen",
                contextFor(filtered.first()).offered()
            )
        }
    }

    // ============================================================
    // WORKFLOW 1 — INSPECTION REFLECTS WHAT IS KNOWN
    // ============================================================

    /**
     * A device screen that could not be refreshed says so, and the records it
     * shows carry the age they actually have. Lack of data never renders as a
     * healthy device.
     */
    @Test
    fun inspection_under_a_degraded_condition_stays_truthful() {
        val offline = DevicesPreview.offlineWithCache() as DevicesUiState.Content
        assertTrue("an offline inventory did not say so", offline.offline)
        assertTrue(
            "an offline inventory claimed to be current",
            offline.freshness !is DataFreshness.Live
        )

        val degraded = DevicesPreview.degraded() as DevicesUiState.Content
        assertTrue("a partial inventory did not say so", degraded.degraded)

        // Unavailable is its own state, not an empty list: NEXA claims no
        // inventory rather than claiming an empty one.
        assertTrue(
            "an unavailable inventory produced a device list",
            DevicesPreview.unavailable() is DevicesUiState.Unavailable
        )
        assertTrue(
            "an offline inventory produced a device list",
            DevicesPreview.offline() is DevicesUiState.Offline
        )
    }

    /** And a device carrying no identity is presented as such, not as trusted. */
    @Test
    fun inspection_never_upgrades_an_unidentified_device() {
        DevicesPreview.inventory.filter { it.identityId == null }.forEach { device ->
            assertFalse(
                "${device.id} had no identity but was reported trusted",
                device.trust == TrustState.Trusted
            )
        }
    }

    // ============================================================
    // AUDIT CONTINUITY
    // ============================================================

    /**
     * A completed action remains traceable, and what the record says about how
     * it ran survives with it. An audit entry for a simulated run that lost its
     * mode would read as a live enforcement change afterwards.
     */
    @Test
    fun audit_preserves_the_execution_mode_of_every_action_record() {
        val actionRecords = AuditPreview.entries.filter { it.category == AuditCategory.Action }
        assertTrue("there were no action records to check", actionRecords.isNotEmpty())
        actionRecords.forEach { entry ->
            assertNotNull(
                "${entry.id} recorded an action without saying how it ran",
                entry.executionMode
            )
        }
    }

    /** Simulated records say so, and are never counted as live enforcement. */
    @Test
    fun audit_keeps_simulated_runs_distinguishable() {
        val simulated = AuditPreview.entries.filter { it.isSimulated }
        simulated.forEach { entry ->
            assertEquals(ExecutionMode.AuditOnly, entry.executionMode)
        }
        val live = AuditPreview.entries.filter { it.executionMode == ExecutionMode.Enforce }
        live.forEach { entry ->
            assertFalse("${entry.id} was both live and simulated", entry.isSimulated)
        }
    }

    /**
     * Reverification is a trust event, and is recorded as one. It does not get
     * an audit family of its own, and it does not land among the enforcement
     * records.
     */
    @Test
    fun audit_files_reverification_under_trust_rather_than_enforcement() {
        val reverification = AuditPreview.entries.filter {
            it.actionCode == EnforcementAction.RequireReverification.code
        }
        reverification.forEach { entry ->
            assertFalse(
                "${entry.id} filed a trust operation as a device event",
                entry.category == AuditCategory.Device
            )
        }
        // And no new family was invented for it.
        val families = AuditPreview.entries.map { it.category }.toSet()
        assertTrue(
            "an audit category outside the Phase 1-4 vocabulary appeared",
            families.all { it in AuditCategory.entries }
        )
    }

    /** Records carry a correlation handle, so one action's events stay one story. */
    @Test
    fun audit_groups_an_action_s_events_under_one_correlation() {
        val correlated = AuditPreview.entries
            .filter { it.category == AuditCategory.Action && it.correlationId != null }
            .groupBy { it.correlationId }
        assertTrue("no action events were correlated at all", correlated.isNotEmpty())
        correlated.forEach { (id, entries) ->
            val codes = entries.mapNotNull { it.actionCode }.toSet()
            assertTrue(
                "correlation $id mixed two different actions: $codes",
                codes.size <= 1
            )
        }
    }

    // ============================================================
    // PRESENTATION RETENTION IS NOT SECURITY RETENTION
    // ============================================================

    /**
     * Returning from a detail screen keeps the query and filters — that is
     * Phase 5.21 and it is a convenience. What it must not keep is a decision:
     * the same device evaluated twice is evaluated twice.
     */
    @Test
    fun returning_to_a_list_reuses_the_view_and_not_the_verdict() {
        val filters = DeviceFilters(presence = setOf(Presence.Present))
        val query = "server"

        val before = DevicesPreview.inventory.resolve(query, filters, DeviceSort.Attention)
        val after = DevicesPreview.inventory.resolve(query, filters, DeviceSort.Attention)
        assertEquals("the view did not survive the round trip", before.map { it.id }, after.map { it.id })

        // The verdict is recomputed from the record, not remembered from it.
        before.forEach { device ->
            val first = availabilityOf(contextFor(device))
            val moved = availabilityOf(
                contextFor(device.copy(freshness = DataFreshness.Stale("3h ago")))
            )
            if (first is ActionAvailability.Available) {
                assertFalse(
                    "${device.id} kept its earlier verdict after its state moved",
                    moved is ActionAvailability.Available
                )
            }
        }
    }
}
