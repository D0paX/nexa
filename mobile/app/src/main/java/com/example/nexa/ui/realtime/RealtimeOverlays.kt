package com.example.nexa.ui.realtime

import com.example.nexa.ui.alerts.AlertListItem
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.availabilityOf
import com.example.nexa.ui.enforcement.ActionContext
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

/**
 * A prepared action context, re-derived against the newest live state.
 *
 * A confirmation screen can sit open for as long as an operator takes to read
 * it, and everything it describes can move underneath it: the device goes
 * absent, trust is withdrawn, the breaker opens, enforcement changes because
 * somebody else acted first. Executing against the copy captured when the
 * screen opened would be executing against a screenshot.
 *
 * So the context is rebuilt from the overlays before it is evaluated, and
 * only the fields the stream actually reports are replaced. What the stream
 * has said nothing about keeps the value the preparation established — an
 * event is a change, not a replacement.
 *
 * Two things are deliberately *not* re-derived here:
 *
 *  - Authorization. It is not carried on the realtime stream at all, and
 *    inferring it from trust or presence is the exact mistake the model
 *    exists to prevent. It is re-checked authoritatively at execution.
 *  - Execution mode. The prepared mode is what the operator was shown and
 *    agreed to; a mode arriving later belongs to a reported *action*, and is
 *    applied there rather than being folded back into the request.
 */
fun ActionContext.withLiveTarget(state: RealtimeState): ActionContext {
    val device = state.devices[target.deviceId]
    val identity = target.identityId?.let { state.identities[it] }
    if (device == null && identity == null && state.circuitBreaker == null) return this

    val refreshedTarget = target.copy(
        presence = device?.presence ?: target.presence,
        ip = device?.observedAddress ?: target.ip,
        lastObservedLabel = device?.lastSeenLabel ?: target.lastObservedLabel,
        // A live observation is, by definition, current. Absence of one leaves
        // the freshness the preparation recorded, however old that was.
        observationFreshness = if (device?.lastSeenLabel != null) {
            DataFreshness.Live
        } else {
            target.observationFreshness
        },
        // Trust travels on its own events and is never inferred from the
        // device having been seen.
        trust = identity?.trust ?: target.trust
    )

    return copy(
        target = refreshedTarget,
        currentEnforcement = device?.enforcement ?: currentEnforcement,
        circuitBreaker = state.circuitBreaker ?: circuitBreaker,
        dataAvailability = availabilityOf(refreshedTarget.observationFreshness)
    )
}
