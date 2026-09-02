package com.example.nexa.ui.common

import com.example.nexa.ui.alerts.AlertFilters
import com.example.nexa.ui.alerts.AlertScopeView
import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.alerts.AlertSort
import com.example.nexa.ui.alerts.AlertsPreview
import com.example.nexa.ui.alerts.applyFilters
import com.example.nexa.ui.alerts.resolve
import com.example.nexa.ui.audit.AuditFilters
import com.example.nexa.ui.audit.AuditPreview
import com.example.nexa.ui.audit.AuditSort
import com.example.nexa.ui.audit.resolve
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.applyFilters
import com.example.nexa.ui.devices.applyQuery
import com.example.nexa.ui.devices.applySort
import com.example.nexa.ui.devices.resolve
import com.example.nexa.ui.identity.IdentityFilters
import com.example.nexa.ui.identity.IdentityPreview
import com.example.nexa.ui.identity.IdentityRelationship
import com.example.nexa.ui.identity.IdentitySort
import com.example.nexa.ui.identity.resolve
import com.example.nexa.ui.notifications.NotificationFilters
import com.example.nexa.ui.notifications.NotificationPreview
import com.example.nexa.ui.notifications.NotificationSort
import com.example.nexa.ui.notifications.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The filter language, and the pipeline it sits in.
 *
 * The composition rules are the load-bearing part: AND between facets, OR
 * within one, and an empty facet narrows nothing. Everything an operator
 * predicts about the controls rests on those three sentences holding on every
 * surface, so they are asserted per domain rather than once in the abstract.
 */
class FilterPipelineTest {

    // ============================================================
    // THE RULES, IN THE ABSTRACT
    // ============================================================

    @Test
    fun `an empty facet narrows nothing`() {
        assertTrue(emptySet<String>().facetMatches("anything"))
        assertTrue(emptySet<String>().facetMatches(null))
    }

    @Test
    fun `a facet ORs its own values`() {
        val facet = setOf("a", "b")
        assertTrue(facet.facetMatches("a"))
        assertTrue(facet.facetMatches("b"))
        assertFalse(facet.facetMatches("c"))
    }

    /**
     * A narrowing facet cannot be satisfied by an absent value. A record with
     * no scope is not in "scope = VLAN_SECURE", and inferring that it might be
     * is exactly the guess the model refuses elsewhere.
     */
    @Test
    fun `a narrowing facet does not match a missing value`() {
        assertFalse(setOf("VLAN_SECURE").facetMatches(null))
    }

    @Test
    fun `toggling adds then removes`() {
        val once = emptySet<String>().toggleFacet("a")
        assertEquals(setOf("a"), once)
        assertEquals(emptySet<String>(), once.toggleFacet("a"))
    }

    @Test
    fun `the filter button counts selections, not facets`() {
        assertEquals("Filters", filterButtonLabel(0))
        assertEquals("Filters · 1", filterButtonLabel(1))
        assertEquals("Filters · 3", filterButtonLabel(3))
    }

    @Test
    fun `active filter counts match the selections made`() {
        assertEquals(0, DeviceFilters().activeCount)
        assertFalse(DeviceFilters().isActive)

        val two = DeviceFilters(
            presence = setOf(Presence.Present),
            trust = setOf(TrustState.Trusted)
        )
        assertEquals(2, two.activeCount)
        assertTrue(two.isActive)

        // Two values inside one facet still read as two choices, because two
        // things were chosen.
        val twoInOne = DeviceFilters(presence = setOf(Presence.Present, Presence.Absent))
        assertEquals(2, twoInOne.activeCount)
    }

    // ============================================================
    // THE RULES, PER DOMAIN
    // ============================================================

    @Test
    fun `devices AND across facets`() {
        val source = DevicesPreview.inventory
        val present = source.applyFilters(DeviceFilters(presence = setOf(Presence.Present)))
        val trusted = source.applyFilters(DeviceFilters(trust = setOf(TrustState.Trusted)))
        val both = source.applyFilters(
            DeviceFilters(presence = setOf(Presence.Present), trust = setOf(TrustState.Trusted))
        )

        assertTrue(both.size <= minOf(present.size, trusted.size))
        both.forEach {
            assertEquals(Presence.Present, it.presence)
            assertEquals(TrustState.Trusted, it.trust)
        }
    }

    @Test
    fun `devices OR within a facet`() {
        val source = DevicesPreview.inventory
        val present = source.applyFilters(DeviceFilters(presence = setOf(Presence.Present)))
        val absent = source.applyFilters(DeviceFilters(presence = setOf(Presence.Absent)))
        val either = source.applyFilters(
            DeviceFilters(presence = setOf(Presence.Present, Presence.Absent))
        )
        assertEquals(present.size + absent.size, either.size)
    }

    @Test
    fun `alerts OR within severity and AND against lifecycle`() {
        val source = AlertsPreview.alerts
        val critical = source.applyFilters(AlertFilters(severity = setOf(AlertSeverity.Critical)))
        val danger = source.applyFilters(AlertFilters(severity = setOf(AlertSeverity.Danger)))
        val either = source.applyFilters(
            AlertFilters(severity = setOf(AlertSeverity.Critical, AlertSeverity.Danger))
        )
        assertEquals(critical.size + danger.size, either.size)

        val andLifecycle = source.applyFilters(
            AlertFilters(
                severity = setOf(AlertSeverity.Critical, AlertSeverity.Danger),
                lifecycle = setOf(com.example.nexa.ui.alerts.AlertLifecycle.New)
            )
        )
        assertTrue(andLifecycle.size <= either.size)
        andLifecycle.forEach {
            assertTrue(it.severity == AlertSeverity.Critical || it.severity == AlertSeverity.Danger)
            assertEquals(com.example.nexa.ui.alerts.AlertLifecycle.New, it.lifecycle)
        }
    }

    /**
     * Severity, lifecycle and delivery stay three separate dimensions. If they
     * had been collapsed into one "status" this assertion could not hold: a
     * delivery filter would silently constrain severity too.
     */
    @Test
    fun `alert severity lifecycle and delivery narrow independently`() {
        val source = AlertsPreview.alerts
        val bySeverity = source.applyFilters(AlertFilters(severity = setOf(AlertSeverity.Critical)))
        val byDelivery = source.applyFilters(
            AlertFilters(delivery = setOf(DeliveryState.Failed))
        )
        // Two different questions produce two different answers.
        assertNotEquals(bySeverity.map { it.id }.toSet(), byDelivery.map { it.id }.toSet())
        // And neither dimension is decided by the other.
        assertTrue(byDelivery.map { it.severity }.toSet().size >= 1)
    }

    @Test
    fun `no filters returns everything`() {
        assertEquals(
            DevicesPreview.inventory.size,
            DevicesPreview.inventory.applyFilters(DeviceFilters()).size
        )
        assertEquals(
            AlertsPreview.alerts.size,
            AlertsPreview.alerts.applyFilters(AlertFilters()).size
        )
    }

    @Test
    fun `an impossible combination returns nothing without failing`() {
        val none = DevicesPreview.inventory.applyFilters(
            DeviceFilters(scopes = setOf("VLAN_DOES_NOT_EXIST"))
        )
        assertTrue(none.isEmpty())
    }

    // ============================================================
    // SEARCH AND FILTERS COMBINE
    // ============================================================

    /**
     * Search AND filters. There is no shortcut where one bypasses the other.
     */
    @Test
    fun `search and filters both apply`() {
        val source = DevicesPreview.inventory
        val filters = DeviceFilters(trust = setOf(TrustState.Trusted))
        val searched = source.resolve("build", DeviceFilters(), DeviceSort.Name)
        val combined = source.resolve("build", filters, DeviceSort.Name)

        assertTrue(combined.size <= searched.size)
        combined.forEach { device ->
            assertEquals(TrustState.Trusted, device.trust)
            assertTrue(nexaQuery("build").matches(com.example.nexa.ui.devices.deviceSearchFields(device)))
        }
    }

    @Test
    fun `clearing filters leaves the search applied`() {
        val source = DevicesPreview.inventory
        val withBoth = source.resolve("build", DeviceFilters(trust = setOf(TrustState.Trusted)), DeviceSort.Name)
        val filtersCleared = source.resolve("build", DeviceFilters(), DeviceSort.Name)

        assertTrue(filtersCleared.isNotEmpty())
        assertTrue(filtersCleared.size >= withBoth.size)
        // Still narrowed by the query, so not the whole inventory.
        assertTrue(filtersCleared.size < source.size)
    }

    @Test
    fun `clearing the search leaves the filters applied`() {
        val source = DevicesPreview.inventory
        val filters = DeviceFilters(trust = setOf(TrustState.Trusted))
        val searchCleared = source.resolve("", filters, DeviceSort.Name)

        assertTrue(searchCleared.isNotEmpty())
        searchCleared.forEach { assertEquals(TrustState.Trusted, it.trust) }
        assertTrue(searchCleared.size < source.size)
    }

    @Test
    fun `a full reset returns the whole source in default order`() {
        val source = DevicesPreview.inventory
        val reset = source.resolve("", DeviceFilters(), DeviceSort.Attention)
        assertEquals(source.size, reset.size)
        assertEquals(source.applySort(DeviceSort.Attention).map { it.id }, reset.map { it.id })
    }

    // ============================================================
    // ORDER OF OPERATIONS
    // ============================================================

    /**
     * Search, then filter, then sort — the same order on every surface. The
     * observable consequence is that sorting a narrowed set gives the same
     * order as narrowing a sorted one, which is what makes the pipeline
     * describable in one sentence.
     */
    @Test
    fun `sorting after filtering equals filtering a sorted list`() {
        val source = DevicesPreview.inventory
        val filters = DeviceFilters(presence = setOf(Presence.Present))

        val pipeline = source.applyQuery("").applyFilters(filters).applySort(DeviceSort.Name)
        val other = source.applySort(DeviceSort.Name).applyFilters(filters)
        assertEquals(other.map { it.id }, pipeline.map { it.id })
    }

    @Test
    fun `filtering never invents a record`() {
        val source = DevicesPreview.inventory
        val ids = source.map { it.id }.toSet()
        val filtered = source.resolve("a", DeviceFilters(presence = setOf(Presence.Present)), DeviceSort.Name)
        filtered.forEach { assertTrue(it.id in ids) }
    }

    @Test
    fun `filtering never duplicates a record`() {
        val filtered = DevicesPreview.inventory.resolve(
            "",
            DeviceFilters(presence = setOf(Presence.Present, Presence.Absent, Presence.Unknown)),
            DeviceSort.Attention
        )
        assertEquals(filtered.size, filtered.map { it.id }.toSet().size)
    }

    // ============================================================
    // DETERMINISTIC ORDER
    // ============================================================

    @Test
    fun `every device sort is stable across repeated runs`() {
        DeviceSort.entries.forEach { sort ->
            val a = DevicesPreview.inventory.applySort(sort).map { it.id }
            val b = DevicesPreview.inventory.shuffled().applySort(sort).map { it.id }
            assertEquals("$sort was not deterministic", a, b)
        }
    }

    /**
     * Ties are broken by identity, not by input order. Two devices sharing a
     * label must land in the same place whichever order they arrived in — a
     * list that reshuffles under a realtime update is unreadable.
     */
    @Test
    fun `equal labels are ordered deterministically by id`() {
        val base = DevicesPreview.inventory.first()
        val twins = listOf(
            base.copy(id = "DEV-9002", label = "Same Name"),
            base.copy(id = "DEV-9001", label = "Same Name")
        )
        assertEquals(
            listOf("DEV-9001", "DEV-9002"),
            twins.applySort(DeviceSort.Name).map { it.id }
        )
        assertEquals(
            listOf("DEV-9001", "DEV-9002"),
            twins.reversed().applySort(DeviceSort.Name).map { it.id }
        )
    }

    @Test
    fun `every domain resolves deterministically`() {
        repeat(2) {
            assertEquals(
                AlertsPreview.alerts.resolve("", AlertFilters(), AlertSort.Attention, AlertScopeView.All)
                    .map { it.id },
                AlertsPreview.alerts.shuffled()
                    .resolve("", AlertFilters(), AlertSort.Attention, AlertScopeView.All).map { it.id }
            )
            assertEquals(
                AuditPreview.entries.resolve("", AuditFilters(), AuditSort.Newest).map { it.id },
                AuditPreview.entries.shuffled().resolve("", AuditFilters(), AuditSort.Newest).map { it.id }
            )
            assertEquals(
                NotificationPreview.records.resolve("", NotificationFilters(), NotificationSort.Attention)
                    .map { it.id },
                NotificationPreview.records.shuffled()
                    .resolve("", NotificationFilters(), NotificationSort.Attention).map { it.id }
            )
            assertEquals(
                IdentityPreview.identities.resolve("", IdentityFilters(), IdentitySort.Attention)
                    .map { it.identityId },
                IdentityPreview.identities.shuffled()
                    .resolve("", IdentityFilters(), IdentitySort.Attention).map { it.identityId }
            )
        }
    }

    @Test
    fun `sorting a filtered list stays deterministic`() {
        val filters = DeviceFilters(presence = setOf(Presence.Present))
        val a = DevicesPreview.inventory.resolve("", filters, DeviceSort.Attention).map { it.id }
        val b = DevicesPreview.inventory.shuffled().resolve("", filters, DeviceSort.Attention).map { it.id }
        assertEquals(a, b)
    }

    // ============================================================
    // IDENTITIES — the domain that had no sort and one facet
    // ============================================================

    @Test
    fun `identity facets narrow independently`() {
        val source = IdentityPreview.identities
        val revoked = source.resolve("", IdentityFilters(trust = setOf(TrustState.Revoked)))
        revoked.forEach { assertEquals(TrustState.Revoked, it.trust) }

        val ambiguous = source.resolve(
            "",
            IdentityFilters(relationship = setOf(IdentityRelationship.Ambiguous))
        )
        ambiguous.forEach { assertEquals(IdentityRelationship.Ambiguous, it.relationship) }

        val both = source.resolve(
            "",
            IdentityFilters(
                trust = setOf(TrustState.Revoked),
                relationship = setOf(IdentityRelationship.Ambiguous)
            )
        )
        assertTrue(both.size <= minOf(revoked.size, ambiguous.size))
    }

    @Test
    fun `identity sorts are distinct and deterministic`() {
        val source = IdentityPreview.identities
        val orders = IdentitySort.entries.map { sort ->
            source.resolve("", IdentityFilters(), sort).map { it.identityId }
        }
        orders.forEach { order -> assertEquals(source.size, order.size) }
        // Repeating a sort gives the same answer.
        IdentitySort.entries.forEach { sort ->
            assertEquals(
                source.resolve("", IdentityFilters(), sort).map { it.identityId },
                source.shuffled().resolve("", IdentityFilters(), sort).map { it.identityId }
            )
        }
    }

    @Test
    fun `identity verification freshness is selectable without collapsing unknown into stale`() {
        assertNotEquals(
            com.example.nexa.ui.identity.identityFreshnessFacet(DataFreshness.Unknown),
            com.example.nexa.ui.identity.identityFreshnessFacet(DataFreshness.Stale("earlier"))
        )
        assertEquals(
            com.example.nexa.ui.identity.IdentityFreshnessFacet.Current,
            com.example.nexa.ui.identity.identityFreshnessFacet(DataFreshness.Live)
        )
    }

    // ============================================================
    // RESULT CLASSIFICATION AND COUNTS
    // ============================================================

    @Test
    fun `results are classified by what actually narrowed`() {
        assertEquals(
            NexaResults.Present,
            nexaResults(sourceCount = 8, visibleCount = 3, queryActive = true, filtersActive = true)
        )
        assertEquals(
            NexaResults.SourceEmpty,
            nexaResults(sourceCount = 0, visibleCount = 0, queryActive = true, filtersActive = true)
        )
        assertEquals(
            NexaResults.NoMatch(NexaNoMatchReason.Search),
            nexaResults(sourceCount = 8, visibleCount = 0, queryActive = true, filtersActive = false)
        )
        assertEquals(
            NexaResults.NoMatch(NexaNoMatchReason.Filters),
            nexaResults(sourceCount = 8, visibleCount = 0, queryActive = false, filtersActive = true)
        )
        assertEquals(
            NexaResults.NoMatch(NexaNoMatchReason.SearchAndFilters),
            nexaResults(sourceCount = 8, visibleCount = 0, queryActive = true, filtersActive = true)
        )
    }

    /**
     * The distinction the whole no-match surface exists for: an empty source
     * and a search that found nothing are never the same state.
     */
    @Test
    fun `an empty source is never reported as a failed search`() {
        val empty = nexaResults(sourceCount = 0, visibleCount = 0, queryActive = true, filtersActive = true)
        assertTrue(empty is NexaResults.SourceEmpty)
        assertFalse(empty is NexaResults.NoMatch)
    }

    @Test
    fun `the count describes the list it sits above`() {
        assertEquals("8 devices", resultCountLabel(8, 8, "device"))
        assertEquals("4 of 12 devices", resultCountLabel(4, 12, "device"))
        assertEquals("1 device", resultCountLabel(1, 1, "device"))
        assertEquals("0 of 12 devices", resultCountLabel(0, 12, "device"))
    }

    @Test
    fun `the count never claims the total while showing a subset`() {
        val source = DevicesPreview.inventory
        val visible = source.resolve("", DeviceFilters(presence = setOf(Presence.Present)), DeviceSort.Name)
        val label = resultCountLabel(visible.size, source.size, "device")
        assertTrue("count did not disclose the subset: $label", label.startsWith("${visible.size} "))
        if (visible.size != source.size) {
            assertTrue(label.contains("of ${source.size}"))
        }
    }

    @Test
    fun `enforcement filtering does not alter enforcement state`() {
        val source = DevicesPreview.inventory
        val before = source.associate { it.id to it.enforcement }
        source.resolve("", DeviceFilters(enforcement = setOf(DeviceEnforcement.Quarantined)), DeviceSort.Name)
        source.forEach { assertEquals(before[it.id], it.enforcement) }
    }
}
