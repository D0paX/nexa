package com.example.nexa.ui.audit

import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.ExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guarantees history has to keep.
 *
 * A record outlives the screen that produced it and is what an operator or an
 * auditor reads months later, with none of the context they had at the time.
 * That makes the distinctions harder to protect here than anywhere else: there
 * is no banner still on screen, no flow still in progress, nothing but the
 * words in the row. These tests hold those words to the same standard the live
 * flow is held to.
 */
class AuditSecurityTest {

    private val entries = AuditPreview.entries

    /**
     * Words that assert a real firewall change. A simulated record may not use
     * one unless the same sentence says no mutation occurred — the same guard
     * the AUDIT_ONLY action flow carries, applied to the past tense.
     */
    private val mutationClaims = listOf(
        "quarantined",
        "released",
        "blocked",
        "enforced",
        "reconciled",
        "is applying",
        "being applied",
        "was applied",
        "mutated"
    )

    private val disclaimers = listOf(
        "no firewall mutation",
        "not applied",
        "unchanged"
    )

    // ============================================================
    // COHERENCE
    // ============================================================

    /**
     * The structural half of the guarantee. A reconciliation compares intent
     * against actual system state and a binding is a rule in the kernel;
     * neither can be produced by a run that mutated nothing.
     */
    @Test
    fun `no simulated record claims a firewall mutation by its type`() {
        entries.forEach { entry ->
            assertTrue(
                "${entry.id} is a simulated ${entry.type}",
                isCoherent(entry)
            )
        }
    }

    @Test
    fun `an incoherent record is rejected rather than dressed up`() {
        val incoherent = entries.first { it.id == "EVT-4433" }
            .copy(type = AuditEventType.ActionReconciled)
        assertTrue(incoherent.isSimulated)
        assertFalse(isCoherent(incoherent))
    }

    @Test
    fun `reconciliations and bindings in the record are live`() {
        entries.filter { it.type.assertsFirewallMutation }.forEach { entry ->
            assertEquals(entry.id, ExecutionMode.Enforce, entry.executionMode)
        }
    }

    // ============================================================
    // SIMULATION HISTORY
    // ============================================================

    @Test
    fun `a simulated record still reads as simulated`() {
        val simulated = entries.filter { it.isSimulated }
        assertTrue(simulated.isNotEmpty())
        simulated.forEach { entry ->
            val headline = auditHeadline(entry).lowercase()
            assertTrue(
                "${entry.id}: $headline",
                headline.contains("simulation") || headline.contains("simulated")
            )
        }
    }

    @Test
    fun `every simulated explanation states that nothing was mutated`() {
        entries.filter { it.isSimulated }.forEach { entry ->
            val explanation = auditExplanation(entry).lowercase()
            assertTrue(
                "${entry.id}: $explanation",
                explanation.contains("no firewall mutation")
            )
        }
    }

    /**
     * The copy guard. Scans the two strings a row and a detail actually show —
     * headline and explanation — for language that would describe a change to
     * real firewall state.
     */
    @Test
    fun `simulated wording never claims the firewall changed`() {
        entries.filter { it.isSimulated }.forEach { entry ->
            listOf(auditHeadline(entry), auditExplanation(entry)).forEach { text ->
                val lower = text.lowercase()
                mutationClaims.forEach { claim ->
                    if (lower.contains(claim)) {
                        assertTrue(
                            "${entry.id} says \"$claim\" without a disclaimer: $text",
                            disclaimers.any { lower.contains(it) }
                        )
                    }
                }
            }
        }
    }

    /** The two must not be able to render the same sentence. */
    @Test
    fun `the same event reads differently live and simulated`() {
        val simulated = entries.first { it.id == "EVT-4433" }
        val live = simulated.copy(executionMode = ExecutionMode.Enforce)

        assertNotEquals(auditHeadline(simulated), auditHeadline(live))
        assertNotEquals(auditExplanation(simulated), auditExplanation(live))
        assertTrue(auditHeadline(live).contains("completed"))
        assertFalse(auditHeadline(live).lowercase().contains("simulation"))
    }

    @Test
    fun `a simulated release leaves the target where it was`() {
        val release = entries.first { it.id == "EVT-4433" }
        assertTrue(release.isSimulated)
        assertEquals("QUARANTINED", release.previousState)
        assertEquals("QUARANTINED", release.resultingState)
    }

    /**
     * A failed simulation is not a network incident. Painting it the same red
     * as a real enforcement failure would teach an operator to read the two as
     * equivalent, which is the habit this whole distinction exists to prevent.
     */
    @Test
    fun `simulated history is never shown in live danger tone`() {
        entries.filter { it.isSimulated }.forEach { entry ->
            val status = auditStatus(entry)
            assertNotEquals(entry.id, NexaStatus.Danger, status)
            assertNotEquals(entry.id, NexaStatus.Critical, status)
            assertEquals(entry.id, GlassVariant.Standard, auditSurfaceFor(entry))
        }
    }

    @Test
    fun `a failed simulation is still reported as a failure`() {
        val failed = entries.first { it.id == "EVT-4441" }
        assertTrue(failed.isSimulated)
        assertEquals(AuditOutcome.Failed, failed.outcome)
        assertTrue(auditHeadline(failed).contains("failed"))
    }

    // ============================================================
    // LIVE HISTORY
    // ============================================================

    @Test
    fun `live records keep their live mode`() {
        val live = entries.filter { it.isLiveEnforcement }
        assertTrue(live.isNotEmpty())
        live.forEach { entry ->
            assertEquals(entry.id, ExecutionMode.Enforce, entry.executionMode)
            assertEquals(entry.id, "LIVE", auditModeBadge(entry)?.label)
        }
    }

    /**
     * The badge says which mode ran. It must not also imply what the action
     * was: live enforcement carries the danger tone, and that tone's default
     * shape is the quarantine block, which beside a *release* record would put
     * a "blocked" glyph on an event that unblocked something.
     */
    @Test
    fun `the mode badge does not borrow an action's shape`() {
        val live = auditModeBadge(entries.first { it.isLiveEnforcement })!!
        val simulated = auditModeBadge(entries.first { it.isSimulated })!!

        assertNotEquals(NexaIcons.Quarantine, live.icon)
        assertNotEquals(NexaIcons.Release, live.icon)
        assertNotEquals(live.icon, simulated.icon)
        assertEquals(NexaIcons.Simulated, simulated.icon)
    }

    /**
     * The inference this model refuses to make. An alert being raised has no
     * execution mode; it must not acquire a live badge for lack of a
     * simulated one.
     */
    @Test
    fun `a record with no execution mode is not marked live`() {
        val modeless = entries.filter { it.executionMode == null }
        assertTrue(modeless.isNotEmpty())
        modeless.forEach { entry ->
            assertNull(entry.id, auditModeBadge(entry))
            assertFalse(entry.id, entry.isLiveEnforcement)
            assertFalse(entry.id, entry.isSimulated)
        }
    }

    // ============================================================
    // UNKNOWN
    // ============================================================

    @Test
    fun `an unknown outcome stays unknown`() {
        val unknown = entries.first { it.id == "EVT-4472" }
        assertEquals(AuditOutcome.Unknown, unknown.outcome)
        assertEquals(NexaStatus.Unknown, auditStatus(unknown))
        val text = auditHeadline(unknown).lowercase() + " " + auditExplanation(unknown).lowercase()
        assertTrue(text.contains("unknown"))
        assertFalse(text.contains("succeeded"))
        assertFalse(text.contains("completed"))
    }

    @Test
    fun `an unknown execution mode is never resolved into live or simulated`() {
        val unknownMode = entries.first { it.id == "EVT-4470" }
            .copy(executionMode = ExecutionMode.Unknown)

        assertFalse(unknownMode.isLiveEnforcement)
        assertFalse(unknownMode.isSimulated)
        assertEquals("MODE UNKNOWN", auditModeBadge(unknownMode)?.label)
        assertTrue(auditHeadline(unknownMode).contains("execution mode unknown"))

        val explanation = auditExplanation(unknownMode).lowercase()
        assertTrue(explanation.contains("was not recorded"))
        assertTrue(explanation.contains("cannot state whether"))
    }

    // ============================================================
    // ALERT / NOTIFICATION SEPARATION
    // ============================================================

    @Test
    fun `notification records are notifications, not action outcomes`() {
        val notifications = entries.filter { it.category == AuditCategory.Notification }
        assertTrue(notifications.isNotEmpty())
        notifications.forEach { entry ->
            assertNotEquals(entry.id, AuditCategory.Action, entry.category)
            assertNull(entry.id, entry.executionMode)
            assertNull(entry.id, entry.actionCode)
            assertTrue(entry.id, auditExplanation(entry).lowercase().contains("notification"))
        }
    }

    @Test
    fun `delivering a notification claims nothing about the alert`() {
        val delivered = entries.first { it.type == AuditEventType.NotificationDelivered }
        val explanation = auditExplanation(delivered).lowercase()
        assertTrue(explanation.contains("says nothing about whether the alert was handled"))
        assertFalse(explanation.contains("resolved"))
    }

    /**
     * A delivery failure produces no alert-lifecycle record at all. The alert
     * stays exactly where it was, which is the whole point of the separation.
     */
    @Test
    fun `a failed delivery does not close the alert it was about`() {
        val forAlert = entries.filter { it.alertId == "ALRT-1091" }
        assertTrue(forAlert.any { it.type == AuditEventType.NotificationFailed })
        assertTrue(forAlert.any { it.type == AuditEventType.AlertRaised })
        assertTrue(forAlert.none { it.type == AuditEventType.AlertResolved })
        assertTrue(forAlert.none { it.type == AuditEventType.AlertAcknowledged })
    }

    @Test
    fun `alert lifecycle records carry no execution mode`() {
        entries.filter { it.category == AuditCategory.Alert }.forEach { entry ->
            assertNull(entry.id, entry.executionMode)
        }
    }

    @Test
    fun `acknowledgement is recorded as acknowledgement, not closure`() {
        val acknowledged = entries.first { it.type == AuditEventType.AlertAcknowledged }
        assertEquals("ACKNOWLEDGED", acknowledged.resultingState)
        assertTrue(auditExplanation(acknowledged).contains("does not resolve it"))
    }

    // ============================================================
    // TRUST / ENFORCEMENT SEPARATION
    // ============================================================

    @Test
    fun `trust records are never enforcement records`() {
        val trust = entries.filter { it.category == AuditCategory.Trust }
        assertTrue(trust.isNotEmpty())
        trust.forEach { entry ->
            assertNotEquals(entry.id, AuditCategory.Enforcement, entry.category)
            assertNotEquals(entry.id, AuditCategory.Action, entry.category)
            assertNull(entry.id, entry.executionMode)
        }
    }

    @Test
    fun `revocation is stated as a trust decision`() {
        val revoked = entries.first { it.type == AuditEventType.IdentityRevoked }
        val explanation = auditExplanation(revoked)
        assertTrue(explanation.contains("trust"))
        assertTrue(explanation.contains("not itself an enforcement action"))
    }

    @Test
    fun `reverification is recorded as changing no firewall state`() {
        val reverify = entries.first { it.type == AuditEventType.ReverificationRequested }
        assertEquals(AuditCategory.Trust, reverify.category)
        assertTrue(auditExplanation(reverify).contains("changes no firewall state"))
    }

    @Test
    fun `verification is not presented as authorization`() {
        val verified = entries.first { it.type == AuditEventType.VerificationCompleted }
        assertTrue(auditExplanation(verified).contains("not authorization"))
    }

    // ============================================================
    // OBSERVATION IS NOT IDENTITY
    // ============================================================

    @Test
    fun `an address change is recorded as observation, not identity change`() {
        val changed = entries.first { it.type == AuditEventType.DeviceAddressChanged }
        assertEquals(AuditCategory.Device, changed.category)
        assertTrue(auditExplanation(changed).contains("device identity is unchanged"))
    }

    @Test
    fun `an observed address is labelled as observed`() {
        val detail = AuditPreview.detailFor("EVT-4401") as AuditDetailUiState.Content
        val labels = detail.data.fields.map { it.label }
        assertTrue(labels.contains("OBSERVED ADDRESS"))
        assertFalse(labels.contains("IDENTITY"))
    }

    // ============================================================
    // PRIVACY
    // ============================================================

    @Test
    fun `nothing in the record exposes secret material`() {
        val forbidden = listOf(
            "private key",
            "secret",
            "password",
            "bearer",
            "-----begin",
            "api_key",
            "apikey",
            "passphrase"
        )

        entries.forEach { entry ->
            val texts = buildList {
                add(auditHeadline(entry))
                add(auditExplanation(entry))
                auditDetailFields(entry).forEach {
                    add(it.label)
                    add(it.value)
                }
            }
            texts.forEach { text ->
                val lower = text.lowercase()
                forbidden.forEach { term ->
                    assertFalse("${entry.id} exposes \"$term\": $text", lower.contains(term))
                }
            }
        }
    }

    @Test
    fun `identity records say plainly that no key material is held`() {
        val created = entries.first { it.type == AuditEventType.IdentityCreated }
        assertTrue(auditExplanation(created).contains("No key material is shown here"))
    }

    // ============================================================
    // HISTORY IS NOT A CONTROL SURFACE
    // ============================================================

    /**
     * No link into the action flow, from any record. That screen requests
     * executions; history must never be one tap from repeating what it
     * describes.
     */
    @Test
    fun `no record offers a route back into the action flow`() {
        entries.forEach { entry ->
            auditLinks(entry).forEach { link ->
                assertTrue(
                    "${entry.id} links to $link",
                    link is AuditLink.Alert || link is AuditLink.Device || link is AuditLink.Identity
                )
            }
        }
    }

    // ============================================================
    // DERIVATION COVERAGE
    // ============================================================

    @Test
    fun `every event type produces a headline and an explanation in every mode`() {
        val base = entries.first { it.id == "EVT-4411" }
        val modes = listOf(null, ExecutionMode.Enforce, ExecutionMode.AuditOnly, ExecutionMode.Unknown)
        AuditEventType.entries.forEach { type ->
            modes.forEach { mode ->
                val entry = base.copy(type = type, executionMode = mode)
                assertTrue("$type/$mode", auditHeadline(entry).isNotBlank())
                assertTrue("$type/$mode", auditExplanation(entry).isNotBlank())
            }
        }
    }

    @Test
    fun `every simulated derivation carries the disclaimer whatever the type`() {
        val base = entries.first { it.id == "EVT-4411" }
        AuditEventType.entries.forEach { type ->
            val entry = base.copy(type = type, executionMode = ExecutionMode.AuditOnly)
            assertTrue(
                "$type",
                auditExplanation(entry).lowercase().contains("no firewall mutation")
            )
        }
    }
}
