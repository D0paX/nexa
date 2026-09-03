package com.example.nexa.ui.overview

import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.ActivityKind
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.isTrustworthy
import com.example.nexa.ui.common.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the rules that decide what the command center tells an operator.
 *
 * These cover the security-relevant judgements — what posture is claimed,
 * what is escalated for action — because getting them wrong misleads someone
 * about whether a network is protected.
 */
class OverviewStateTest {

    private fun enforcement(
        enabled: Boolean = true,
        breaker: CircuitBreakerState = CircuitBreakerState.Closed,
        mode: ExecutionMode = ExecutionMode.Enforce,
        quarantined: Int = 0,
        pending: Int = 0,
        failed: Int = 0,
        reconciliation: Int = 0
    ) = EnforcementState(
        enabled = enabled,
        circuitBreaker = breaker,
        executionMode = mode,
        quarantinedDevices = quarantined,
        pendingActions = pending,
        failedActions = failed,
        reconciliationIssues = reconciliation
    )

    private fun alerts(
        total: Int = 0,
        critical: Int = 0,
        warning: Int = 0,
        unacknowledged: Int = 0
    ) = AlertSummary(total, critical, warning, unacknowledged)

    // ------------------------------------------------------------
    // Posture
    // ------------------------------------------------------------

    @Test
    fun `healthy system with nothing outstanding is Secure`() {
        val posture = derivePosture(enforcement(), alerts(), DataFreshness.Live)
        assertEquals(SecurityPosture.Secure, posture)
    }

    @Test
    fun `quarantined targets report Enforcing rather than Secure`() {
        val posture = derivePosture(enforcement(quarantined = 3), alerts(), DataFreshness.Live)
        assertEquals(SecurityPosture.Enforcing, posture)
    }

    @Test
    fun `critical alert outranks active enforcement`() {
        val posture = derivePosture(
            enforcement(quarantined = 3),
            alerts(total = 1, critical = 1, unacknowledged = 1),
            DataFreshness.Live
        )
        assertEquals(SecurityPosture.Critical, posture)
    }

    @Test
    fun `failed enforcement action is Critical even with no alerts`() {
        val posture = derivePosture(enforcement(failed = 1), alerts(), DataFreshness.Live)
        assertEquals(SecurityPosture.Critical, posture)
    }

    /**
     * The important one: an open breaker means nothing will be enforced, so
     * claiming ENFORCING would tell an operator the network is protected
     * when it is not.
     */
    @Test
    fun `open circuit breaker never reports Enforcing`() {
        val posture = derivePosture(
            enforcement(breaker = CircuitBreakerState.Open, quarantined = 5),
            alerts(),
            DataFreshness.Live
        )
        assertEquals(SecurityPosture.Paused, posture)
    }

    @Test
    fun `reconciliation issues degrade posture`() {
        val posture = derivePosture(enforcement(reconciliation = 2), alerts(), DataFreshness.Live)
        assertEquals(SecurityPosture.Degraded, posture)
    }

    @Test
    fun `disabled enforcement is Degraded not Secure`() {
        val posture = derivePosture(enforcement(enabled = false), alerts(), DataFreshness.Live)
        assertEquals(SecurityPosture.Degraded, posture)
    }

    /** With no confirmed view, NEXA claims nothing — least of all health. */
    @Test
    fun `unknown freshness yields Unknown posture regardless of counters`() {
        val posture = derivePosture(
            enforcement(quarantined = 3),
            alerts(),
            DataFreshness.Unknown
        )
        assertEquals(SecurityPosture.Unknown, posture)
    }

    // ------------------------------------------------------------
    // Attention
    // ------------------------------------------------------------

    @Test
    fun `healthy system raises nothing for attention`() {
        val items = buildAttentionItems(enforcement(), alerts())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `steady-state quarantine is not an action item`() {
        val items = buildAttentionItems(enforcement(quarantined = 4), alerts())
        assertTrue(items.isEmpty())
    }

    @Test
    fun `failed enforcement sorts above every other attention item`() {
        val items = buildAttentionItems(
            enforcement(breaker = CircuitBreakerState.Open, failed = 1, reconciliation = 1, pending = 1),
            alerts(total = 2, warning = 2, unacknowledged = 2)
        )
        assertEquals("enforcement-failed", items.first().id)
        assertEquals(NexaStatus.Critical, items.first().status)
    }

    @Test
    fun `open breaker surfaces a paused attention item`() {
        val items = buildAttentionItems(enforcement(breaker = CircuitBreakerState.Open), alerts())
        assertTrue(items.any { it.id == "circuit-breaker" && it.status == NexaStatus.Paused })
    }

    @Test
    fun `critical alerts become tappable attention items`() {
        val critical = listOf(
            ActivityEntry("ALRT-1", ActivityKind.AlertRaised, "Port scan", "00:11", "2m ago", NexaStatus.Critical)
        )
        val items = buildAttentionItems(enforcement(), alerts(total = 1, critical = 1), critical)
        val alertItem = items.single { it.id == "alert-ALRT-1" }
        assertEquals(AttentionTarget.Alert("ALRT-1"), alertItem.target)
    }

    @Test
    fun `unacknowledged warnings are raised only when nothing critical is present`() {
        val withCritical = buildAttentionItems(
            enforcement(),
            alerts(total = 3, critical = 1, unacknowledged = 3)
        )
        assertFalse(withCritical.any { it.id == "unacknowledged" })

        val withoutCritical = buildAttentionItems(
            enforcement(),
            alerts(total = 3, warning = 3, unacknowledged = 3)
        )
        assertTrue(withoutCritical.any { it.id == "unacknowledged" })
    }

    @Test
    fun `attention items are returned in priority order`() {
        val items = buildAttentionItems(
            enforcement(breaker = CircuitBreakerState.Open, failed = 1, reconciliation = 1, pending = 2),
            alerts(total = 1, warning = 1, unacknowledged = 1)
        )
        assertEquals(items.map { it.priority }.sorted(), items.map { it.priority })
    }

    // ------------------------------------------------------------
    // Wording
    // ------------------------------------------------------------

    /** ENFORCING states what NEXA is doing — not that every target is safe. */
    @Test
    fun `enforcing detail does not claim unaffected targets are safe`() {
        val detail = postureDetail(SecurityPosture.Enforcing, enforcement(quarantined = 2))
        assertTrue(detail.contains("2 device"))
        assertFalse(detail.contains("safe"))
        assertFalse(detail.contains("secure"))
    }

    @Test
    fun `unknown posture detail never implies health`() {
        val detail = postureDetail(SecurityPosture.Unknown, enforcement())
        assertTrue(detail.contains("cannot confirm"))
    }

    @Test
    fun `paused detail states that existing rules remain`() {
        val detail = postureDetail(SecurityPosture.Paused, enforcement(breaker = CircuitBreakerState.Open))
        assertTrue(detail.contains("halted"))
        assertTrue(detail.contains("remain in place"))
    }

    // ------------------------------------------------------------
    // Freshness presentation
    // ------------------------------------------------------------

    @Test
    fun `only live data is treated as trustworthy`() {
        assertTrue(DataFreshness.Live.isTrustworthy)
        assertFalse(DataFreshness.Stale("Last confirmed 4 min ago").isTrustworthy)
        assertFalse(DataFreshness.Unknown.isTrustworthy)
    }

    @Test
    fun `stale freshness surfaces its own age label`() {
        assertEquals("Last confirmed 4 min ago", DataFreshness.Stale("Last confirmed 4 min ago").label)
    }

    // ------------------------------------------------------------
    // Preview scenarios resolve to the states they claim
    // ------------------------------------------------------------

    @Test
    fun `preview scenarios produce their intended posture`() {
        assertEquals(SecurityPosture.Enforcing, (OverviewPreview.enforcing() as OverviewUiState.Content).data.posture)
        assertEquals(SecurityPosture.Secure, (OverviewPreview.secure() as OverviewUiState.Content).data.posture)
        assertEquals(SecurityPosture.Paused, (OverviewPreview.paused() as OverviewUiState.Content).data.posture)
        assertEquals(SecurityPosture.Degraded, (OverviewPreview.degraded() as OverviewUiState.Content).data.posture)
        assertEquals(SecurityPosture.Critical, (OverviewPreview.critical() as OverviewUiState.Content).data.posture)
    }

    /** A healthy, empty system is Content-with-nothing — never Unavailable. */
    @Test
    fun `secure scenario is empty content rather than an unavailable state`() {
        val state = OverviewPreview.secure()
        assertTrue(state is OverviewUiState.Content)
        val data = (state as OverviewUiState.Content).data
        assertTrue(data.alerts.total == 0)
        assertTrue(data.activity.isEmpty())
        assertTrue(data.attention.isEmpty())
    }

    @Test
    fun `offline and unavailable are distinct states`() {
        assertEquals(OverviewUiState.Offline, OverviewPreview.offline())
        assertEquals(OverviewUiState.Unavailable, OverviewPreview.unavailable())
        assertTrue(OverviewPreview.offline() != OverviewPreview.unavailable())
    }
}
