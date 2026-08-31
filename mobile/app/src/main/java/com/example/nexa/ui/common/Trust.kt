package com.example.nexa.ui.common

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus

/**
 * The Phase 2 trust vocabulary, shared by every feature that talks about it.
 *
 * One enum so identity and device screens cannot drift into two competing
 * status systems. The states mirror the Phase 2 lifecycle and nothing more —
 * no display-only state is invented here.
 *
 * [Unverified] is a device-level answer, not an identity lifecycle state: it
 * means the network observed something for which no cryptographic identity
 * exists at all. An identity is never [Unverified], because an identity
 * existing is precisely what that state denies.
 */
enum class TrustState {
    /** An identity exists; verification has not completed. */
    Pending,

    /** An identity exists and is currently verified. */
    Trusted,

    /** An identity existed and has been revoked. */
    Revoked,

    /** Device-level only: observed, with no cryptographic identity. */
    Unverified,

    /** Standing cannot currently be determined. */
    Unknown
}

val TrustState.status: NexaStatus
    get() = when (this) {
        TrustState.Trusted -> NexaStatus.Verified
        TrustState.Pending -> NexaStatus.Information
        TrustState.Revoked -> NexaStatus.Danger
        TrustState.Unverified -> NexaStatus.Warning
        TrustState.Unknown -> NexaStatus.Unknown
    }

val TrustState.label: String
    get() = when (this) {
        TrustState.Trusted -> "Trusted"
        TrustState.Pending -> "Pending"
        TrustState.Revoked -> "Revoked"
        TrustState.Unverified -> "Unverified"
        TrustState.Unknown -> "Trust unknown"
    }

/** Trust is never carried by color alone — each state has its own shape. */
val TrustState.icon: ImageVector
    get() = when (this) {
        TrustState.Trusted -> NexaIcons.Secure
        TrustState.Pending -> NexaIcons.Pending
        TrustState.Revoked -> NexaIcons.Revoked
        TrustState.Unverified -> NexaIcons.Warning
        TrustState.Unknown -> NexaIcons.Unknown
    }

/** Whether a cryptographic identity exists at all for this standing. */
val TrustState.hasIdentity: Boolean
    get() = this == TrustState.Trusted || this == TrustState.Pending || this == TrustState.Revoked
