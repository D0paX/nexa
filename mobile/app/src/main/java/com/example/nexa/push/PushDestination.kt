package com.example.nexa.push

/**
 * Where a notification tap is allowed to go.
 *
 * A closed set. A push cannot name a destination — there is no route, URL or
 * command field on the wire, and this type is the only vocabulary the tap
 * handler understands. A sender can therefore influence *which* of these the
 * operator lands on, and nothing else.
 *
 * Nothing in this set executes anything. There is deliberately no
 * ActionConfirmation member: that screen submits enforcement requests, and a
 * notification must never be one tap away from one. Responding to an incident
 * starts from the incident, through the confirmation flow the operator
 * reaches deliberately.
 */
sealed interface PushDestination {
    data class Alert(val alertId: String) : PushDestination
    data class Device(val mac: String) : PushDestination
    data class Identity(val identityId: String) : PushDestination
    data class DeliveryRecord(val deliveryId: String) : PushDestination
    data object Center : PushDestination
}

/**
 * A MAC address, in the only form the device inventory routes by.
 *
 * An address is not an identity. A device push carrying an IP where a MAC
 * belongs is refused a device route rather than being resolved into one:
 * reconstructing a target from an address is the stale-IP mistake Phase 4
 * exists to prevent, and it would be no better for happening in a tap
 * handler.
 */
private val MAC_ADDRESS = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")

fun isRoutableMac(value: String): Boolean = MAC_ADDRESS.matches(value)

/**
 * Resolves a validated payload to a destination.
 *
 * Derived entirely from [PushPayload.sourceType] and validated identifiers.
 * When a source type cannot be routed safely, the operator lands on the
 * Notification Center — the delivery record is always available, always
 * honest about what is known, and links onward to whatever authoritative
 * surface does exist.
 */
fun destinationFor(payload: PushPayload): PushDestination = when (payload.sourceType) {
    PushSourceType.Alert -> PushDestination.Alert(payload.sourceId)

    PushSourceType.Device ->
        if (isRoutableMac(payload.sourceId)) {
            PushDestination.Device(payload.sourceId)
        } else {
            // Not a MAC, so not a device route. The delivery record still
            // shows what arrived, without inventing a target.
            PushDestination.DeliveryRecord(payload.notificationId)
        }

    PushSourceType.Identity -> PushDestination.Identity(payload.sourceId)

    // An action notification leads to its delivery record, which states the
    // action and its recorded execution state and links on to the target. It
    // does not lead to the action flow: NEXA has no read-only action result
    // surface yet, and routing history into the confirmation screen would put
    // an operator one tap from re-running what they were only reading about.
    PushSourceType.Action -> PushDestination.DeliveryRecord(payload.notificationId)

    PushSourceType.System -> PushDestination.Center
}
