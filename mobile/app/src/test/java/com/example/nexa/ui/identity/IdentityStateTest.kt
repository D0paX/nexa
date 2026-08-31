package com.example.nexa.ui.identity

import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.hasIdentity
import com.example.nexa.ui.devices.Presence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the identity and trust rules.
 *
 * These exist to hold the Phase 2 boundaries in place: that observation is
 * not identity, identity is not trust, trust is not authorization, and that
 * no trust operation is offered for a state that cannot support it.
 */
class IdentityStateTest {

    private fun verification(
        label: String = "Verified 5m ago",
        freshness: DataFreshness = DataFreshness.Live,
        reverificationRequired: Boolean = false,
        reason: String? = null
    ) = VerificationSummary(
        lastVerifiedLabel = label,
        freshness = freshness,
        reverificationRequired = reverificationRequired,
        reverificationReason = reason
    )

    private fun credential(
        state: CredentialState = CredentialState.Active
    ) = CredentialSummary("CRED-1", state, "Effective now")

    private fun device(
        mac: String = "00:11:22:33:44:55",
        scope: String = "VLAN_A",
        presence: Presence = Presence.Present,
        freshness: DataFreshness = DataFreshness.Live
    ) = AssociatedDevice(
        deviceId = "DEV-1",
        label = "Device",
        mac = mac,
        ip = "10.0.0.1",
        scope = scope,
        presence = presence,
        recordFreshness = freshness,
        lastObservedLabel = "now"
    )

    private fun identity(
        id: String = "TID-1",
        subject: String = "Subject",
        trust: TrustState = TrustState.Trusted,
        verification: VerificationSummary = verification(),
        credential: CredentialSummary = credential(),
        device: AssociatedDevice? = device(),
        relationship: IdentityRelationship = IdentityRelationship.Confirmed,
        alerts: Int = 0,
        critical: Int = 0
    ) = IdentitySummary(
        identityId = id,
        subjectLabel = subject,
        owner = "owner@example.com",
        trust = trust,
        verification = verification,
        credential = credential,
        device = device,
        relationship = relationship,
        activeAlerts = alerts,
        criticalAlerts = critical
    )

    // ------------------------------------------------------------
    // Identity vs trust vs observation
    // ------------------------------------------------------------

    /** An identity existing is not the same as that identity being trusted. */
    @Test
    fun `pending identity exists but is not trusted`() {
        val i = identity(trust = TrustState.Pending)
        assertTrue(i.trust.hasIdentity)
        assertFalse(i.trust == TrustState.Trusted)
    }

    /** Unverified is a device-level answer and never an identity's own state. */
    @Test
    fun `unverified means no identity exists`() {
        assertFalse(TrustState.Unverified.hasIdentity)
        assertTrue(TrustState.Trusted.hasIdentity)
        assertTrue(TrustState.Pending.hasIdentity)
        assertTrue(TrustState.Revoked.hasIdentity)
    }

    /** Presence belongs to the observed device, not to the identity. */
    @Test
    fun `a trusted identity can be bound to an absent device`() {
        val i = identity(trust = TrustState.Trusted, device = device(presence = Presence.Absent))
        assertEquals(TrustState.Trusted, i.trust)
        assertEquals(Presence.Absent, i.device!!.presence)
    }

    // ------------------------------------------------------------
    // Trust vs authorization
    // ------------------------------------------------------------

    /**
     * The distinction this whole checkpoint turns on: a trusted identity
     * still requires operator authorization, and the model states that
     * separately rather than deriving it from trust.
     */
    @Test
    fun `trusted identity still requires operator approval`() {
        val state = IdentityPreview.detailFor("TID-88F1") as IdentityDetailUiState.Content
        assertEquals(TrustState.Trusted, state.data.identity.trust)
        assertTrue(state.data.authorization.operatorApprovalRequired)
        assertTrue(state.data.authorization.note.contains("does not grant authorization"))
    }

    // ------------------------------------------------------------
    // Reverification availability
    // ------------------------------------------------------------

    @Test
    fun `reverification is offered for a trusted identity`() {
        val action = availableIdentityActions(identity(trust = TrustState.Trusted))
            .single { it.kind == IdentityActionKind.RequireReverification }
        assertTrue(action.enabled)
        assertEquals("REQUIRE_REVERIFICATION", action.actionCode)
    }

    @Test
    fun `reverification is offered for a pending identity`() {
        val action = availableIdentityActions(identity(trust = TrustState.Pending))
            .single { it.kind == IdentityActionKind.RequireReverification }
        assertTrue(action.enabled)
    }

    /** Reverification is not the operation that undoes a revocation. */
    @Test
    fun `revoked identity cannot be reverified and says why`() {
        val action = availableIdentityActions(identity(trust = TrustState.Revoked))
            .single { it.kind == IdentityActionKind.RequireReverification }
        assertFalse(action.enabled)
        assertNotNull(action.disabledReason)
        assertTrue(action.disabledReason!!.contains("does not restore"))
    }

    @Test
    fun `unavailable association disables reverification with a stated reason`() {
        val action = availableIdentityActions(
            identity(relationship = IdentityRelationship.Unavailable)
        ).single { it.kind == IdentityActionKind.RequireReverification }
        assertFalse(action.enabled)
        assertNotNull(action.disabledReason)
    }

    /** No identity, nothing to reverify. */
    @Test
    fun `no reverification is offered without an identity`() {
        val actions = availableIdentityActions(identity(trust = TrustState.Unverified))
        assertTrue(actions.none { it.kind == IdentityActionKind.RequireReverification })
    }

    /** Trust management never offers an enforcement operation. */
    @Test
    fun `identity actions never include quarantine or revocation`() {
        TrustState.entries.forEach { trust ->
            availableIdentityActions(identity(trust = trust)).forEach { action ->
                assertFalse(action.actionCode.contains("QUARANTINE"))
                assertFalse(action.actionCode.contains("REVOKE"))
            }
        }
    }

    @Test
    fun `view device is only offered when a device is associated`() {
        assertTrue(
            availableIdentityActions(identity(device = device()))
                .any { it.kind == IdentityActionKind.ViewDevice }
        )
        assertTrue(
            availableIdentityActions(identity(device = null))
                .none { it.kind == IdentityActionKind.ViewDevice }
        )
    }

    // ------------------------------------------------------------
    // Reverification justification
    // ------------------------------------------------------------

    @Test
    fun `no reverification prompt is invented when none is required`() {
        assertNull(reverificationPrompt(identity(verification = verification(reverificationRequired = false))))
    }

    @Test
    fun `reverification prompt uses the authoritative reason when present`() {
        val prompt = reverificationPrompt(
            identity(
                verification = verification(
                    reverificationRequired = true,
                    reason = "The observed device context changed."
                )
            )
        )
        assertEquals("The observed device context changed.", prompt)
    }

    // ------------------------------------------------------------
    // Verification freshness
    // ------------------------------------------------------------

    @Test
    fun `stale verification is labelled stale rather than shown as current`() {
        val v = verification(label = "Verified 2h ago", freshness = DataFreshness.Stale("Verified 2h ago"))
        assertFalse(v.isCurrent)
        assertTrue(v.freshnessLabel.contains("stale"))
    }

    @Test
    fun `unknown freshness reports the context as unavailable`() {
        val v = verification(freshness = DataFreshness.Unknown)
        assertFalse(v.isCurrent)
        assertEquals("Verification context unavailable", v.freshnessLabel)
    }

    @Test
    fun `live verification reports its own label`() {
        val v = verification(label = "Verified 5m ago", freshness = DataFreshness.Live)
        assertTrue(v.isCurrent)
        assertEquals("Verified 5m ago", v.freshnessLabel)
    }

    // ------------------------------------------------------------
    // Association / mismatch
    // ------------------------------------------------------------

    /** When readings conflict, NEXA admits it rather than picking one. */
    @Test
    fun `ambiguous association refuses to assert the relationship`() {
        val explanation = IdentityRelationship.Ambiguous.explanation
        assertTrue(explanation.contains("cannot safely assert"))
    }

    @Test
    fun `preview contains an identity whose association is ambiguous`() {
        val ambiguous = IdentityPreview.identities.single { it.relationship == IdentityRelationship.Ambiguous }
        assertEquals(TrustState.Trusted, ambiguous.trust)
        assertNotNull(ambiguous.relationshipNote)
    }

    @Test
    fun `an identity can exist with no associated device`() {
        val orphan = IdentityPreview.identities.single { it.device == null }
        assertEquals(IdentityRelationship.Unavailable, orphan.relationship)
    }

    // ------------------------------------------------------------
    // Credentials
    // ------------------------------------------------------------

    @Test
    fun `credential states present distinctly`() {
        assertEquals("Active", CredentialState.Active.label)
        assertEquals("Superseded", CredentialState.Superseded.label)
        assertEquals("Revoked", CredentialState.Revoked.label)
        assertTrue(CredentialState.Revoked.status != CredentialState.Active.status)
    }

    @Test
    fun `revoked identity carries a revoked credential`() {
        val revoked = IdentityPreview.identities.single { it.trust == TrustState.Revoked }
        assertEquals(CredentialState.Revoked, revoked.credential.state)
    }

    @Test
    fun `credential history is returned for a rotated identity`() {
        val state = IdentityPreview.detailFor("TID-2B0C") as IdentityDetailUiState.Content
        assertTrue(state.data.credentialHistory.size > 1)
    }

    // ------------------------------------------------------------
    // Search / filter
    // ------------------------------------------------------------

    @Test
    fun `search matches identity id owner mac and scope`() {
        val list = IdentityPreview.identities
        assertTrue(list.applyQuery("88F1").isNotEmpty())
        assertTrue(list.applyQuery("ci-runner").isNotEmpty())
        assertTrue(list.applyQuery("AA:BB").isNotEmpty())
        assertTrue(list.applyQuery("VLAN_BUILD").isNotEmpty())
    }

    @Test
    fun `search returns empty for no match`() {
        assertTrue(IdentityPreview.identities.applyQuery("no-such-identity").isEmpty())
    }

    @Test
    fun `trust filter narrows to the selected lifecycle states`() {
        val revoked = IdentityPreview.identities.applyFilters(IdentityFilters(setOf(TrustState.Revoked)))
        assertEquals(1, revoked.size)
        assertEquals(TrustState.Revoked, revoked.first().trust)
    }

    @Test
    fun `revoked and ambiguous identities sort above healthy ones`() {
        val sorted = IdentityPreview.identities.resolve("", IdentityFilters())
        assertEquals(TrustState.Revoked, sorted.first().trust)
        assertTrue(sorted.size == IdentityPreview.identities.size)
    }

    // ------------------------------------------------------------
    // States
    // ------------------------------------------------------------

    /** No identities is a confirmed answer; unavailable is the absence of one. */
    @Test
    fun `empty identity set is content and not unavailable`() {
        val empty = IdentityPreview.empty()
        assertTrue(empty is IdentitiesUiState.Content)
        assertTrue((empty as IdentitiesUiState.Content).all.isEmpty())
        assertTrue(IdentityPreview.unavailable() is IdentitiesUiState.Unavailable)
        assertTrue(IdentityPreview.offline() is IdentitiesUiState.Offline)
    }

    @Test
    fun `unknown identity id reports unavailable instead of inventing one`() {
        assertTrue(IdentityPreview.detailFor("TID-NOPE") is IdentityDetailUiState.Unavailable)
    }

    // ------------------------------------------------------------
    // Attention presentation
    // ------------------------------------------------------------

    @Test
    fun `revocation outranks every other attention signal`() {
        assertEquals("REVOKED", identityAttentionLabel(identity(trust = TrustState.Revoked, critical = 3)))
    }

    @Test
    fun `a healthy current identity carries no attention badge`() {
        assertNull(identityAttentionBadge(identity()))
        assertNull(identityAttentionLabel(identity()))
    }

    @Test
    fun `subtitle keeps trust and credential standing separate`() {
        val subtitle = identitySubtitle(
            identity(trust = TrustState.Pending, credential = credential(CredentialState.Superseded))
        )
        assertEquals("Pending · Superseded · VLAN_A", subtitle)
    }

    // ------------------------------------------------------------
    // Trust operations are not enforcement operations
    // ------------------------------------------------------------

    /**
     * Reverification must never be described with quarantine's language.
     * Mirrors the mapping in ActionConfirmationScreen; if that wording is
     * ever pointed back at enforcement, this fails.
     */
    @Test
    fun `reverification action code is distinct from enforcement codes`() {
        val reverify = availableIdentityActions(identity(trust = TrustState.Trusted))
            .single { it.kind == IdentityActionKind.RequireReverification }
        assertEquals("REQUIRE_REVERIFICATION", reverify.actionCode)
        assertFalse(reverify.actionCode == "QUARANTINE_DEVICE")
        assertFalse(reverify.actionCode == "RELEASE_QUARANTINE")
    }

    /** The reverification prompt never claims an enforcement effect. */
    @Test
    fun `reverification reasons never mention quarantine or isolation`() {
        IdentityPreview.identities
            .mapNotNull { reverificationPrompt(it) }
            .forEach { prompt ->
                val lower = prompt.lowercase()
                assertFalse(lower.contains("quarantine"))
                assertFalse(lower.contains("isolate"))
                assertFalse(lower.contains("firewall"))
            }
    }

    // ------------------------------------------------------------
    // Secrets
    // ------------------------------------------------------------

    /**
     * A structural guard: the identity model has no field that could carry
     * key material, and the preview data contains no secret-looking value.
     */
    @Test
    fun `preview identity data exposes no secret material`() {
        val forbidden = listOf("PRIVATE KEY", "BEGIN RSA", "secret", "password", "bearer")
        IdentityPreview.identities.forEach { identity ->
            val rendered = listOfNotNull(
                identity.identityId,
                identity.subjectLabel,
                identity.owner,
                identity.credential.identifier,
                identity.credential.note,
                identity.relationshipNote
            ).joinToString(" ").lowercase()
            forbidden.forEach { term ->
                assertFalse(
                    "identity ${identity.identityId} must not expose $term",
                    rendered.contains(term.lowercase())
                )
            }
        }
    }
}
