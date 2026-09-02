package com.example.nexa.ui.common

import com.example.nexa.ui.alerts.AlertsPreview
import com.example.nexa.ui.alerts.AlertsUiState
import com.example.nexa.ui.audit.AuditCoverage
import com.example.nexa.ui.audit.AuditPreview
import com.example.nexa.ui.audit.AuditUiState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.devices.DevicesUiState
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.devices.DeviceActionKind
import com.example.nexa.ui.devices.availableActions
import com.example.nexa.ui.enforcement.ActionAvailability
import com.example.nexa.ui.enforcement.ActionContext
import com.example.nexa.ui.enforcement.ActionTarget
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.enforcement.availabilityOf
import com.example.nexa.ui.identity.IdentitiesUiState
import com.example.nexa.ui.identity.IdentityPreview
import com.example.nexa.ui.notifications.NotificationCenterUiState
import com.example.nexa.ui.notifications.NotificationCoverage
import com.example.nexa.ui.notifications.NotificationPreview
import com.example.nexa.ui.overview.OverviewPreview
import com.example.nexa.ui.overview.OverviewUiState
import com.example.nexa.ui.components.warrantsNotice
import com.example.nexa.ui.overview.SecurityPosture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Statements NEXA must never make.
 *
 * Every test here is a sentence a security console could plausibly end up
 * saying by accident, and which would be false in a way an operator cannot
 * detect: "no alerts" when the service failed, "normal" when enforcement
 * could not be read, "42 devices" from an inventory nobody confirmed.
 *
 * The regression risk is real and permanent. Any future refactor that makes
 * an unavailable screen render like an empty one, or lets a stale list keep a
 * live badge, breaks one of these.
 */
class NoFalseSecurityTest {

    // ============================================================
    // UNAVAILABLE IS NOT EMPTY
    // ============================================================

    /**
     * The archetypal failure. A service that could not answer must not
     * produce a screen that reads as an answer of "nothing".
     */
    @Test
    fun `an unavailable source never becomes an empty list`() {
        assertTrue(DevicesPreview.unavailable() is DevicesUiState.Unavailable)
        assertTrue(AlertsPreview.unavailable() is AlertsUiState.Unavailable)
        assertTrue(IdentityPreview.unavailable() is IdentitiesUiState.Unavailable)
        assertTrue(AuditPreview.unavailable is AuditUiState.Unavailable)
        assertTrue(NotificationPreview.unavailable is NotificationCenterUiState.Unavailable)

        // None of them is a Content with an empty list, which is what "no
        // devices" / "no alerts" would actually be.
        assertFalse(DevicesPreview.unavailable() is DevicesUiState.Content)
        assertFalse(AlertsPreview.unavailable() is AlertsUiState.Content)
        assertFalse(AuditPreview.unavailable is AuditUiState.Content)
    }

    /** And a genuinely empty source is not dressed up as a failure. */
    @Test
    fun `a confirmed empty source is content, not unavailable`() {
        val devices = DevicesPreview.empty() as DevicesUiState.Content
        assertTrue(devices.all.isEmpty())
        assertEquals(DataFreshness.Live, devices.freshness)

        val alerts = AlertsPreview.empty() as AlertsUiState.Content
        assertTrue(alerts.all.isEmpty())
    }

    @Test
    fun `empty and unavailable are different states everywhere`() {
        assertNotEquals(DevicesPreview.empty()::class, DevicesPreview.unavailable()::class)
        assertNotEquals(AlertsPreview.empty()::class, AlertsPreview.unavailable()::class)
        assertNotEquals(AuditPreview.empty::class, AuditPreview.unavailable::class)
        assertNotEquals(NotificationPreview.empty::class, NotificationPreview.unavailable::class)
    }

    // ============================================================
    // STALE IS NOT CURRENT
    // ============================================================

    /**
     * Data that exists but is old stays visible — erasing it helps nobody —
     * but it never keeps a badge that says it is current.
     */
    @Test
    fun `stale content keeps its data and loses its live claim`() {
        val devices = DevicesPreview.stale() as DevicesUiState.Content
        assertTrue("stale inventory was emptied", devices.all.isNotEmpty())
        assertFalse("stale inventory still claims to be live", devices.freshness.isTrustworthy)
        assertTrue(devices.freshness is DataFreshness.Stale)

        val alerts = AlertsPreview.stale() as AlertsUiState.Content
        assertTrue(alerts.all.isNotEmpty())
        assertFalse(alerts.freshness.isTrustworthy)
    }

    @Test
    fun `a stale label states when it was last confirmed`() {
        val devices = DevicesPreview.stale() as DevicesUiState.Content
        val label = devices.freshness.label.lowercase()
        assertTrue("stale label was not specific: $label", label.contains("confirmed") ||
            label.contains("ago") || label.contains("updated"))
        assertFalse(label.contains("just now"))
    }

    /**
     * Every scenario that is not current produces a freshness that fails the
     * trustworthy check, so no surface built on it can describe itself as
     * live without lying.
     */
    @Test
    fun `no degraded scenario yields trustworthy freshness`() {
        DegradedScenario.Scenario.entries
            .filter { it != DegradedScenario.Scenario.Current && it != DegradedScenario.Scenario.Empty }
            .forEach { scenario ->
                assertFalse(
                    "$scenario claimed trustworthy freshness",
                    DegradedScenario.freshnessFor(scenario).isTrustworthy
                )
            }
    }

    // ============================================================
    // OFFLINE IS NOT STALE
    // ============================================================

    /**
     * Losing the connection does not blank the screen — the last confirmed
     * picture is the only thing an operator still has — but it is labelled
     * for what it is. "Old" and "I cannot ask" are different problems with
     * different responses, and a shared banner reading "stale" would hide
     * the second behind the first.
     */
    @Test
    fun `cached content while offline is labelled offline, not stale`() {
        val devices = DevicesPreview.offlineWithCache() as DevicesUiState.Content
        assertTrue("offline blanked the inventory", devices.all.isNotEmpty())
        assertTrue(devices.offline)
        assertEquals(
            NexaAvailability.Offline,
            contentAvailability(devices.freshness, devices.all.isEmpty(), devices.degraded, devices.offline)
        )

        val alerts = AlertsPreview.offlineWithCache() as AlertsUiState.Content
        assertTrue(alerts.offline)
        assertEquals(
            NexaAvailability.Offline,
            contentAvailability(alerts.freshness, alerts.all.isEmpty(), alerts.degraded, alerts.offline)
        )

        val identities = IdentityPreview.offlineWithCache() as IdentitiesUiState.Content
        assertTrue(identities.offline)
    }

    /** And content that is merely old is not accused of being disconnected. */
    @Test
    fun `stale content is not reported as offline`() {
        val devices = DevicesPreview.stale() as DevicesUiState.Content
        assertFalse(devices.offline)
        assertEquals(
            NexaAvailability.Stale,
            contentAvailability(devices.freshness, devices.all.isEmpty(), devices.degraded, devices.offline)
        )
    }

    /**
     * A connection that is gone explains every other shortcoming in the
     * picture, so it is the headline even when the data is also partial.
     */
    @Test
    fun `offline outranks incompleteness`() {
        assertEquals(
            NexaAvailability.Offline,
            contentAvailability(
                freshness = DataFreshness.Stale("earlier"),
                isEmpty = false,
                degraded = true,
                offline = true
            )
        )
    }

    // ============================================================
    // DEGRADED IS NOT COMPLETE
    // ============================================================

    /** Partial history is stated as partial, with a reason. */
    @Test
    fun `partial history is explicitly marked incomplete`() {
        val audit = AuditPreview.degraded as AuditUiState.Content
        assertFalse(audit.coverage.isComplete)
        val partial = audit.coverage as AuditCoverage.Partial
        assertTrue(partial.reason.isNotBlank())
        assertTrue(audit.visible.isNotEmpty())
    }

    @Test
    fun `partial delivery visibility is explicitly marked incomplete`() {
        val notifications = NotificationPreview.degraded as NotificationCenterUiState.Content
        assertFalse(notifications.coverage.isComplete)
        assertTrue((notifications.coverage as NotificationCoverage.Partial).reason.isNotBlank())
    }

    @Test
    fun `a degraded inventory says so`() {
        val devices = DevicesPreview.degraded() as DevicesUiState.Content
        assertTrue("degraded inventory did not announce itself", devices.degraded)
    }

    // ============================================================
    // POSTURE IS NEVER DERIVED FROM ABSENCE
    // ============================================================

    /**
     * The rule that matters most on the command centre: not being able to see
     * is never rendered as a clean bill of health.
     */
    @Test
    fun `unavailable and offline overview states carry no posture at all`() {
        assertTrue(OverviewPreview.unavailable() is OverviewUiState.Unavailable)
        assertTrue(OverviewPreview.offline() is OverviewUiState.Offline)
        // Neither is a Content, so neither can carry a SecurityPosture.Secure.
        assertFalse(OverviewPreview.unavailable() is OverviewUiState.Content)
        assertFalse(OverviewPreview.offline() is OverviewUiState.Content)
    }

    /** Unknown freshness yields unknown posture, never a reassuring one. */
    @Test
    fun `unknown freshness never produces a secure posture`() {
        val stale = OverviewPreview.stale() as OverviewUiState.Content
        assertNotEquals(SecurityPosture.Secure, stale.data.posture)
    }

    @Test
    fun `no degraded scenario maps to a secure posture`() {
        // Current and Empty are the only scenarios that may present a
        // confident posture, and even then it comes from content.
        DegradedScenario.Scenario.entries
            .filter { it != DegradedScenario.Scenario.Current && it != DegradedScenario.Scenario.Empty }
            .forEach { scenario ->
                assertNotEquals(
                    "$scenario",
                    NexaAvailability.Current,
                    DegradedScenario.availabilityFor(scenario)
                )
            }
    }

    // ============================================================
    // ACTIONS ON INSUFFICIENT STATE
    // ============================================================

    /**
     * A context that is sound in every respect except the one under test, so
     * a refusal can only have come from the availability rule.
     */
    private fun contextWith(
        action: EnforcementAction,
        availability: NexaAvailability,
        freshness: DataFreshness = DataFreshness.Live
    ) = ActionContext(
        id = "ctx-1",
        action = action,
        target = ActionTarget(
            deviceId = "DEV-1001",
            label = "Corp Laptop",
            mac = "00:1A:2B:3C:4D:5E",
            ip = "10.0.0.1",
            scope = "VLAN_SECURE",
            presence = Presence.Present,
            identityId = "TID-88F1",
            trust = TrustState.Trusted,
            observationFreshness = freshness,
            lastObservedLabel = "just now"
        ),
        authorization = AuthorizationState.Authorized,
        executionMode = ExecutionMode.Enforce,
        // Release only applies to a quarantined target; offering it against a
        // normal one would be structurally hidden before any availability
        // rule was reached, and would test nothing.
        currentEnforcement = if (action == EnforcementAction.ReleaseQuarantine) {
            DeviceEnforcement.Quarantined
        } else {
            DeviceEnforcement.Normal
        },
        circuitBreaker = CircuitBreakerState.Closed,
        dataAvailability = availability
    )

    /**
     * The security-critical rule. A confirmation screen assembled from a
     * service that could not answer is a screen full of plausible blanks, and
     * no operator should be asked to commit an enforcement change against it.
     */
    @Test
    fun `no action is offered when the state behind it is unreadable`() {
        val unreadable = listOf(
            NexaAvailability.Offline,
            NexaAvailability.Unavailable,
            NexaAvailability.Unknown,
            NexaAvailability.Error,
            NexaAvailability.Loading,
            NexaAvailability.Empty
        )
        EnforcementAction.entries.forEach { action ->
            unreadable.forEach { availability ->
                val result = availabilityOf(contextWith(action, availability))
                assertTrue(
                    "$action was offered on $availability state",
                    result is ActionAvailability.Disabled
                )
            }
        }
    }

    /**
     * Data that exists but is old or partial blocks anything that changes
     * enforcement. Reverification stays available: it asks the identity to
     * prove it is present rather than acting on what NEXA last saw.
     */
    @Test
    fun `stale and partial state block enforcement changes but not reverification`() {
        listOf(NexaAvailability.Stale, NexaAvailability.Degraded).forEach { availability ->
            listOf(
                EnforcementAction.QuarantineDevice,
                EnforcementAction.ReleaseQuarantine
            ).forEach { action ->
                assertTrue(
                    "$action was offered on $availability state",
                    availabilityOf(contextWith(action, availability))
                        is ActionAvailability.Disabled
                )
            }

            val reverify = contextWith(EnforcementAction.RequireReverification, availability)
            assertEquals(
                "reverification was blocked on $availability",
                ActionAvailability.Available,
                availabilityOf(reverify)
            )
        }
    }

    @Test
    fun `a current context still offers its action`() {
        assertEquals(
            ActionAvailability.Available,
            availabilityOf(contextWith(EnforcementAction.QuarantineDevice, NexaAvailability.Current))
        )
    }

    /**
     * Refusals name what is missing. "Unavailable" on its own tells an
     * operator nothing and invites them to look for a way around it.
     */
    @Test
    fun `every refusal explains what is not known`() {
        listOf(
            NexaAvailability.Offline,
            NexaAvailability.Unavailable,
            NexaAvailability.Unknown,
            NexaAvailability.Error,
            NexaAvailability.Stale,
            NexaAvailability.Degraded
        ).forEach { availability ->
            val result = availabilityOf(
                contextWith(EnforcementAction.QuarantineDevice, availability)
            )
            val reason = (result as ActionAvailability.Disabled).reason
            assertTrue("$availability gave a bare reason", reason.length > 30)
            // And never implies the target is fine.
            listOf("secure", "safe", "healthy", "no risk").forEach { claim ->
                assertFalse(
                    "$availability reason claims \"$claim\": $reason",
                    reason.lowercase().contains(claim)
                )
            }
        }
    }

    /**
     * The refusal is visible before the operator commits to the flow.
     *
     * Walking someone up to a full-strength destructive button and turning
     * them away at the confirmation screen teaches them that the refusal is
     * an obstacle rather than a fact about the data.
     */
    @Test
    fun `a device with an unconfirmable observation offers no enforcement change`() {
        val device = DevicesPreview.inventory.first { it.freshness is DataFreshness.Unknown }
        val actions = availableActions(device)
        actions.filter { it.kind != DeviceActionKind.RequireReverification }.forEach {
            assertFalse("${it.kind} was offered on unknown state", it.enabled)
            assertTrue("${it.kind} gave no reason", !it.disabledReason.isNullOrBlank())
        }
    }

    /** A stale observation blocks the same actions, for the same reason. */
    @Test
    fun `a device with a stale observation offers no enforcement change`() {
        val device = DevicesPreview.inventory.first { it.freshness is DataFreshness.Stale }
        availableActions(device)
            .filter { it.kind != DeviceActionKind.RequireReverification }
            .forEach { assertFalse("${it.kind} was offered on stale state", it.enabled) }
    }

    /** And a current one still offers them. */
    @Test
    fun `a device with a current observation still offers its actions`() {
        val device = DevicesPreview.inventory.first {
            it.freshness is DataFreshness.Live &&
                it.enforcement == DeviceEnforcement.Normal
        }
        assertTrue(availableActions(device).any { it.kind == DeviceActionKind.Quarantine && it.enabled })
    }

    // ============================================================
    // ONE SUBSYSTEM DOES NOT REWRITE ANOTHER
    // ============================================================

    /**
     * A failure in one place must not spread. Identity being unreadable says
     * nothing about the device inventory, and vice versa.
     */
    @Test
    fun `an unavailable subsystem leaves the others intact`() {
        val devices = DevicesPreview.scenario as DevicesUiState.Content
        val alerts = AlertsPreview.scenario as AlertsUiState.Content

        // Identity unavailable.
        assertTrue(IdentityPreview.unavailable() is IdentitiesUiState.Unavailable)
        // Devices and alerts are unchanged by that fact.
        assertTrue(devices.all.isNotEmpty())
        assertTrue(alerts.all.isNotEmpty())
        assertTrue(devices.freshness.isTrustworthy)
    }

    // ============================================================
    // THE WARNING IS ON SCREEN, NOT JUST IN THE MODEL
    // ============================================================

    /**
     * A state the app cannot stand behind must produce a visible banner.
     * Deriving the right availability and then rendering nothing would leave
     * the operator with exactly the screen this checkpoint exists to prevent.
     */
    @Test
    fun `every state that cannot be stood behind shows a banner`() {
        listOf(
            NexaAvailability.Stale,
            NexaAvailability.Degraded,
            NexaAvailability.Unknown,
            NexaAvailability.Offline,
            NexaAvailability.Unavailable,
            NexaAvailability.Error
        ).forEach { assertTrue("$it showed no banner", it.warrantsNotice) }
    }

    /**
     * And the states that need no banner do not get one. A warning that is
     * always on screen is a warning nobody reads, which would blunt the ones
     * above. Empty is deliberately silent here: it is a complete, current
     * answer, and it states itself in the list's own copy.
     */
    @Test
    fun `current, loading and empty raise no banner`() {
        listOf(
            NexaAvailability.Current,
            NexaAvailability.Loading,
            NexaAvailability.Empty
        ).forEach { assertFalse("$it raised a banner", it.warrantsNotice) }
    }

    /**
     * Connection state and domain state are separate axes: a disconnected
     * feed with a current snapshot is a normal, safe combination.
     */
    @Test
    fun `a disconnected feed does not make domain data stale by itself`() {
        val devices = DevicesPreview.scenario as DevicesUiState.Content
        // Nothing about the realtime connection appears in the domain state.
        assertTrue(devices.freshness.isTrustworthy)
        assertEquals(DataFreshness.Live, devices.freshness)
    }
}
