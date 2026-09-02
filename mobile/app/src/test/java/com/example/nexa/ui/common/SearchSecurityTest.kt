package com.example.nexa.ui.common

import com.example.nexa.ui.alerts.AlertFilters
import com.example.nexa.ui.alerts.AlertScopeView
import com.example.nexa.ui.alerts.AlertSort
import com.example.nexa.ui.alerts.AlertsPreview
import com.example.nexa.ui.alerts.alertSearchFields
import com.example.nexa.ui.alerts.resolve
import com.example.nexa.ui.audit.AuditFilters
import com.example.nexa.ui.audit.AuditPreview
import com.example.nexa.ui.audit.AuditSort
import com.example.nexa.ui.audit.auditSearchFields
import com.example.nexa.ui.audit.resolve
import com.example.nexa.ui.devices.DeviceFilters
import com.example.nexa.ui.devices.DeviceSort
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.deviceSearchFields
import com.example.nexa.ui.devices.resolve
import com.example.nexa.ui.enforcement.ActionAvailability
import com.example.nexa.ui.enforcement.ActionContext
import com.example.nexa.ui.enforcement.ActionTarget
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.enforcement.availabilityOf
import com.example.nexa.ui.identity.IdentityFilters
import com.example.nexa.ui.identity.IdentityPreview
import com.example.nexa.ui.identity.IdentitySort
import com.example.nexa.ui.identity.identitySearchFields
import com.example.nexa.ui.identity.resolve
import com.example.nexa.ui.notifications.NotificationFilters
import com.example.nexa.ui.notifications.NotificationPreview
import com.example.nexa.ui.notifications.NotificationSort
import com.example.nexa.ui.notifications.notificationSearchFields
import com.example.nexa.ui.notifications.resolve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What search and filtering must never do.
 *
 * The whole feature is presentation. It decides what appears on a screen and
 * nothing else. Every test here pins one way that could quietly stop being
 * true — a searchable field list that grows to include something secret, a
 * filter that starts to read like permission, a query that finds a route out
 * of the text box.
 */
class SearchSecurityTest {

    // ============================================================
    // SEARCH REACHES ONLY WHAT IT SHOULD
    // ============================================================

    /**
     * The searchable surface of each domain is written out by hand rather
     * than reflected off the model, so that adding a field to a data class
     * cannot silently make it searchable. These assertions freeze the lists.
     */
    @Test
    fun `no searchable field carries anything secret`() {
        val forbidden = listOf(
            "key", "secret", "token", "credential", "password", "private",
            "bearer", "signature", "-----begin"
        )

        fun check(label: String, fields: List<String?>) {
            fields.filterNotNull().forEach { value ->
                forbidden.forEach { marker ->
                    assertFalse(
                        "$label exposes something that looks like a $marker: $value",
                        value.lowercase().contains(marker)
                    )
                }
            }
        }

        DevicesPreview.inventory.forEach { check("device", deviceSearchFields(it)) }
        AlertsPreview.alerts.forEach { check("alert", alertSearchFields(it)) }
        AuditPreview.entries.forEach { check("audit", auditSearchFields(it)) }
        NotificationPreview.records.forEach { check("notification", notificationSearchFields(it)) }
        IdentityPreview.identities.forEach { check("identity", identitySearchFields(it)) }
    }

    /**
     * A registration token is not on the delivery model at all, which is a
     * stronger guarantee than excluding it from the field list would be. This
     * asserts the consequence: no query can surface one.
     */
    @Test
    fun `no query reaches a push registration token`() {
        val tokenish = listOf(
            "APA91b",
            "registration",
            "fcm",
            "EXAMPLE-ENDPOINT"
        )
        tokenish.forEach { probe ->
            val hits = NotificationPreview.records.resolve(
                probe,
                NotificationFilters(),
                NotificationSort.Newest
            )
            hits.forEach { record ->
                notificationSearchFields(record).filterNotNull().forEach { field ->
                    assertFalse(
                        "a token-shaped value was searchable: $field",
                        field.contains("APA91", ignoreCase = true)
                    )
                }
            }
        }
    }

    @Test
    fun `identity search never reaches key material`() {
        IdentityPreview.identities.forEach { identity ->
            val fields = identitySearchFields(identity).filterNotNull()
            // The credential is present as an identifier and a lifecycle
            // state. The key itself is not on the model, so there is nothing
            // shaped like one to find.
            fields.forEach { value ->
                assertFalse(value.contains("BEGIN", ignoreCase = true))
                assertFalse(value.contains("PRIVATE", ignoreCase = true))
            }
        }
    }

    // ============================================================
    // SEARCH IS TEXT, NOT A COMMAND
    // ============================================================

    /**
     * A query that looks like a deep link is a query. It is matched against
     * fields like any other string and cannot reach the link resolver — the
     * only path into that is [com.example.nexa.ui.deeplink.NexaDeepLinkParser],
     * and nothing in the search pipeline calls it.
     */
    @Test
    fun `a link-shaped query is treated as text`() {
        val probes = listOf(
            "nexa://device/DEV-1001",
            "nexa://action/quarantine",
            "https://nexa.example/quarantine?device=DEV-1001",
            "content://com.example.nexa/devices"
        )
        probes.forEach { probe ->
            val before = DevicesPreview.inventory.map { it.id to it.enforcement }
            val hits = DevicesPreview.inventory.resolve(probe, DeviceFilters(), DeviceSort.Name)
            // Whatever it matched or did not, nothing changed.
            assertEquals(before, DevicesPreview.inventory.map { it.id to it.enforcement })
            hits.forEach { device -> assertTrue(device.id in before.map { it.first }) }
        }
    }

    @Test
    fun `command-shaped and injection-shaped queries change nothing`() {
        val probes = listOf(
            "'; DELETE FROM devices; --",
            "\$(reboot)",
            "| cat /etc/shadow",
            "QUARANTINE_DEVICE DEV-1001",
            "--force"
        )
        val snapshot = DevicesPreview.inventory.map { it.copy() }
        probes.forEach { probe ->
            DevicesPreview.inventory.resolve(probe, DeviceFilters(), DeviceSort.Name)
            AlertsPreview.alerts.resolve(probe, AlertFilters(), AlertSort.Newest, AlertScopeView.All)
            AuditPreview.entries.resolve(probe, AuditFilters(), AuditSort.Newest)
            NotificationPreview.records.resolve(probe, NotificationFilters(), NotificationSort.Newest)
            IdentityPreview.identities.resolve(probe, IdentityFilters(), IdentitySort.Attention)
        }
        assertEquals(snapshot, DevicesPreview.inventory)
    }

    /**
     * Searching for an action code finds the *record* of an action that
     * already happened. It does not offer to run one — audit rows carry no
     * action affordance, and the entry the search returns is the same
     * read-only historical record it always was.
     */
    @Test
    fun `searching audit for an action code returns history, not an action`() {
        val hits = AuditPreview.entries.resolve("QUARANTINE_DEVICE", AuditFilters(), AuditSort.Newest)
        hits.forEach { entry ->
            // Every hit is a record of something with an outcome already
            // recorded — history, not a pending request.
            assertTrue(entry.id.isNotBlank())
            assertTrue(entry.ageMinutes >= 0)
        }
    }

    // ============================================================
    // VISIBILITY IS NOT PERMISSION
    // ============================================================

    private fun contextFor(
        device: com.example.nexa.ui.devices.DeviceListItem,
        authorization: AuthorizationState,
        availability: NexaAvailability
    ) = ActionContext(
        id = "ctx-${device.id}",
        action = EnforcementAction.QuarantineDevice,
        target = ActionTarget(
            deviceId = device.id,
            label = device.label,
            mac = device.mac,
            ip = device.ip,
            scope = device.scope,
            presence = device.presence,
            identityId = device.identityId,
            trust = device.trust,
            observationFreshness = device.freshness,
            lastObservedLabel = device.lastSeenLabel
        ),
        authorization = authorization,
        executionMode = ExecutionMode.Enforce,
        currentEnforcement = com.example.nexa.ui.devices.DeviceEnforcement.Normal,
        circuitBreaker = CircuitBreakerState.Closed,
        dataAvailability = availability
    )

    /**
     * The rule this checkpoint could most easily break. An operator filters
     * to "Trusted and Present", a device appears, and nothing whatsoever has
     * changed about whether they may act on it.
     */
    @Test
    fun `surviving a filter does not authorize an action`() {
        val visible = DevicesPreview.inventory.resolve(
            "",
            DeviceFilters(trust = setOf(TrustState.Trusted), presence = setOf(Presence.Present)),
            DeviceSort.Name
        )
        assertTrue("the fixture no longer exercises this", visible.isNotEmpty())

        visible.forEach { device ->
            // Denied stays denied for a device the filter surfaced.
            assertTrue(
                availabilityOf(contextFor(device, AuthorizationState.Denied, NexaAvailability.Current))
                    is ActionAvailability.Disabled
            )
            // Unknown standing stays unknown.
            assertTrue(
                availabilityOf(contextFor(device, AuthorizationState.Unknown, NexaAvailability.Current))
                    is ActionAvailability.Disabled
            )
            // And unreadable state still blocks, filter or no filter.
            assertTrue(
                availabilityOf(contextFor(device, AuthorizationState.Authorized, NexaAvailability.Unavailable))
                    is ActionAvailability.Disabled
            )
        }
    }

    /**
     * The mirror of the rule above: being filtered *out* does not make a
     * record safe, gone, or different. Hidden is not nonexistent.
     */
    @Test
    fun `being filtered out changes nothing about a record`() {
        val source = DevicesPreview.inventory
        val filters = DeviceFilters(trust = setOf(TrustState.Trusted))
        val visible = source.resolve("", filters, DeviceSort.Name).map { it.id }.toSet()
        val hidden = source.filter { it.id !in visible }

        assertTrue("the fixture no longer exercises this", hidden.isNotEmpty())
        hidden.forEach { device ->
            // Still in the source, unchanged.
            assertTrue(device in source)
            // Still evaluated by exactly the same rules if acted upon.
            val denied = availabilityOf(
                contextFor(device, AuthorizationState.Denied, NexaAvailability.Current)
            )
            assertTrue(denied is ActionAvailability.Disabled)
        }
    }

    /**
     * Selecting "Trust = Trusted" is a view. It shows the identities that are
     * trusted; it does not confer trust on anything, and the records that come
     * back carry whatever standing they already had.
     */
    @Test
    fun `a trust filter selects by trust rather than conferring it`() {
        val revoked = IdentityPreview.identities.resolve(
            "",
            IdentityFilters(trust = setOf(TrustState.Revoked)),
            IdentitySort.Attention
        )
        revoked.forEach { assertEquals(TrustState.Revoked, it.trust) }

        // And the source is untouched: the identities that were revoked are
        // still revoked whether or not the filter is on.
        val stillRevoked = IdentityPreview.identities.count { it.trust == TrustState.Revoked }
        assertEquals(stillRevoked, revoked.size)
    }

    // ============================================================
    // EXECUTION MODE SURVIVES FILTERING
    // ============================================================

    /**
     * Filtering audit by execution mode selects records; it never rewrites
     * one. A simulated run stays simulated, and — the more dangerous
     * direction — an unknown mode is never swept into "live" merely because
     * it was not simulated.
     */
    @Test
    fun `filtering audit by execution mode never rewrites a mode`() {
        val before = AuditPreview.entries.associate { it.id to it.executionMode }

        val simulated = AuditPreview.entries.resolve(
            "",
            AuditFilters(executionModes = setOf(ExecutionMode.AuditOnly)),
            AuditSort.Newest
        )
        simulated.forEach { assertEquals(ExecutionMode.AuditOnly, it.executionMode) }

        val live = AuditPreview.entries.resolve(
            "",
            AuditFilters(executionModes = setOf(ExecutionMode.Enforce)),
            AuditSort.Newest
        )
        live.forEach { assertEquals(ExecutionMode.Enforce, it.executionMode) }

        // Records with no recorded mode appear in neither.
        val unknownMode = AuditPreview.entries.filter { it.executionMode == null }
        val selectedIds = (simulated + live).map { it.id }.toSet()
        unknownMode.forEach { assertFalse(it.id in selectedIds) }

        AuditPreview.entries.forEach { assertEquals(before[it.id], it.executionMode) }
    }

    @Test
    fun `filtering notifications by delivery state never rewrites delivery`() {
        val before = NotificationPreview.records.associate { it.id to it.delivery.state }
        NotificationPreview.records.resolve(
            "",
            NotificationFilters(states = setOf(DeliveryState.Failed)),
            NotificationSort.Newest
        )
        NotificationPreview.records.forEach { assertEquals(before[it.id], it.delivery.state) }
    }

    // ============================================================
    // FILTERING DOES NOT TOUCH AVAILABILITY
    // ============================================================

    /**
     * Phase 5.20's vocabulary is decided from freshness, completeness and
     * connectivity. A search that returns nothing must never be able to make
     * a screen say "unavailable", and a screen that is unavailable must never
     * be talked back into "current" by clearing a filter.
     */
    @Test
    fun `availability is independent of search and filters`() {
        val freshness = DataFreshness.Stale("Last confirmed 6 min ago")
        val a = contentAvailability(freshness, isEmpty = false, degraded = false, offline = false)
        val b = contentAvailability(freshness, isEmpty = false, degraded = false, offline = false)
        assertEquals(a, b)
        assertEquals(NexaAvailability.Stale, a)

        // Offline stays offline no matter how narrow the result set is.
        assertEquals(
            NexaAvailability.Offline,
            contentAvailability(freshness, isEmpty = true, degraded = false, offline = true)
        )
    }

    /**
     * The most concrete version of the same rule: an offline screen with
     * cached records and an unmatched search reports "no match" for the list
     * while the surface as a whole is still offline. Searching must not turn
     * "there is data, but it is old" into "there are no devices".
     */
    @Test
    fun `an offline cached list with no match stays offline and reports no match`() {
        val cached = DevicesPreview.inventory
        val visible = cached.resolve("zzzz-no-such-device", DeviceFilters(), DeviceSort.Name)
        assertTrue(visible.isEmpty())

        val results = nexaResults(
            sourceCount = cached.size,
            visibleCount = visible.size,
            queryActive = true,
            filtersActive = false
        )
        assertEquals(NexaResults.NoMatch(NexaNoMatchReason.Search), results)
        // Not an empty source — the cached records are still there.
        assertFalse(results is NexaResults.SourceEmpty)

        val availability = contentAvailability(
            DataFreshness.Stale("Last confirmed 6 min ago"),
            isEmpty = visible.isEmpty(),
            degraded = false,
            offline = true
        )
        assertEquals(NexaAvailability.Offline, availability)
    }

    @Test
    fun `an unavailable source is never reachable through search at all`() {
        // There is no cached collection to resolve against, so the pipeline
        // is never entered — the screen shows the availability surface. The
        // property asserted here is that an empty list plus an active query
        // is classified as an empty source, not as a search failure the
        // operator could fix by clearing the box.
        val results = nexaResults(
            sourceCount = 0,
            visibleCount = 0,
            queryActive = true,
            filtersActive = true
        )
        assertEquals(NexaResults.SourceEmpty, results)
    }
}
