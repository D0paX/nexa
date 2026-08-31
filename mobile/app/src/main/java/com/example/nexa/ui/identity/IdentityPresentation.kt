package com.example.nexa.ui.identity

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.label

/**
 * How identity state is presented.
 *
 * Every label and badge is resolved here rather than inside a composable, so
 * a security judgement is never made mid-layout and each mapping can be
 * tested on its own.
 */

val CredentialState.status: NexaStatus
    get() = when (this) {
        CredentialState.Active -> NexaStatus.Secure
        CredentialState.Superseded -> NexaStatus.Information
        CredentialState.Revoked -> NexaStatus.Danger
    }

val CredentialState.label: String
    get() = when (this) {
        CredentialState.Active -> "Active"
        CredentialState.Superseded -> "Superseded"
        CredentialState.Revoked -> "Revoked"
    }

val CredentialState.icon: ImageVector
    get() = when (this) {
        CredentialState.Active -> NexaIcons.Credential
        CredentialState.Superseded -> NexaIcons.Pending
        CredentialState.Revoked -> NexaIcons.Revoked
    }

val IdentityRelationship.status: NexaStatus
    get() = when (this) {
        IdentityRelationship.Confirmed -> NexaStatus.Secure
        IdentityRelationship.Ambiguous -> NexaStatus.Warning
        IdentityRelationship.Unavailable -> NexaStatus.Unknown
    }

val IdentityRelationship.label: String
    get() = when (this) {
        IdentityRelationship.Confirmed -> "Association confirmed"
        IdentityRelationship.Ambiguous -> "Association ambiguous"
        IdentityRelationship.Unavailable -> "Association unavailable"
    }

/**
 * What NEXA is willing to say about the identity-to-device relationship.
 *
 * The ambiguous wording is deliberately an admission rather than a guess:
 * when two readings are possible, saying so is the safe answer.
 */
val IdentityRelationship.explanation: String
    get() = when (this) {
        IdentityRelationship.Confirmed ->
            "The observed device record matches this identity's binding."
        IdentityRelationship.Ambiguous ->
            "NEXA cannot safely assert this relationship. The observed device context does not agree with this identity's binding."
        IdentityRelationship.Unavailable ->
            "NEXA cannot currently evaluate the association between this identity and an observed device."
    }

/**
 * How verification freshness reads.
 *
 * Verification is stated as a past event with an age, never as a standing
 * guarantee, and an unknown age is reported as unknown.
 */
val VerificationSummary.freshnessLabel: String
    get() = when (freshness) {
        is DataFreshness.Live -> lastVerifiedLabel
        is DataFreshness.Stale -> "Verification stale — $lastVerifiedLabel"
        is DataFreshness.Unknown -> "Verification context unavailable"
    }

val VerificationSummary.freshnessStatus: NexaStatus
    get() = when (freshness) {
        is DataFreshness.Live -> NexaStatus.Secure
        is DataFreshness.Stale -> NexaStatus.Warning
        is DataFreshness.Unknown -> NexaStatus.Unknown
    }

val VerificationSummary.isCurrent: Boolean
    get() = freshness is DataFreshness.Live

/** The single worst thing about this identity, or null when nothing is outstanding. */
fun identityAttentionBadge(identity: IdentitySummary): NexaStatus? = when {
    identity.trust == TrustState.Revoked -> NexaStatus.Danger
    identity.criticalAlerts > 0 -> NexaStatus.Critical
    identity.relationship == IdentityRelationship.Ambiguous -> NexaStatus.Warning
    identity.verification.reverificationRequired -> NexaStatus.Warning
    identity.relationship == IdentityRelationship.Unavailable -> NexaStatus.Unknown
    identity.verification.freshness is DataFreshness.Unknown -> NexaStatus.Unknown
    identity.verification.freshness is DataFreshness.Stale -> NexaStatus.Warning
    identity.trust == TrustState.Pending -> NexaStatus.Information
    else -> null
}

fun identityAttentionLabel(identity: IdentitySummary): String? = when {
    identity.trust == TrustState.Revoked -> "REVOKED"
    identity.criticalAlerts > 0 -> "CRITICAL"
    identity.relationship == IdentityRelationship.Ambiguous -> "AMBIGUOUS"
    identity.verification.reverificationRequired -> "REVERIFY"
    identity.relationship == IdentityRelationship.Unavailable -> "UNAVAILABLE"
    identity.verification.freshness is DataFreshness.Unknown -> "UNKNOWN"
    identity.verification.freshness is DataFreshness.Stale -> "STALE"
    identity.trust == TrustState.Pending -> "PENDING"
    else -> null
}

/**
 * The subtitle for an identity row: trust standing and credential standing
 * as two separate facts, plus the scope that gives the association meaning.
 */
fun identitySubtitle(identity: IdentitySummary): String {
    val base = "${identity.trust.label} · ${identity.credential.state.label}"
    val scope = identity.device?.scope
    return if (scope != null) "$base · $scope" else base
}
