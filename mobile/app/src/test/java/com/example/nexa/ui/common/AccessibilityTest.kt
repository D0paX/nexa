package com.example.nexa.ui.common

import com.example.nexa.theme.NexaStatus
import com.example.nexa.theme.style
import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.alerts.label as alertLabel
import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.enforcement.AuthorizationState
import com.example.nexa.ui.enforcement.EnforcementAction
import com.example.nexa.ui.enforcement.ExecutionState
import com.example.nexa.ui.enforcement.confirmLabel
import com.example.nexa.ui.enforcement.label
import com.example.nexa.ui.enforcement.resultHeadline
import com.example.nexa.ui.identity.IdentityFreshnessFacet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What NEXA says out loud.
 *
 * The screen-reader user and the sighted user must receive the same truth —
 * not a summary of it, and never a more confident one. So these tests come in
 * two halves. The first checks that the spoken forms read as sentences rather
 * than as punctuation. The second, and the more important one, checks that
 * every distinction the product spends its visual language protecting also
 * survives being read aloud.
 *
 *   TRUSTED     is not  AUTHORIZED
 *   SIMULATION  is not  LIVE
 *   STALE       is not  CURRENT
 *   OFFLINE     is not  EMPTY
 *   UNKNOWN     is not  CURRENT
 *   RECONCILING is not  SUCCEEDED
 */
class AccessibilityTest {

    // ============================================================
    // SPOKEN FORMS ARE SENTENCES
    // ============================================================

    @Test
    fun `the filter control says how many filters are active`() {
        assertEquals("Filters", filterCountSpoken(0))
        assertEquals("Filters, 1 active", filterCountSpoken(1))
        assertEquals("Filters, 3 active", filterCountSpoken(3))
    }

    /** The visible label keeps its shorthand; the spoken one does not. */
    @Test
    fun `the spoken filter label never reads a separator`() {
        (0..5).forEach { count ->
            val spoken = filterCountSpoken(count)
            assertFalse("spoken label contains a middle dot: $spoken", spoken.contains("·"))
        }
        assertTrue(filterButtonLabel(3).contains("·"))
    }

    @Test
    fun `summary lines are spoken with commas`() {
        assertEquals(
            "8 devices, 7 need attention",
            spokenSummaryLine("8 devices · 7 need attention")
        )
        assertEquals(
            "5 open, 2 critical, 4 unacknowledged",
            spokenSummaryLine("5 open · 2 critical · 4 unacknowledged")
        )
        assertFalse(spokenSummaryLine("2 of 8 devices · 2 need attention").contains("·"))
    }

    /** A line with nothing to join is left exactly as it is. */
    @Test
    fun `a summary line without separators is unchanged`() {
        assertEquals("0 devices", spokenSummaryLine("0 devices"))
    }

    /**
     * The spoken count is a transformation of the visible one, never a second
     * calculation. Two implementations would be two chances to disagree, and
     * the sighted and spoken answers have to match.
     */
    @Test
    fun `spoken and visible counts agree on the numbers`() {
        listOf(0 to 12, 1 to 1, 4 to 12, 8 to 8).forEach { (visible, total) ->
            val printed = resultCountLabel(visible, total, "device")
            val spoken = resultCountSpoken(visible, total, "device")
            assertTrue("$spoken lost the visible count", spoken.contains(visible.toString()))
            if (visible != total) {
                assertTrue("$spoken lost the total", spoken.contains(total.toString()))
                assertTrue(printed.contains(total.toString()))
            }
        }
    }

    @Test
    fun `a narrowed count says it is showing a subset`() {
        assertEquals("Showing 4 of 12 devices", resultCountSpoken(4, 12, "device"))
        assertEquals("8 devices", resultCountSpoken(8, 8, "device"))
        assertEquals("1 device", resultCountSpoken(1, 1, "device"))
    }

    // ============================================================
    // STATE IS NEVER CARRIED BY COLOUR
    // ============================================================

    /**
     * Every status has a word. A screen reader receives the label, so a state
     * communicated only by a red tint would be a state a blind operator
     * cannot perceive at all.
     */
    @Test
    fun `every status has a spoken label`() {
        NexaStatus.entries.forEach { status ->
            val label = status.style.label
            assertTrue("$status has no label", label.isNotBlank())
        }
    }

    /**
     * And no label is a colour. "Red" tells an operator what the pixel looks
     * like, which is precisely the information they do not have.
     */
    @Test
    fun `no status label names a colour`() {
        val colours = listOf("red", "green", "amber", "yellow", "orange", "blue", "grey", "gray")
        NexaStatus.entries.forEach { status ->
            val label = status.style.label.lowercase()
            colours.forEach { colour ->
                assertFalse("$status is announced as \"$colour\"", label.contains(colour))
            }
        }
    }

    /** The same holds for every domain vocabulary an operator navigates by. */
    @Test
    fun `every domain state has a spoken label`() {
        NexaAvailability.entries.forEach { assertTrue("$it", it.label.isNotBlank()) }
        TrustState.entries.forEach { assertTrue("$it", it.label.isNotBlank()) }
        DeliveryState.entries.forEach { assertTrue("$it", it.label.isNotBlank()) }
        ExecutionMode.entries.forEach { assertTrue("$it", it.label.isNotBlank()) }
        ExecutionState.entries.forEach { assertTrue("$it", it.label.isNotBlank()) }
        AuthorizationState.entries.forEach { assertTrue("$it", it.label.isNotBlank()) }
        AlertSeverity.entries.forEach { assertTrue("$it", it.alertLabel.isNotBlank()) }
        AlertLifecycle.entries.forEach { assertTrue("$it", it.alertLabel.isNotBlank()) }
        IdentityFreshnessFacet.entries.forEach { assertTrue("$it", it.label.isNotBlank()) }
    }

    /**
     * Distinct states need distinct words. Two states sharing a label are
     * indistinguishable to someone who only hears them, whatever the screen
     * does with colour and shape.
     */
    @Test
    fun `distinct states are distinguishable by their labels alone`() {
        fun <T> assertUnique(name: String, values: List<T>, label: (T) -> String) {
            val labels = values.map { label(it).lowercase() }
            assertEquals("$name has colliding labels: $labels", labels.size, labels.toSet().size)
        }
        assertUnique("availability", NexaAvailability.entries.toList()) { it.label }
        assertUnique("trust", TrustState.entries.toList()) { it.label }
        assertUnique("delivery", DeliveryState.entries.toList()) { it.label }
        assertUnique("execution mode", ExecutionMode.entries.toList()) { it.label }
        assertUnique("execution state", ExecutionState.entries.toList()) { it.label }
        assertUnique("authorization", AuthorizationState.entries.toList()) { it.label }
        assertUnique("severity", AlertSeverity.entries.toList()) { it.alertLabel }
        assertUnique("lifecycle", AlertLifecycle.entries.toList()) { it.alertLabel }
    }

    // ============================================================
    // THE DISTINCTIONS SURVIVE BEING SPOKEN
    // ============================================================

    /** Trust is a standing. Authorization is a decision. Different words. */
    @Test
    fun `trusted is not spoken as authorized`() {
        val trust = TrustState.Trusted.label.lowercase()
        assertFalse(trust.contains("authoriz"))
        assertNotEquals(
            AuthorizationState.Authorized.label.lowercase(),
            trust
        )
    }

    @Test
    fun `verified trust is not spoken as safe or secure`() {
        TrustState.entries.forEach { state ->
            val label = state.label.lowercase()
            assertFalse("$state is announced as safe", label.contains("safe"))
            assertFalse("$state is announced as secure", label.contains("secure"))
        }
    }

    /** A simulation announces itself as one, in every mode-aware string. */
    @Test
    fun `simulation is always spoken as simulation`() {
        assertTrue(ExecutionMode.AuditOnly.label.lowercase().contains("simulation"))
        EnforcementAction.entries.forEach { action ->
            val label = confirmLabel(action, ExecutionMode.AuditOnly)
            assertTrue("$action does not announce simulation: $label", label.startsWith("SIMULATE"))
        }
        listOf(
            ExecutionState.Succeeded,
            ExecutionState.Failed,
            ExecutionState.RolledBack,
            ExecutionState.RollbackFailed,
            ExecutionState.Unknown
        ).forEach { state ->
            val headline = resultHeadline(state, ExecutionMode.AuditOnly).lowercase()
            assertTrue("$state does not announce simulation: $headline", headline.contains("simulation"))
        }
    }

    /** And a live run is never announced as one. */
    @Test
    fun `a live result is never spoken as a simulation`() {
        ExecutionState.entries.forEach { state ->
            val headline = resultHeadline(state, ExecutionMode.Enforce).lowercase()
            assertFalse("$state reads as simulated: $headline", headline.contains("simulation"))
        }
    }

    /** An unknown mode announces uncertainty rather than borrowing either. */
    @Test
    fun `an unknown execution mode is never spoken as live`() {
        val label = ExecutionMode.Unknown.label.lowercase()
        assertTrue(label.contains("unknown"))
        assertFalse(label.contains("live"))
        EnforcementAction.entries.forEach { action ->
            val confirm = confirmLabel(action, ExecutionMode.Unknown)
            assertFalse(confirm.contains("CONFIRM"))
            assertTrue(confirm.contains("UNKNOWN"))
        }
    }

    /**
     * The highest-consequence one. Reconciling is the state before an answer
     * exists, and a screen reader that rounds it up to "succeeded" is telling
     * an operator that a firewall change landed when nothing has said so.
     */
    @Test
    fun `reconciling is never spoken as succeeded`() {
        assertNotEquals(
            ExecutionState.Reconciling.label.lowercase(),
            ExecutionState.Succeeded.label.lowercase()
        )
        val reconciling = ExecutionState.Reconciling.label.lowercase()
        assertFalse(reconciling.contains("succeed"))
        assertFalse(reconciling.contains("complete"))
        assertFalse(reconciling.contains("done"))
    }

    @Test
    fun `an unknown outcome is never spoken as failed or succeeded`() {
        val unknown = ExecutionState.Unknown.label.lowercase()
        assertFalse(unknown.contains("fail"))
        assertFalse(unknown.contains("succeed"))
        assertTrue(unknown.contains("unknown"))
    }

    /** Rollback failure keeps its own, worse word. */
    @Test
    fun `rollback failure is not spoken as a rollback`() {
        assertNotEquals(
            ExecutionState.RolledBack.label.lowercase(),
            ExecutionState.RollbackFailed.label.lowercase()
        )
        assertTrue(ExecutionState.RollbackFailed.label.lowercase().contains("fail"))
    }

    /** Offline is a visibility problem; empty is an answer. Different words. */
    @Test
    fun `offline is never spoken as empty`() {
        assertNotEquals(
            NexaAvailability.Offline.label.lowercase(),
            NexaAvailability.Empty.label.lowercase()
        )
        assertFalse(NexaAvailability.Offline.label.lowercase().contains("empty"))
    }

    @Test
    fun `stale is never spoken as current`() {
        assertNotEquals(
            NexaAvailability.Stale.label.lowercase(),
            NexaAvailability.Current.label.lowercase()
        )
        assertFalse(NexaAvailability.Stale.label.lowercase().contains("current"))
    }

    @Test
    fun `unknown availability is never spoken as current`() {
        assertNotEquals(
            NexaAvailability.Unknown.label.lowercase(),
            NexaAvailability.Current.label.lowercase()
        )
    }

    @Test
    fun `degraded is spoken as neither current nor error`() {
        val degraded = NexaAvailability.Degraded.label.lowercase()
        assertFalse(degraded.contains("current"))
        assertFalse(degraded.contains("error"))
        assertNotEquals(NexaAvailability.Degraded.label, NexaAvailability.Error.label)
    }

    /**
     * Presence is an observation and enforcement is a state the system chose.
     * Neither is a verdict about safety.
     */
    @Test
    fun `presence and enforcement are never spoken as safety`() {
        val words = listOf("safe", "secure", "protected", "fine")
        Presence.entries.forEach { presence ->
            words.forEach {
                assertFalse("$presence reads as $it", presence.name.lowercase().contains(it))
            }
        }
        DeviceEnforcement.entries.forEach { enforcement ->
            words.forEach {
                assertFalse("$enforcement reads as $it", enforcement.name.lowercase().contains(it))
            }
        }
    }

    // ============================================================
    // SECRETS STAY UNSPOKEN
    // ============================================================

    /**
     * Accessibility must not become a disclosure path. What the visual UI
     * withholds, the spoken UI withholds — a label is content, and content
     * that is secret does not become safe by being read rather than shown.
     */
    @Test
    fun `no state label carries anything secret`() {
        val forbidden = listOf("key", "secret", "token", "credential", "password", "private")
        val allLabels = NexaStatus.entries.map { it.style.label } +
            NexaAvailability.entries.map { it.label } +
            TrustState.entries.map { it.label } +
            DeliveryState.entries.map { it.label } +
            ExecutionState.entries.map { it.label } +
            ExecutionMode.entries.map { it.label } +
            AuthorizationState.entries.map { it.label }

        allLabels.forEach { label ->
            forbidden.forEach { marker ->
                assertFalse(
                    "a spoken label contains \"$marker\": $label",
                    label.lowercase().contains(marker)
                )
            }
        }
    }

    // ============================================================
    // REVERIFICATION KEEPS ITS OWN VOICE
    // ============================================================

    @Test
    fun `the reverification control names reverification`() {
        val simulated = confirmLabel(EnforcementAction.RequireReverification, ExecutionMode.AuditOnly)
        val live = confirmLabel(EnforcementAction.RequireReverification, ExecutionMode.Enforce)

        assertTrue(simulated.contains("REVERIFICATION"))
        assertTrue(live.contains("REVERIFICATION"))
        listOf("QUARANTINE", "RELEASE", "ISOLATE").forEach {
            assertFalse("reverification is announced as $it", simulated.contains(it))
            assertFalse("reverification is announced as $it", live.contains(it))
        }
    }

    /** And every action names itself rather than saying only "confirm". */
    @Test
    fun `no confirmation control is announced as a bare confirm`() {
        EnforcementAction.entries.forEach { action ->
            ExecutionMode.entries.forEach { mode ->
                val label = confirmLabel(action, mode)
                assertNotEquals("CONFIRM", label)
                assertTrue("$action/$mode says too little: $label", label.length > "CONFIRM".length)
            }
        }
    }
}
