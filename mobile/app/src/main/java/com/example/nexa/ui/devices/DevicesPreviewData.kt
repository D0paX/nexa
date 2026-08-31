package com.example.nexa.ui.devices

import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.common.ActivityEntry
import com.example.nexa.ui.common.ActivityKind
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.TrustState

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE.
 *
 * Fabricated inventory for development. Nothing here reaches Phase 1-4 and
 * no API is implied. The combinations are chosen to exercise the real
 * distinctions rather than to look tidy: present-but-unverified,
 * trusted-but-quarantined, revoked, reconciling, failed, stale and unknown
 * all appear, because those are the cases where a flattened "status" would
 * mislead an operator.
 */
object DevicesPreview {

    /** The scenario rendered during development. */
    val scenario: DevicesUiState get() = content()

    fun content(): DevicesUiState {
        val devices = inventory
        return DevicesUiState.Content(
            all = devices,
            visible = devices.resolve("", DeviceFilters(), DeviceSort.Attention),
            query = "",
            filters = DeviceFilters(),
            sort = DeviceSort.Attention,
            freshness = DataFreshness.Live,
            degraded = false
        )
    }

    fun empty(): DevicesUiState = DevicesUiState.Content(
        all = emptyList(),
        visible = emptyList(),
        query = "",
        filters = DeviceFilters(),
        sort = DeviceSort.Attention,
        freshness = DataFreshness.Live,
        degraded = false
    )

    fun stale(): DevicesUiState = (content() as DevicesUiState.Content)
        .copy(freshness = DataFreshness.Stale("Last confirmed 6 min ago"))

    fun degraded(): DevicesUiState = (content() as DevicesUiState.Content)
        .copy(degraded = true)

    fun offline(): DevicesUiState = DevicesUiState.Offline

    fun unavailable(): DevicesUiState = DevicesUiState.Unavailable

    val inventory: List<DeviceListItem> = listOf(
        DeviceListItem(
            id = "DEV-1001",
            label = "Corp Laptop - Engineering",
            mac = "00:1A:2B:3C:4D:5E",
            ip = "192.168.1.105",
            scope = "VLAN_SECURE",
            presence = Presence.Present,
            trust = TrustState.Trusted,
            identityId = "TID-88F1",
            enforcement = DeviceEnforcement.Quarantined,
            alerts = DeviceAlerts(total = 2, critical = 1, warning = 1),
            lastSeenLabel = "2m ago",
            freshness = DataFreshness.Live
        ),
        DeviceListItem(
            id = "DEV-1002",
            label = "Unknown Device",
            mac = "00:5E:4D:3C:2B:1A",
            ip = "192.168.1.140",
            scope = "VLAN_SECURE",
            presence = Presence.Present,
            trust = TrustState.Unverified,
            identityId = null,
            enforcement = DeviceEnforcement.Normal,
            alerts = DeviceAlerts(total = 1, critical = 0, warning = 1),
            lastSeenLabel = "14m ago",
            freshness = DataFreshness.Live
        ),
        DeviceListItem(
            id = "DEV-1003",
            label = "Build Server",
            mac = "3C:22:FB:19:04:A1",
            ip = "10.20.4.11",
            scope = "VLAN_BUILD",
            presence = Presence.Present,
            trust = TrustState.Trusted,
            identityId = "TID-2B0C",
            enforcement = DeviceEnforcement.Failed,
            alerts = DeviceAlerts(total = 0, critical = 0, warning = 0),
            lastSeenLabel = "1m ago",
            freshness = DataFreshness.Live
        ),
        DeviceListItem(
            id = "DEV-1004",
            label = "Reception Tablet",
            mac = "AA:BB:CC:DD:EE:FF",
            ip = "192.168.9.30",
            scope = "VLAN_GUEST",
            presence = Presence.Absent,
            trust = TrustState.Revoked,
            identityId = "TID-51AA",
            enforcement = DeviceEnforcement.Normal,
            alerts = DeviceAlerts(total = 0, critical = 0, warning = 0),
            lastSeenLabel = "3h ago",
            freshness = DataFreshness.Stale("Last seen 3h ago")
        ),
        DeviceListItem(
            id = "DEV-1005",
            label = "Lab Controller",
            mac = "9C:2F:1D:44:0B:77",
            ip = null,
            scope = "VLAN_LAB",
            presence = Presence.Unknown,
            trust = TrustState.Unknown,
            identityId = null,
            enforcement = DeviceEnforcement.Unknown,
            alerts = DeviceAlerts(total = 0, critical = 0, warning = 0),
            lastSeenLabel = "unknown",
            freshness = DataFreshness.Unknown
        ),
        DeviceListItem(
            id = "DEV-1006",
            label = "Ops Workstation",
            mac = "00:9F:2C:1D:4E:7B",
            ip = "10.20.4.52",
            scope = "VLAN_BUILD",
            presence = Presence.Present,
            trust = TrustState.Pending,
            identityId = "TID-77C4",
            enforcement = DeviceEnforcement.Reconciling,
            alerts = DeviceAlerts(total = 0, critical = 0, warning = 0),
            lastSeenLabel = "5m ago",
            freshness = DataFreshness.Live
        ),
        DeviceListItem(
            id = "DEV-1007",
            label = "Conference Display",
            mac = "48:E1:5C:90:2A:31",
            ip = "192.168.1.77",
            scope = "VLAN_SECURE",
            presence = Presence.Present,
            trust = TrustState.Trusted,
            identityId = "TID-9E12",
            enforcement = DeviceEnforcement.Normal,
            alerts = DeviceAlerts(total = 0, critical = 0, warning = 0),
            lastSeenLabel = "just now",
            freshness = DataFreshness.Live
        ),
        DeviceListItem(
            id = "DEV-1008",
            label = "Warehouse Scanner",
            mac = "7A:11:03:BC:5D:22",
            ip = "10.44.2.8",
            scope = "VLAN_GUEST",
            presence = Presence.Present,
            trust = TrustState.Unverified,
            identityId = null,
            enforcement = DeviceEnforcement.Paused,
            alerts = DeviceAlerts(total = 0, critical = 0, warning = 0),
            lastSeenLabel = "9m ago",
            freshness = DataFreshness.Live
        )
    )

    val scopes: List<String> = inventory.map { it.scope }.distinct().sorted()

    fun detailFor(mac: String): DeviceDetailUiState {
        val device = inventory.firstOrNull { it.mac.equals(mac, ignoreCase = true) }
            ?: return DeviceDetailUiState.Unavailable

        val record = DeviceRecordContext(
            presence = device.presence,
            scope = device.scope,
            ip = device.ip,
            mac = device.mac,
            lastObservedLabel = device.lastSeenLabel,
            freshness = device.freshness
        )

        val identity = device.identityId?.let { id ->
            TrustedIdentityContext(
                identityId = id,
                trust = device.trust,
                owner = when (device.id) {
                    "DEV-1001" -> "jsmith@example.com"
                    "DEV-1003" -> "ci-runner@example.com"
                    "DEV-1004" -> "reception@example.com"
                    "DEV-1006" -> "ops@example.com"
                    else -> null
                },
                verifiedLabel = when (device.trust) {
                    TrustState.Trusted -> "Verified 41m ago"
                    TrustState.Pending -> "Verification in progress"
                    TrustState.Revoked -> "Revoked 2h ago"
                    else -> "Not verified"
                },
                reverificationLabel = when (device.trust) {
                    TrustState.Trusted -> "Next reverification in 19m"
                    TrustState.Pending -> "Awaiting reverification"
                    else -> null
                }
            )
        }

        val enforcement = DeviceEnforcementContext(
            state = device.enforcement,
            detail = when (device.enforcement) {
                DeviceEnforcement.Normal -> "No enforcement binding is active for this device."
                DeviceEnforcement.Quarantined -> "An enforcement binding is active. Traffic is constrained to the remediation VLAN."
                DeviceEnforcement.Reconciling -> "Binding state is being reconciled after restart. Current firewall state is not confirmed."
                DeviceEnforcement.Failed -> "The last enforcement action did not complete. Target state is not confirmed."
                DeviceEnforcement.Paused -> "The circuit breaker is open. No enforcement action will execute."
                DeviceEnforcement.Unknown -> "Enforcement state for this device cannot be determined."
            },
            ownershipScope = if (device.enforcement == DeviceEnforcement.Quarantined ||
                device.enforcement == DeviceEnforcement.Reconciling
            ) device.scope else null,
            bindingLabel = when (device.enforcement) {
                DeviceEnforcement.Quarantined -> "BND-4471"
                DeviceEnforcement.Reconciling -> "BND-4390"
                else -> null
            },
            targetStale = device.freshness !is DataFreshness.Live
        )

        val alerts = when (device.id) {
            "DEV-1001" -> listOf(
                DeviceAlertItem("ALRT-1092", "Suspicious Port Scan", "CRITICAL", "2m ago"),
                DeviceAlertItem("ALRT-1088", "Unusual outbound volume", "WARNING", "36m ago")
            )
            "DEV-1002" -> listOf(
                DeviceAlertItem("ALRT-1091", "Untrusted MAC in Trusted VLAN", "WARNING", "14m ago")
            )
            else -> emptyList()
        }

        val activity = when (device.id) {
            "DEV-1001" -> listOf(
                ActivityEntry("A1", ActivityKind.AlertRaised, "Suspicious Port Scan", device.mac, "2m ago", NexaStatus.Critical),
                ActivityEntry("A2", ActivityKind.EnforcementCompleted, "Quarantine applied", device.mac, "2m ago", NexaStatus.Simulation),
                ActivityEntry("A3", ActivityKind.TrustChanged, "Trust session renewed", device.mac, "41m ago", NexaStatus.Secure)
            )
            "DEV-1003" -> listOf(
                ActivityEntry("B1", ActivityKind.ActionFailed, "Quarantine failed", device.mac, "8m ago", NexaStatus.Critical),
                ActivityEntry("B2", ActivityKind.EnforcementStarted, "Quarantine requested", device.mac, "9m ago", NexaStatus.Information)
            )
            "DEV-1006" -> listOf(
                ActivityEntry("C1", ActivityKind.ReverificationRequired, "Reverification required", device.mac, "5m ago", NexaStatus.Warning)
            )
            else -> listOf(
                ActivityEntry("D1", ActivityKind.DeviceAppeared, "Device observed", device.mac, device.lastSeenLabel, NexaStatus.Information)
            )
        }

        return DeviceDetailUiState.Content(
            DeviceDetailData(
                device = device,
                record = record,
                identity = identity,
                enforcement = enforcement,
                alerts = alerts,
                activity = activity,
                actions = availableActions(device)
            )
        )
    }
}
