package com.example.nexa.ui.identity

import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.ActivityKind
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.Presence

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE.
 *
 * Fabricated identity state for development. Nothing here performs or
 * reflects real cryptographic verification, and no Phase 2 API is implied.
 * Identifiers are operator-facing labels only — there is deliberately no
 * field anywhere in this file that could hold key material.
 *
 * The scenarios are chosen to cover the combinations that a careless
 * interface would flatten: trusted-but-stale, present-but-unverified,
 * revoked, pending, and an identity whose device association cannot be
 * asserted at all.
 */
object IdentityPreview {

    val scenario: IdentitiesUiState get() = content()

    fun content(): IdentitiesUiState {
        val all = identities
        return IdentitiesUiState.Content(
            all = all,
            visible = all.resolve("", IdentityFilters()),
            query = "",
            filters = IdentityFilters(),
            freshness = DataFreshness.Live,
            degraded = false
        )
    }

    fun empty(): IdentitiesUiState = IdentitiesUiState.Content(
        all = emptyList(),
        visible = emptyList(),
        query = "",
        filters = IdentityFilters(),
        freshness = DataFreshness.Live,
        degraded = false
    )

    fun degraded(): IdentitiesUiState =
        (content() as IdentitiesUiState.Content).copy(degraded = true)

    fun stale(): IdentitiesUiState = (content() as IdentitiesUiState.Content)
        .copy(freshness = DataFreshness.Stale("Last confirmed 12 min ago"))

    /**
     * The connection is gone and the last confirmed picture is still on
     * screen, marked as such.
     *
     * Deliberately not the same as [offline], which is the surface for
     * having no cache at all. Blanking a screen the moment connectivity
     * drops throws away the only information an operator still has.
     */
    fun offlineWithCache(): IdentitiesUiState = (content() as IdentitiesUiState.Content)
        .copy(offline = true, freshness = DataFreshness.Stale("Last confirmed 12 min ago"))

    fun offline(): IdentitiesUiState = IdentitiesUiState.Offline

    fun unavailable(): IdentitiesUiState = IdentitiesUiState.Unavailable

    // --- Identities ---

    val identities: List<IdentitySummary> = listOf(
        // Trusted, but the device it is bound to is quarantined and noisy.
        IdentitySummary(
            identityId = "TID-88F1",
            subjectLabel = "Corp Laptop - Engineering",
            owner = "jsmith@example.com",
            trust = TrustState.Trusted,
            verification = VerificationSummary(
                lastVerifiedLabel = "Verified 41m ago",
                freshness = DataFreshness.Live,
                reverificationRequired = false,
                nextReverificationLabel = "Next reverification in 19m"
            ),
            credential = CredentialSummary(
                identifier = "CRED-7719",
                state = CredentialState.Active,
                effectiveLabel = "Effective since 12 Aug"
            ),
            device = AssociatedDevice(
                deviceId = "DEV-1001",
                label = "Corp Laptop - Engineering",
                mac = "00:1A:2B:3C:4D:5E",
                ip = "192.168.1.105",
                scope = "VLAN_SECURE",
                presence = Presence.Present,
                recordFreshness = DataFreshness.Live,
                lastObservedLabel = "2m ago"
            ),
            relationship = IdentityRelationship.Confirmed,
            activeAlerts = 2,
            criticalAlerts = 1
        ),
        // Pending: identity exists, verification has not completed.
        IdentitySummary(
            identityId = "TID-77C4",
            subjectLabel = "Ops Workstation",
            owner = "ops@example.com",
            trust = TrustState.Pending,
            verification = VerificationSummary(
                lastVerifiedLabel = "Never completed",
                freshness = DataFreshness.Live,
                reverificationRequired = true,
                reverificationReason = "Initial verification has not completed for this identity.",
                nextReverificationLabel = null
            ),
            credential = CredentialSummary(
                identifier = "CRED-8102",
                state = CredentialState.Active,
                effectiveLabel = "Issued 5m ago",
                note = "Awaiting first successful verification."
            ),
            device = AssociatedDevice(
                deviceId = "DEV-1006",
                label = "Ops Workstation",
                mac = "00:9F:2C:1D:4E:7B",
                ip = "10.20.4.52",
                scope = "VLAN_BUILD",
                presence = Presence.Present,
                recordFreshness = DataFreshness.Live,
                lastObservedLabel = "5m ago"
            ),
            relationship = IdentityRelationship.Confirmed,
            activeAlerts = 0,
            criticalAlerts = 0
        ),
        // Revoked: trust withdrawn. Device is no longer present.
        IdentitySummary(
            identityId = "TID-51AA",
            subjectLabel = "Reception Tablet",
            owner = "reception@example.com",
            trust = TrustState.Revoked,
            verification = VerificationSummary(
                lastVerifiedLabel = "Last verified 3h ago",
                freshness = DataFreshness.Stale("Last verified 3h ago"),
                reverificationRequired = false,
                reverificationReason = null
            ),
            credential = CredentialSummary(
                identifier = "CRED-4410",
                state = CredentialState.Revoked,
                effectiveLabel = "Revoked 2h ago",
                note = "Revoked by operator after device decommissioning request."
            ),
            device = AssociatedDevice(
                deviceId = "DEV-1004",
                label = "Reception Tablet",
                mac = "AA:BB:CC:DD:EE:FF",
                ip = "192.168.9.30",
                scope = "VLAN_GUEST",
                presence = Presence.Absent,
                recordFreshness = DataFreshness.Stale("Last seen 3h ago"),
                lastObservedLabel = "3h ago"
            ),
            relationship = IdentityRelationship.Confirmed,
            activeAlerts = 0,
            criticalAlerts = 0
        ),
        // Trusted identity whose observed context no longer agrees with its
        // binding: the honest answer is that NEXA cannot assert the relationship.
        IdentitySummary(
            identityId = "TID-2B0C",
            subjectLabel = "Build Server",
            owner = "ci-runner@example.com",
            trust = TrustState.Trusted,
            verification = VerificationSummary(
                lastVerifiedLabel = "Verified 2h ago",
                freshness = DataFreshness.Stale("Verified 2h ago"),
                reverificationRequired = true,
                reverificationReason = "The observed device context changed since the last verification."
            ),
            credential = CredentialSummary(
                identifier = "CRED-6650",
                state = CredentialState.Superseded,
                effectiveLabel = "Superseded 20m ago",
                note = "Replaced by a newer credential during rotation."
            ),
            device = AssociatedDevice(
                deviceId = "DEV-1003",
                label = "Build Server",
                mac = "3C:22:FB:19:04:A1",
                ip = "10.20.4.11",
                scope = "VLAN_BUILD",
                presence = Presence.Present,
                recordFreshness = DataFreshness.Live,
                lastObservedLabel = "1m ago"
            ),
            relationship = IdentityRelationship.Ambiguous,
            relationshipNote = "This MAC has also been observed in VLAN_LAB within the verification window.",
            activeAlerts = 0,
            criticalAlerts = 0
        ),
        // Identity whose association cannot currently be evaluated at all.
        IdentitySummary(
            identityId = "TID-9E12",
            subjectLabel = "Conference Display",
            owner = null,
            trust = TrustState.Trusted,
            verification = VerificationSummary(
                lastVerifiedLabel = "Verified 8m ago",
                freshness = DataFreshness.Unknown,
                reverificationRequired = false
            ),
            credential = CredentialSummary(
                identifier = "CRED-9001",
                state = CredentialState.Active,
                effectiveLabel = "Effective since 3 Aug"
            ),
            device = null,
            relationship = IdentityRelationship.Unavailable,
            activeAlerts = 0,
            criticalAlerts = 0
        )
    )

    fun detailFor(identityId: String): IdentityDetailUiState {
        val identity = identities.firstOrNull { it.identityId.equals(identityId, ignoreCase = true) }
            ?: return IdentityDetailUiState.Unavailable

        // Authorization is stated separately from trust on purpose: a trusted
        // identity still does not authorize an operator action by itself.
        val authorization = AuthorizationSummary(
            operatorApprovalRequired = true,
            note = "Trust standing does not grant authorization. Actions against this identity still require operator approval and are authorized at execution."
        )

        val history = when (identity.identityId) {
            "TID-2B0C" -> listOf(
                identity.credential,
                CredentialSummary("CRED-6402", CredentialState.Superseded, "Superseded 6d ago"),
                CredentialSummary("CRED-6100", CredentialState.Revoked, "Revoked 21d ago", "Rotated after scheduled review.")
            )
            "TID-51AA" -> listOf(
                identity.credential,
                CredentialSummary("CRED-4290", CredentialState.Superseded, "Superseded 40d ago")
            )
            else -> listOf(identity.credential)
        }

        val alerts = when (identity.identityId) {
            "TID-88F1" -> listOf(
                IdentityAlertItem("ALRT-1092", "Suspicious Port Scan", "CRITICAL", "2m ago"),
                IdentityAlertItem("ALRT-1088", "Unusual outbound volume", "WARNING", "36m ago")
            )
            else -> emptyList()
        }

        val activity = when (identity.identityId) {
            "TID-88F1" -> listOf(
                ActivityEntry("I1", ActivityKind.TrustChanged, "Trust session renewed", identity.identityId, "41m ago", NexaStatus.Secure),
                ActivityEntry("I2", ActivityKind.AlertRaised, "Suspicious Port Scan", identity.identityId, "2m ago", NexaStatus.Critical)
            )
            "TID-77C4" -> listOf(
                ActivityEntry("I3", ActivityKind.ReverificationRequired, "Reverification required", identity.identityId, "5m ago", NexaStatus.Warning),
                ActivityEntry("I4", ActivityKind.DeviceAppeared, "Identity created", identity.identityId, "6m ago", NexaStatus.Information)
            )
            "TID-51AA" -> listOf(
                ActivityEntry("I5", ActivityKind.TrustChanged, "Identity revoked", identity.identityId, "2h ago", NexaStatus.Danger),
                ActivityEntry("I6", ActivityKind.TrustChanged, "Credential superseded", identity.identityId, "40d ago", NexaStatus.Information)
            )
            "TID-2B0C" -> listOf(
                ActivityEntry("I7", ActivityKind.ReverificationRequired, "Reverification required", identity.identityId, "20m ago", NexaStatus.Warning),
                ActivityEntry("I8", ActivityKind.TrustChanged, "Credential superseded", identity.identityId, "20m ago", NexaStatus.Information)
            )
            else -> listOf(
                ActivityEntry("I9", ActivityKind.TrustChanged, "Verification completed", identity.identityId, "8m ago", NexaStatus.Secure)
            )
        }

        return IdentityDetailUiState.Content(
            IdentityDetailData(
                identity = identity,
                authorization = authorization,
                credentialHistory = history,
                alerts = alerts,
                activity = activity,
                actions = availableIdentityActions(identity)
            )
        )
    }

    /** Resolves the identity bound to an observed device, if one exists. */
    fun identityForDevice(mac: String): IdentitySummary? =
        identities.firstOrNull { it.device?.mac.equals(mac, ignoreCase = true) }
}
