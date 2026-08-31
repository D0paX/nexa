package com.example.nexa.push

import android.content.Intent
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.IdentityDetail
import com.example.nexa.NotificationCenter
import com.example.nexa.NotificationDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries a tapped notification into the navigation host.
 *
 * A tap means "take me to the relevant context". It never means "perform this
 * command", and there is no path from here to an execution: the destinations
 * are a closed set, and none of them submits anything.
 *
 * 5.18 replaces the intent encoding below with real deep links. The seam it
 * has to keep is [PushNavigation.request] and the [PushDestination] to
 * [NavKey] mapping, both of which are independent of how the tap arrived.
 */
object PushNavigation {

    private val _pending = MutableStateFlow<PushDestination?>(null)
    val pending: StateFlow<PushDestination?> = _pending.asStateFlow()

    fun request(destination: PushDestination) {
        _pending.value = destination
    }

    /** Called once the host has navigated, so a rotation does not re-navigate. */
    fun consume() {
        _pending.value = null
    }
}

// ============================================================
// INTENT ENCODING
// ============================================================

/**
 * The intent extras a notification carries.
 *
 * Two short strings: a destination *kind* from a closed vocabulary, and an
 * identifier. Never a route, a URI or anything else that could be followed
 * without being understood first.
 */
object PushIntentKeys {
    const val EXTRA_DESTINATION = "com.example.nexa.push.DESTINATION"
    const val EXTRA_ID = "com.example.nexa.push.DESTINATION_ID"

    const val DESTINATION_ALERT = "ALERT"
    const val DESTINATION_DEVICE = "DEVICE"
    const val DESTINATION_IDENTITY = "IDENTITY"
    const val DESTINATION_DELIVERY = "DELIVERY"
    const val DESTINATION_CENTER = "CENTER"
}

fun Intent.putPushDestination(destination: PushDestination): Intent {
    when (destination) {
        is PushDestination.Alert -> {
            putExtra(PushIntentKeys.EXTRA_DESTINATION, PushIntentKeys.DESTINATION_ALERT)
            putExtra(PushIntentKeys.EXTRA_ID, destination.alertId)
        }
        is PushDestination.Device -> {
            putExtra(PushIntentKeys.EXTRA_DESTINATION, PushIntentKeys.DESTINATION_DEVICE)
            putExtra(PushIntentKeys.EXTRA_ID, destination.mac)
        }
        is PushDestination.Identity -> {
            putExtra(PushIntentKeys.EXTRA_DESTINATION, PushIntentKeys.DESTINATION_IDENTITY)
            putExtra(PushIntentKeys.EXTRA_ID, destination.identityId)
        }
        is PushDestination.DeliveryRecord -> {
            putExtra(PushIntentKeys.EXTRA_DESTINATION, PushIntentKeys.DESTINATION_DELIVERY)
            putExtra(PushIntentKeys.EXTRA_ID, destination.deliveryId)
        }
        PushDestination.Center ->
            putExtra(PushIntentKeys.EXTRA_DESTINATION, PushIntentKeys.DESTINATION_CENTER)
    }
    return this
}

/**
 * Reads a destination back out of an intent.
 *
 * Re-validates. An intent can be forged by any app on the device that knows
 * the extra names, so what comes back out is treated exactly like what came
 * in off the network: unknown kind, missing id, or an identifier that fails
 * the same checks the parser applies, and the result is null.
 */
fun pushDestinationFromExtras(kind: String?, id: String?): PushDestination? {
    if (kind == PushIntentKeys.DESTINATION_CENTER) return PushDestination.Center
    if (kind == null || id == null) return null
    if (!isSafeRoutingIdentifier(id)) return null

    return when (kind) {
        PushIntentKeys.DESTINATION_ALERT -> PushDestination.Alert(id)
        PushIntentKeys.DESTINATION_DEVICE ->
            // Same rule as the payload: a device route needs a MAC, and an
            // address is not a target.
            if (isRoutableMac(id)) PushDestination.Device(id) else null
        PushIntentKeys.DESTINATION_IDENTITY -> PushDestination.Identity(id)
        PushIntentKeys.DESTINATION_DELIVERY -> PushDestination.DeliveryRecord(id)
        else -> null
    }
}

private val SAFE_IDENTIFIER = Regex("^[A-Za-z0-9._:-]{1,64}$")

fun isSafeRoutingIdentifier(value: String): Boolean = SAFE_IDENTIFIER.matches(value)

// ============================================================
// DESTINATION MAPPING
// ============================================================

/**
 * The only mapping from a push destination to a screen.
 *
 * Exhaustive over a closed set, and every arm lands on a surface that reads
 * state. None of them submits an action.
 */
fun PushDestination.toNavKey(): NavKey = when (this) {
    is PushDestination.Alert -> AlertDetail(alertId)
    is PushDestination.Device -> DeviceDetail(mac)
    is PushDestination.Identity -> IdentityDetail(identityId)
    is PushDestination.DeliveryRecord -> NotificationDetail(deliveryId)
    PushDestination.Center -> NotificationCenter
}
