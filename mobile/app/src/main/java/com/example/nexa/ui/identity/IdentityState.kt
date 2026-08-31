package com.example.nexa.ui.identity

import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
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

data class IdentityFilters(val trust: Set<TrustState> = emptySet()) {
    val isActive: Boolean get() = trust.isNotEmpty()
}

/**
 * Free-text match over operator-facing identifiers only.
 *
 * Never matches key material, and there is none in the model to match.
 */
fun List<IdentitySummary>.applyQuery(query: String): List<IdentitySummary> {
    val q = query.trim()
    if (q.isEmpty()) return this
    return filter { identity ->
        identity.identityId.contains(q, ignoreCase = true) ||
            identity.subjectLabel.contains(q, ignoreCase = true) ||
            (identity.owner?.contains(q, ignoreCase = true) == true) ||
            (identity.device?.mac?.contains(q, ignoreCase = true) == true) ||
            (identity.device?.ip?.contains(q, ignoreCase = true) == true) ||
            (identity.device?.scope?.contains(q, ignoreCase = true) == true)
    }
}

fun List<IdentitySummary>.applyFilters(filters: IdentityFilters): List<IdentitySummary> =
    filter { filters.trust.isEmpty() || it.trust in filters.trust }

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

fun List<IdentitySummary>.resolve(query: String, filters: IdentityFilters): List<IdentitySummary> =
    applyQuery(query)
        .applyFilters(filters)
        .sortedWith(compareBy({ identityAttentionRank(it) }, { it.subjectLabel.lowercase() }))

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
        val freshness: DataFreshness,
        val degraded: Boolean
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
