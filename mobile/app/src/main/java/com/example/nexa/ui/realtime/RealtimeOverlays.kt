package com.example.nexa.ui.realtime

import com.example.nexa.ui.alerts.AlertListItem
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.devices.DeviceListItem
import com.example.nexa.ui.identity.IdentitySummary
import com.example.nexa.ui.notifications.NotificationRecord

/**
 * Applying the overlay to a snapshot.
 *
 * Pure functions, one per domain, used by every view model that shows live
 * data. Keeping them here rather than inside each view model is what makes
 * "Devices and Overview agree about this device" a property of the code
 * rather than a coincidence.
 *
 * Every function matches on the **stable identifier** of the object. None of
 * them matches on an address, a label or a position, so a device that changed
 * address is still the same device and no update can land on the wrong one.
 */

/**
 * Devices, with observation and enforcement brought up to date.
 *
 * Only the fields the stream actually reports are replaced. The rest of the
 * record stays as the snapshot had it: an event is a change, not a
 * replacement, and overwriting untouched fields with defaults would be
 * inventing state.
 */
@JvmName("devicesWithRealtime")
fun List<DeviceListItem>.withRealtime(state: RealtimeState): List<DeviceListItem> {
    if (state.devices.isEmpty()) return this
    return map { device ->
        val overlay = state.devices[device.id] ?: return@map device
        device.copy(
            presence = overlay.presence ?: device.presence,
            enforcement = overlay.enforcement ?: device.enforcement,
            // Observation context, kept beside the record. The device is still
            // identified by its id.
            ip = overlay.observedAddress ?: device.ip,
            lastSeenLabel = overlay.lastSeenLabel ?: device.lastSeenLabel,
            // A live observation is, by definition, current.
            freshness = if (overlay.lastSeenLabel != null) DataFreshness.Live else device.freshness,
            // Trust travels on its own events and is applied below, never
            // inferred from a device having been seen.
            trust = device.identityId?.let { state.identities[it]?.trust } ?: device.trust
        )
    }
}

/**
 * Alerts, with lifecycle brought up to date.
 *
 * Delivery is applied from the delivery map, separately, because they are
 * separate facts — and because reading one from the other is exactly the
 * confusion the product spends most of its wording preventing.
 */
@JvmName("alertsWithRealtime")
fun List<AlertListItem>.withRealtime(state: RealtimeState): List<AlertListItem> {
    if (state.alerts.isEmpty()) return this
    return map { alert ->
        val overlay = state.alerts[alert.id] ?: return@map alert
        alert.copy(lifecycle = overlay.lifecycle)
    }
}

/**
 * Delivery records, with transport state brought up to date.
 *
 * Writes the delivery half of the record and nothing else. The source
 * snapshot — which alert, which action, which identity the message was about
 * — is left exactly as it was.
 */
@JvmName("deliveriesWithRealtime")
fun List<NotificationRecord>.withRealtime(state: RealtimeState): List<NotificationRecord> {
    if (state.deliveries.isEmpty()) return this
    return map { record ->
        val overlay = state.deliveries[record.id] ?: return@map record
        record.copy(
            delivery = record.delivery.copy(
                state = overlay.state,
                attemptCount = overlay.attemptCount,
                failureReason = overlay.failureReason ?: record.delivery.failureReason
            )
        )
    }
}

/**
 * Identities, with trust standing brought up to date.
 *
 * Trust only. Nothing here touches what an operator may do with the identity;
 * that is decided by the authorization engine at request time, and a stream
 * of trust changes has never been able to speak for it.
 */
@JvmName("identitiesWithRealtime")
fun List<IdentitySummary>.withRealtime(state: RealtimeState): List<IdentitySummary> {
    if (state.identities.isEmpty()) return this
    return map { identity ->
        val overlay = state.identities[identity.identityId] ?: return@map identity
        identity.copy(trust = overlay.trust)
    }
}
