package com.example.nexa.push

import com.example.nexa.ui.deeplink.DeepLinkSource
import com.example.nexa.ui.deeplink.NexaDeepLink
import com.example.nexa.ui.deeplink.NexaDeepLinkParser

/**
 * Turns a validated push payload into a NEXA deep link.
 *
 * The push layer stops here. It does not know what a screen is, does not
 * construct navigation, and has no way to express an action — it produces a
 * link, and the deep-link resolver decides everything after that. Adding a
 * destination to the app therefore never means touching the messaging
 * service.
 *
 * Nothing in the mapping can produce an execution, because [NexaDeepLink] has
 * no variant for one.
 */
fun deepLinkFor(payload: PushPayload): NexaDeepLink {
    val source = DeepLinkSource.Notification

    return when (payload.sourceType) {
        PushSourceType.Alert -> NexaDeepLink.Alert(payload.sourceId, source)

        PushSourceType.Identity -> NexaDeepLink.Identity(payload.sourceId, source)

        // A device is addressed by its record identifier. The link format
        // refuses an address, so a payload naming a device by IP or MAC gets
        // its delivery record instead of a device route — the record shows
        // what arrived without inventing a target.
        PushSourceType.Device ->
            if (NexaDeepLinkParser.isValidIdentifier(payload.sourceId)) {
                NexaDeepLink.Device(payload.sourceId, source)
            } else {
                NexaDeepLink.Notification(payload.notificationId, source)
            }

        // An action notification leads to its delivery record, which states
        // what was requested and links onward to the target. It does not lead
        // to the action flow: NEXA has no read-only action result surface yet,
        // and routing a message into the confirmation screen would put an
        // operator one tap from re-running what they were only reading about.
        PushSourceType.Action -> NexaDeepLink.Notification(payload.notificationId, source)

        PushSourceType.System -> NexaDeepLink.Notifications(source)
    }
}

/**
 * A MAC address, in the form the device inventory currently keys on.
 *
 * Used only to decide whether a payload's *target hint* is worth keeping as
 * an unresolved reference. It is never a routing identity: deep links address
 * devices by record id, and an address is an observation.
 */
private val MAC_ADDRESS = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

fun isRoutableMac(value: String): Boolean = MAC_ADDRESS.matches(value)
