package com.example.nexa.ui.identity

import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.NexaQuery
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.facetMatches
import com.example.nexa.ui.common.matches
import com.example.nexa.ui.common.nexaQuery
import com.example.nexa.ui.common.hasIdentity
import com.example.nexa.ui.devices.Presence

/**
 * The operator-facing model of Phase 2 identity and trust.
 *
 * The whole point of this model is to keep four things apart that an
 * interface finds tempting to merge:
 *
 *   observation    — the network saw something (Phase 1)
 *   identity       — a cryptographic subject exists (Phase 2)
 *   trust          — that identity is currently verified (Phase 2)
 *   authorization  — an operator may perform a given action (Phase 4)
 *
 * Each is a separate field here. None is derived from another.
 */

// ============================================================
// CREDENTIALS
// ============================================================

/**
 * Credential lifecycle standing.
 *
 * Lifecycle only — no key material, no secret, ever reaches this model.
 */
enum class CredentialState { Active, Superseded, Revoked }

/**
 * A credential's standing and when it took effect.
 *
 * [identifier] is an operator-facing label for the credential, never the
 * credential itself.
 */
data class CredentialSummary(
    val identifier: String,
    val state: CredentialState,
    val effectiveLabel: String,
    val note: String? = null
)

// ============================================================
// VERIFICATION
// ============================================================

/**
 * What NEXA knows about the last verification, and whether that knowledge
 * is still current.
 *
 * [reverificationRequired] is authoritative state, not something the UI
 * computes from a timestamp — the client does not own the freshness policy.
 */
data class VerificationSummary(
    val lastVerifiedLabel: String,
    val freshness: DataFreshness,
    val reverificationRequired: Boolean,
    val reverificationReason: String? = null,
    val nextReverificationLabel: String? = null
)

// ============================================================
// AUTHORIZATION — deliberately separate from trust
// ============================================================

/**
 * Whether an operator may act, which is a different question from whether
 * an identity is trusted.
 *
 * A trusted identity still requires authorization; this type exists so the
 * interface cannot quietly answer the second question with the first.
 */
data class AuthorizationSummary(
    val operatorApprovalRequired: Boolean,
    val note: String
)

// ============================================================
// DEVICE ASSOCIATION
// ============================================================

/**
 * How confidently NEXA can tie this identity to an observed device.
 *
 * Ambiguity is a first-class outcome: when the association cannot be
 * asserted, the UI says so instead of silently picking one side.
 */
enum class IdentityRelationship {
    /** Observation and identity binding agree. */
    Confirmed,

    /** Observation and identity binding conflict, or more than one candidate exists. */
    Ambiguous,

    /** The association cannot currently be evaluated. */
    Unavailable
}

/**
 * The observed device context an identity is associated with.
 *
 * Address and MAC are context, never identity: Phase 4 protects against
 * stale-IP reassignment precisely because an address does not identify
 * anything on its own.
 */
data class AssociatedDevice(
    val deviceId: String,
    val label: String,
    val mac: String,
    val ip: String?,
    val scope: String,
    val presence: Presence,
    val recordFreshness: DataFreshness,
    val lastObservedLabel: String
)

// ============================================================
// IDENTITY
// ============================================================

data class IdentitySummary(
    val identityId: String,
    val subjectLabel: String,
    val owner: String?,
    val trust: TrustState,
    val verification: VerificationSummary,
    val credential: CredentialSummary,
    val device: AssociatedDevice?,
    val relationship: IdentityRelationship,
    val relationshipNote: String? = null,
    val activeAlerts: Int,
    val criticalAlerts: Int
)

data class IdentityAlertItem(
    val id: String,
    val title: String,
    val severity: String,
    val timeAgo: String
)

data class IdentityDetailData(
    val identity: IdentitySummary,
    val authorization: AuthorizationSummary,
    val credentialHistory: List<CredentialSummary>,
    val alerts: List<IdentityAlertItem>,
    val activity: List<ActivityEntry>,
    val actions: List<IdentityAction>
)

// ============================================================
// ACTIONS
// ============================================================

enum class IdentityActionKind { RequireReverification, ViewDevice }

/**
 * A trust-management action.
 *
 * [actionCode] is handed to the existing Phase 4 confirmation flow. The
 * client requests; TrustSessionManager and the enforcement pipeline decide
 * and execute. Nothing here mutates TrustState or credential state.
 */
data class IdentityAction(
    val kind: IdentityActionKind,
    val label: String,
    val actionCode: String,
    val enabled: Boolean,
    val disabledReason: String? = null
)

/**
 * Which trust operations this identity's state actually supports.
 *
 * Reverification requires an identity that still exists and can be
 * re-checked. A revoked identity is not offered reverification — trust was
 * withdrawn, and re-running verification is not the operation that changes
 * that. When the association is unavailable, the action is disabled rather
 * than hidden, so the operator sees why.
 *
 * Reverification is a trust operation and is deliberately NOT a quarantine
 * or a revocation; no enforcement action is offered from this model.
 */
fun availableIdentityActions(identity: IdentitySummary): List<IdentityAction> {
    val actions = mutableListOf<IdentityAction>()

    when {
        !identity.trust.hasIdentity -> Unit // Nothing to reverify.

        identity.trust == TrustState.Revoked -> actions += IdentityAction(
            kind = IdentityActionKind.RequireReverification,
            label = "Require Reverification",
            actionCode = "REQUIRE_REVERIFICATION",
            enabled = false,
            disabledReason = "This identity is revoked. Reverification does not restore withdrawn trust."
        )

        identity.relationship == IdentityRelationship.Unavailable -> actions += IdentityAction(
            kind = IdentityActionKind.RequireReverification,
            label = "Require Reverification",
            actionCode = "REQUIRE_REVERIFICATION",
            enabled = false,
            disabledReason = "The device association for this identity cannot currently be evaluated."
        )

        else -> actions += IdentityAction(
            kind = IdentityActionKind.RequireReverification,
            label = "Require Reverification",
            actionCode = "REQUIRE_REVERIFICATION",
            enabled = true
        )
    }

    if (identity.device != null) {
        actions += IdentityAction(
            kind = IdentityActionKind.ViewDevice,
            label = "View Observed Device",
            actionCode = "",
            enabled = true
        )
    }

    return actions
}

/**
 * Why reverification is being suggested, when the authoritative state says
 * it is required. Returns null when nothing requires it — the UI never
 * invents a justification.
 */
fun reverificationPrompt(identity: IdentitySummary): String? = when {
    !identity.verification.reverificationRequired -> null
    identity.verification.reverificationReason != null -> identity.verification.reverificationReason
    else -> "Reverification is required for this identity."
}

// ============================================================
// LIST QUERY / FILTER
// ============================================================

/**
 * What an operator can narrow the identity inventory by.
 *
 * Four facets, each answering a different question, combined with the shared
 * rule — AND between them, OR within each:
 *
 *   trust         where the identity stands in its trust lifecycle
 *   relationship  whether observation and binding agree
 *   scopes        which network scope the associated device sits in
 *   freshness     how recently verification was confirmed
 *
 * Trust here is a *view* selector. Selecting "Trusted" shows the identities
 * that are trusted; it does not make anything trusted, and it authorizes
 * nothing. The record's trust standing is decided by Phase 2 and remains
 * exactly what it was whether or not the filter is on.
 */
data class IdentityFilters(
    val trust: Set<TrustState> = emptySet(),
    val relationship: Set<IdentityRelationship> = emptySet(),
    val scopes: Set<String> = emptySet(),
    val freshness: Set<IdentityFreshnessFacet> = emptySet()
) {
    val isActive: Boolean
        get() = trust.isNotEmpty() || relationship.isNotEmpty() ||
            scopes.isNotEmpty() || freshness.isNotEmpty()

    val activeCount: Int
        get() = trust.size + relationship.size + scopes.size + freshness.size
}

/**
 * Verification freshness, as something selectable.
 *
 * A narrower vocabulary than [DataFreshness] on purpose: an operator wants
 * "which identities have not been confirmed recently", not the full
 * availability vocabulary. [Unknown] stays separate from [Stale] because not
 * having looked and having looked a while ago are different facts.
 */
enum class IdentityFreshnessFacet(val label: String) {
    Current("Recently verified"),
    Stale("Not recent"),
    Unknown("Verification unknown")
}

fun identityFreshnessFacet(freshness: DataFreshness): IdentityFreshnessFacet = when (freshness) {
    is DataFreshness.Live -> IdentityFreshnessFacet.Current
    is DataFreshness.Stale -> IdentityFreshnessFacet.Stale
    is DataFreshness.Unknown -> IdentityFreshnessFacet.Unknown
}

enum class IdentitySort(val label: String) {
    /** Identities needing an operator first. The default. */
    Attention("Needs attention"),
    Subject("Subject"),
    Trust("Trust")
}

/**
 * The text an identity is searchable by.
 *
 * Operator-facing identifiers only. There is no key material on this model to
 * match — the credential is represented by an identifier and a lifecycle
 * state, never by the key itself — so search cannot reach anything secret.
 * The list is written out explicitly so that adding a field to
 * [IdentitySummary] can never silently make it searchable.
 */
fun identitySearchFields(identity: IdentitySummary): List<String?> = listOf(
    identity.identityId,
    identity.subjectLabel,
    identity.owner,
    identity.trust.name,
    identity.credential.identifier,
    identity.device?.label,
    identity.device?.mac,
    identity.device?.ip,
    identity.device?.scope,
    identity.device?.deviceId
)

fun List<IdentitySummary>.applyQuery(query: NexaQuery): List<IdentitySummary> =
    if (!query.isActive) this else filter { query.matches(identitySearchFields(it)) }

/**
 * Convenience overload: normalizes then matches.
 *
 * The normalized form is what matching actually uses, so a caller that has a
 * raw string goes through the same door as everything else rather than
 * inventing its own trimming.
 */
fun List<IdentitySummary>.applyQuery(query: String): List<IdentitySummary> = applyQuery(nexaQuery(query))

fun List<IdentitySummary>.applyFilters(filters: IdentityFilters): List<IdentitySummary> =
    filter { identity ->
        filters.trust.facetMatches(identity.trust) &&
            filters.relationship.facetMatches(identity.relationship) &&
            filters.scopes.facetMatches(identity.device?.scope) &&
            filters.freshness.facetMatches(
                identityFreshnessFacet(identity.verification.freshness)
            )
    }

/** Identities needing attention sort first; none are ever hidden. */
fun identityAttentionRank(identity: IdentitySummary): Int = when {
    identity.trust == TrustState.Revoked -> 0
    identity.criticalAlerts > 0 -> 1
    identity.relationship == IdentityRelationship.Ambiguous -> 2
    identity.verification.reverificationRequired -> 3
    identity.relationship == IdentityRelationship.Unavailable -> 4
    identity.verification.freshness is DataFreshness.Unknown -> 5
    identity.verification.freshness is DataFreshness.Stale -> 6
    identity.trust == TrustState.Pending -> 7
    else -> 8
}

private fun trustOrder(trust: TrustState): Int = when (trust) {
    TrustState.Revoked -> 0
    TrustState.Unverified -> 1
    TrustState.Unknown -> 2
    TrustState.Pending -> 3
    TrustState.Trusted -> 4
}

/**
 * Ordering, always ending in the identity id.
 *
 * Two identities can carry the same subject label — that is exactly what an
 * ambiguous binding looks like — so the id is what makes the order hold still
 * between loads and across realtime updates.
 */
fun List<IdentitySummary>.applySort(sort: IdentitySort): List<IdentitySummary> = when (sort) {
    IdentitySort.Attention -> sortedWith(
        compareBy({ identityAttentionRank(it) }, { it.subjectLabel.lowercase() }, { it.identityId })
    )
    IdentitySort.Subject -> sortedWith(
        compareBy({ it.subjectLabel.lowercase() }, { it.identityId })
    )
    IdentitySort.Trust -> sortedWith(
        compareBy({ trustOrder(it.trust) }, { it.subjectLabel.lowercase() }, { it.identityId })
    )
}

/** The whole pipeline. Same order as every other domain. */
fun List<IdentitySummary>.resolve(
    query: String,
    filters: IdentityFilters,
    sort: IdentitySort = IdentitySort.Attention
): List<IdentitySummary> =
    applyQuery(nexaQuery(query)).applyFilters(filters).applySort(sort)

// ============================================================
// SCREEN STATE
// ============================================================

sealed interface IdentitiesUiState {
    data object Loading : IdentitiesUiState

    data class Content(
        val all: List<IdentitySummary>,
        val visible: List<IdentitySummary>,
        val query: String,
        val filters: IdentityFilters,
        val sort: IdentitySort,
        val freshness: DataFreshness,
        val degraded: Boolean,
        /** Cached identity state, no connection. Distinct from merely stale. */
        val offline: Boolean = false
    ) : IdentitiesUiState

    data object Offline : IdentitiesUiState

    /** Identity state could not be retrieved — not the same as having none. */
    data object Unavailable : IdentitiesUiState

    data class Error(val message: String) : IdentitiesUiState
}

sealed interface IdentityDetailUiState {
    data object Loading : IdentityDetailUiState
    data class Content(val data: IdentityDetailData) : IdentityDetailUiState
    data object Unavailable : IdentityDetailUiState
    data class Error(val message: String) : IdentityDetailUiState
}
