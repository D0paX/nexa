package com.example.nexa.ui.devices

import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the device inventory rules.
 *
 * The emphasis is on the security-relevant ones: that presence, trust and
 * enforcement stay independent, and that an action is never offered for a
 * state that cannot support it.
 */
class DevicesStateTest {

    private fun device(
        id: String = "DEV-1",
        label: String = "Device",
        mac: String = "00:11:22:33:44:55",
        ip: String? = "10.0.0.1",
        scope: String = "VLAN_A",
        presence: Presence = Presence.Present,
        trust: TrustState = TrustState.Trusted,
        identityId: String? = "TID-1",
        enforcement: DeviceEnforcement = DeviceEnforcement.Normal,
        alerts: DeviceAlerts = DeviceAlerts(0, 0, 0),
        freshness: DataFreshness = DataFreshness.Live
    ) = DeviceListItem(
        id = id,
        label = label,
        mac = mac,
        ip = ip,
        scope = scope,
        presence = presence,
        trust = trust,
        identityId = identityId,
        enforcement = enforcement,
        alerts = alerts,
        lastSeenLabel = "now",
        freshness = freshness
    )

    // ------------------------------------------------------------
    // Search
    // ------------------------------------------------------------

    @Test
    fun `search matches label case-insensitively`() {
        val list = listOf(device(label = "Build Server"), device(id = "DEV-2", label = "Reception Tablet"))
        assertEquals(1, list.applyQuery("build").size)
        assertEquals(1, list.applyQuery("BUILD").size)
    }

    @Test
    fun `search matches mac ip and scope`() {
        val list = listOf(device(mac = "AA:BB:CC:DD:EE:FF", ip = "192.168.4.9", scope = "VLAN_GUEST"))
        assertEquals(1, list.applyQuery("aa:bb").size)
        assertEquals(1, list.applyQuery("192.168").size)
        assertEquals(1, list.applyQuery("guest").size)
    }

    @Test
    fun `search matches trusted identity identifier`() {
        val list = listOf(device(identityId = "TID-88F1"))
        assertEquals(1, list.applyQuery("88f1").size)
    }

    @Test
    fun `blank query returns the full list`() {
        val list = listOf(device(), device(id = "DEV-2"))
        assertEquals(2, list.applyQuery("   ").size)
    }

    @Test
    fun `no match returns empty rather than everything`() {
        val list = listOf(device(label = "Build Server"))
        assertTrue(list.applyQuery("nonexistent").isEmpty())
    }

    // ------------------------------------------------------------
    // Filters
    // ------------------------------------------------------------

    @Test
    fun `filters within a facet are an OR and across facets an AND`() {
        val list = listOf(
            device(id = "a", presence = Presence.Present, trust = TrustState.Trusted),
            device(id = "b", presence = Presence.Absent, trust = TrustState.Trusted),
            device(id = "c", presence = Presence.Present, trust = TrustState.Unverified)
        )
        val result = list.applyFilters(
            DeviceFilters(
                presence = setOf(Presence.Present),
                trust = setOf(TrustState.Trusted)
            )
        )
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun `empty filter set does not exclude anything`() {
        val list = listOf(device(), device(id = "DEV-2", presence = Presence.Absent))
        assertEquals(2, list.applyFilters(DeviceFilters()).size)
    }

    @Test
    fun `alerts filter keeps only devices with alerts`() {
        val list = listOf(
            device(id = "quiet"),
            device(id = "noisy", alerts = DeviceAlerts(2, 1, 1))
        )
        val result = list.applyFilters(DeviceFilters(onlyWithAlerts = true))
        assertEquals(listOf("noisy"), result.map { it.id })
    }

    @Test
    fun `scope filter separates identical devices in different scopes`() {
        val list = listOf(
            device(id = "a", mac = "00:11:22:33:44:55", scope = "VLAN_A"),
            device(id = "b", mac = "00:11:22:33:44:55", scope = "VLAN_B")
        )
        val result = list.applyFilters(DeviceFilters(scopes = setOf("VLAN_B")))
        assertEquals(listOf("b"), result.map { it.id })
    }

    @Test
    fun `active filter count reflects every selected facet`() {
        val filters = DeviceFilters(
            presence = setOf(Presence.Present),
            trust = setOf(TrustState.Trusted, TrustState.Pending),
            onlyWithAlerts = true
        )
        assertTrue(filters.isActive)
        assertEquals(4, filters.activeCount)
        assertFalse(DeviceFilters().isActive)
    }

    // ------------------------------------------------------------
    // Sorting
    // ------------------------------------------------------------

    @Test
    fun `attention sort puts critical alerts first and healthy devices last`() {
        val list = listOf(
            device(id = "healthy", label = "A Healthy"),
            device(id = "critical", label = "Z Critical", alerts = DeviceAlerts(1, 1, 0)),
            device(id = "failed", label = "M Failed", enforcement = DeviceEnforcement.Failed)
        )
        val sorted = list.applySort(DeviceSort.Attention).map { it.id }
        assertEquals(listOf("critical", "failed", "healthy"), sorted)
    }

    /** Healthy devices sort down, but they are never removed from the list. */
    @Test
    fun `attention sort hides nothing`() {
        val list = listOf(device(id = "a"), device(id = "b", alerts = DeviceAlerts(1, 1, 0)))
        assertEquals(2, list.applySort(DeviceSort.Attention).size)
    }

    @Test
    fun `name sort is alphabetical and case-insensitive`() {
        val list = listOf(device(id = "1", label = "zebra"), device(id = "2", label = "Alpha"))
        assertEquals(listOf("2", "1"), list.applySort(DeviceSort.Name).map { it.id })
    }

    @Test
    fun `resolve applies query filters and sort together`() {
        val list = listOf(
            device(id = "a", label = "Server One", alerts = DeviceAlerts(1, 1, 0)),
            device(id = "b", label = "Server Two"),
            device(id = "c", label = "Tablet")
        )
        val result = list.resolve("server", DeviceFilters(), DeviceSort.Attention)
        assertEquals(listOf("a", "b"), result.map { it.id })
    }

    // ------------------------------------------------------------
    // Presence / trust independence
    // ------------------------------------------------------------

    /** Being on the network is not evidence of identity. */
    @Test
    fun `a present device can be unverified`() {
        val d = device(presence = Presence.Present, trust = TrustState.Unverified, identityId = null)
        assertEquals(Presence.Present, d.presence)
        assertEquals(TrustState.Unverified, d.trust)
        assertNull(d.identityId)
    }

    /** And having an identity is not evidence of being present. */
    @Test
    fun `an absent device can still be trusted`() {
        val d = device(presence = Presence.Absent, trust = TrustState.Trusted)
        assertEquals(Presence.Absent, d.presence)
        assertEquals(TrustState.Trusted, d.trust)
    }

    // ------------------------------------------------------------
    // Action visibility — the Phase 4 gate
    // ------------------------------------------------------------

    @Test
    fun `a normal device can be quarantined but not released`() {
        val actions = availableActions(device(enforcement = DeviceEnforcement.Normal))
        assertTrue(actions.any { it.kind == DeviceActionKind.Quarantine && it.enabled })
        assertFalse(actions.any { it.kind == DeviceActionKind.Release })
    }

    @Test
    fun `a quarantined device offers release and not another quarantine`() {
        val actions = availableActions(device(enforcement = DeviceEnforcement.Quarantined))
        assertTrue(actions.any { it.kind == DeviceActionKind.Release && it.enabled })
        assertFalse(actions.any { it.kind == DeviceActionKind.Quarantine })
    }

    /** An open circuit breaker disables actions and says so, rather than hiding them. */
    @Test
    fun `paused enforcement disables the action with a stated reason`() {
        val actions = availableActions(device(enforcement = DeviceEnforcement.Paused))
        val quarantine = actions.single { it.kind == DeviceActionKind.Quarantine }
        assertFalse(quarantine.enabled)
        assertNotNull(quarantine.disabledReason)
        assertTrue(quarantine.disabledReason!!.contains("circuit breaker"))
    }

    @Test
    fun `a device mid-reconciliation cannot be acted on`() {
        val actions = availableActions(device(enforcement = DeviceEnforcement.Reconciling))
        assertTrue(actions.none { it.kind == DeviceActionKind.Quarantine && it.enabled })
    }

    @Test
    fun `unknown enforcement state blocks the action`() {
        val quarantine = availableActions(device(enforcement = DeviceEnforcement.Unknown))
            .single { it.kind == DeviceActionKind.Quarantine }
        assertFalse(quarantine.enabled)
    }

    /** Reverification is a Phase 2 concept: no identity, nothing to reverify. */
    @Test
    fun `reverification is offered only when a trusted identity exists`() {
        assertTrue(
            availableActions(device(trust = TrustState.Trusted))
                .any { it.kind == DeviceActionKind.RequireReverification }
        )
        assertTrue(
            availableActions(device(trust = TrustState.Pending))
                .any { it.kind == DeviceActionKind.RequireReverification }
        )
        assertFalse(
            availableActions(device(trust = TrustState.Unverified, identityId = null))
                .any { it.kind == DeviceActionKind.RequireReverification }
        )
        assertFalse(
            availableActions(device(trust = TrustState.Revoked))
                .any { it.kind == DeviceActionKind.RequireReverification }
        )
    }

    @Test
    fun `every offered action carries a Phase 4 action code`() {
        DeviceEnforcement.entries.forEach { state ->
            availableActions(device(enforcement = state)).forEach { action ->
                assertTrue(
                    "action ${action.kind} must carry an action code",
                    action.actionCode.isNotBlank()
                )
            }
        }
    }

    // ------------------------------------------------------------
    // Presentation mapping
    // ------------------------------------------------------------

    @Test
    fun `a healthy device carries no attention badge`() {
        assertNull(attentionBadge(device()))
        assertNull(attentionLabel(device()))
    }

    @Test
    fun `critical alerts outrank quarantine on the badge`() {
        val d = device(enforcement = DeviceEnforcement.Quarantined, alerts = DeviceAlerts(1, 1, 0))
        assertEquals("CRITICAL", attentionLabel(d))
    }

    @Test
    fun `stale and unknown freshness are surfaced distinctly`() {
        assertEquals("STALE", attentionLabel(device(freshness = DataFreshness.Stale("2h"))))
        assertEquals("UNKNOWN", attentionLabel(device(freshness = DataFreshness.Unknown)))
    }

    @Test
    fun `subtitle keeps presence trust and scope as separate facts`() {
        val subtitle = deviceSubtitle(device(presence = Presence.Present, trust = TrustState.Unverified, scope = "VLAN_GUEST"))
        assertEquals("Present · Unverified · VLAN_GUEST", subtitle)
    }

    // ------------------------------------------------------------
    // Preview inventory integrity
    // ------------------------------------------------------------

    @Test
    fun `preview inventory exercises the states the UI must distinguish`() {
        val inv = DevicesPreview.inventory
        assertTrue(inv.any { it.trust == TrustState.Trusted })
        assertTrue(inv.any { it.trust == TrustState.Unverified })
        assertTrue(inv.any { it.trust == TrustState.Revoked })
        assertTrue(inv.any { it.trust == TrustState.Pending })
        assertTrue(inv.any { it.enforcement == DeviceEnforcement.Quarantined })
        assertTrue(inv.any { it.enforcement == DeviceEnforcement.Failed })
        assertTrue(inv.any { it.enforcement == DeviceEnforcement.Paused })
        assertTrue(inv.any { it.presence == Presence.Unknown })
        assertTrue(inv.any { it.freshness is DataFreshness.Stale })
    }

    @Test
    fun `an empty inventory is content rather than unavailable`() {
        val state = DevicesPreview.empty()
        assertTrue(state is DevicesUiState.Content)
        assertTrue((state as DevicesUiState.Content).all.isEmpty())
        assertTrue(DevicesPreview.unavailable() is DevicesUiState.Unavailable)
    }

    @Test
    fun `detail for an unknown mac reports unavailable instead of inventing a device`() {
        assertTrue(DevicesPreview.detailFor("FF:FF:FF:FF:FF:FF") is DeviceDetailUiState.Unavailable)
    }

    /** Enforcement ownership is stated against a scope, never an IP address. */
    @Test
    fun `quarantined device reports ownership scope and no ip-based ownership`() {
        val state = DevicesPreview.detailFor("00:1A:2B:3C:4D:5E") as DeviceDetailUiState.Content
        val enforcement = state.data.enforcement
        assertEquals(DeviceEnforcement.Quarantined, enforcement.state)
        assertEquals("VLAN_SECURE", enforcement.ownershipScope)
    }

    @Test
    fun `a stale device marks its target context as stale`() {
        val state = DevicesPreview.detailFor("AA:BB:CC:DD:EE:FF") as DeviceDetailUiState.Content
        assertTrue(state.data.enforcement.targetStale)
    }

    @Test
    fun `an observed device with no identity has no trusted identity context`() {
        val state = DevicesPreview.detailFor("00:5E:4D:3C:2B:1A") as DeviceDetailUiState.Content
        assertNull(state.data.identity)
        assertEquals(TrustState.Unverified, state.data.device.trust)
    }
}
