package com.example.nexa.ui.notifications

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus
import com.example.nexa.ui.alerts.label
import com.example.nexa.ui.alerts.status
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.common.icon
import com.example.nexa.ui.common.label
import com.example.nexa.ui.common.status
import com.example.nexa.ui.enforcement.label

/**
 * How notification delivery is presented.
 *
 * Split down the middle, deliberately:
 *
 *   the delivery half reads only [NotificationDeliverySummary]
 *   the source half reads only [NotificationSource]
 *
 * Neither can see the other's data, so no amount of later editing can make a
 * failed delivery colour an alert badge or a delivered notification claim an
 * action succeeded. The only function that sees both is
 * [deliveryImpactStatement], whose entire job is to say that one did not
 * change the other.
 */

// ============================================================
// DELIVERY — reads delivery only
// ============================================================

/**
 * The row's primary statement.
 *
 * The delivery outcome, never the subject of the message. This screen exists
 * to answer whether the message arrived; leading with the alert's title would
 * put an incident headline next to a FAILED badge, which is precisely the
 * misreading the whole feature is built to prevent.
 */
fun deliveryHeadline(delivery: NotificationDeliverySummary): String = when (delivery.state) {
    DeliveryState.Pending -> "Queued for delivery"
    DeliveryState.Sent -> "Sent, not yet confirmed"
    DeliveryState.Delivered -> "Delivered"
    DeliveryState.Retrying -> retryingHeadline(delivery)
    DeliveryState.Failed -> "Delivery failed"
    DeliveryState.Exhausted -> "Delivery exhausted"
    DeliveryState.Unavailable -> "Delivery state unavailable"
}

private fun retryingHeadline(delivery: NotificationDeliverySummary): String {
    val max = delivery.maxAttempts
    return if (max != null) {
        "Retrying — attempt ${delivery.attemptCount} of $max"
    } else {
        "Retrying — attempt ${delivery.attemptCount}"
    }
}

/**
 * What the delivery state means.
 *
 * Every sentence names the notification explicitly, so none of them can be
 * read as a statement about the incident. Failure states carry the recorded
 * reason where there is one — "something went wrong" tells an operator
 * nothing and invites them to guess.
 */
fun deliveryExplanation(delivery: NotificationDeliverySummary): String {
    val base = when (delivery.state) {
        DeliveryState.Pending ->
            "This notification has not been sent yet. It is queued on the delivery channel."
        DeliveryState.Sent ->
            "The notification was handed to the channel. Delivery has not been confirmed."
        DeliveryState.Delivered ->
            "The channel confirmed delivery of this notification. Confirmation covers delivery only — it does not mean the message was read or acted on."
        DeliveryState.Retrying ->
            "A delivery attempt failed and another is scheduled. The notification has not been delivered yet."
        DeliveryState.Failed ->
            "This notification did not reach its destination."
        DeliveryState.Exhausted ->
            "Delivery attempts are exhausted. No further attempt will be made, and this notification was never delivered."
        DeliveryState.Unavailable ->
            "NEXA cannot read the delivery record for this notification. Whether it was delivered is unknown — this is not a report that it failed."
    }
    val reason = delivery.failureReason
    val withReason =
        if (reason != null && delivery.state.isFailure) "$base Reported reason: $reason" else base

    // Arrival on this handset is worth stating, and worth keeping separate
    // from the notification service confirming delivery.
    return if (delivery.receivedOnThisDevice) {
        "$withReason This message arrived on this device, which is not the same as the notification service confirming delivery."
    } else {
        withReason
    }
}

val NotificationDeliverySummary.status: NexaStatus get() = state.status

val NotificationDeliverySummary.icon: ImageVector get() = state.icon

val NotificationDeliverySummary.stateLabel: String get() = state.label

/**
 * The retry line, shown only when the backend supplied a schedule.
 *
 * NEXA does not run its own timer and present the result as though it knew
 * when the next attempt is due.
 */
fun retryLine(delivery: NotificationDeliverySummary): String? = when {
    delivery.nextRetryLabel != null -> "Next attempt ${delivery.nextRetryLabel}"
    delivery.state == DeliveryState.Retrying -> "A further attempt is scheduled. Timing is not reported."
    else -> null
}

/** Attempt counters, when the record has them. */
fun attemptLine(delivery: NotificationDeliverySummary): String {
    val max = delivery.maxAttempts
    return if (max != null) {
        "${delivery.attemptCount} of $max attempt(s)"
    } else {
        "${delivery.attemptCount} attempt(s)"
    }
}

/**
 * Material weight.
 *
 * Most delivery is quiet. Only the states an operator has to do something
 * about earn a denser surface — and even those stay a surface, not a red
 * panel: the incident is what deserves alarm, not its transport.
 */
fun deliverySurfaceFor(record: NotificationRecord): GlassVariant =
    when (record.delivery.state) {
        DeliveryState.Exhausted, DeliveryState.Failed -> GlassVariant.Strong
        else -> GlassVariant.Standard
    }

// ============================================================
// SOURCE — reads source only
// ============================================================

/** What kind of message this was. Derived from the source, so it cannot disagree. */
fun notificationTypeLabel(source: NotificationSource): String = when (source) {
    is NotificationSource.Alert -> "Alert notification"
    is NotificationSource.Action -> "Action notification"
    is NotificationSource.Trust -> "Trust notification"
    is NotificationSource.SecurityEvent -> "Security event notification"
    NotificationSource.Unknown -> "Notification"
}

val NotificationSourceType.label: String
    get() = when (this) {
        NotificationSourceType.Alert -> "Alert"
        NotificationSourceType.Action -> "Action"
        NotificationSourceType.Trust -> "Trust"
        NotificationSourceType.SecurityEvent -> "Security event"
        NotificationSourceType.Unknown -> "Unknown"
    }

val NotificationSource.icon: ImageVector
    get() = when (this) {
        is NotificationSource.Alert -> NexaIcons.Alerts
        is NotificationSource.Action -> NexaIcons.Enforcing
        is NotificationSource.Trust -> NexaIcons.Identity
        is NotificationSource.SecurityEvent -> NexaIcons.Information
        NotificationSource.Unknown -> NexaIcons.Unknown
    }

/** The provenance line: "SOURCE: ALERT · ALRT-1089". */
fun sourceProvenance(source: NotificationSource): String {
    val kind = source.type.label.uppercase()
    val id = source.identifier ?: return "SOURCE: $kind"
    return "SOURCE: $kind · $id"
}

/**
 * The subject's own state, as recorded at send time.
 *
 * Reads the source and nothing else, so the words here are identical whatever
 * happened to the delivery. An action that is reconciling says so however
 * successfully its notification arrived.
 */
fun sourceStateSummary(source: NotificationSource): String? = when (source) {
    is NotificationSource.Alert ->
        "${severityWord(source)} · ${source.lifecycle?.label?.uppercase() ?: STATE_NOT_READ}"
    // The execution state only. The action code is long, appears in the
    // detail fields and in the subject, and squeezes the row that has to
    // state the delivery outcome.
    is NotificationSource.Action -> source.executionState?.label?.uppercase() ?: STATE_NOT_READ
    is NotificationSource.Trust -> "TRUST ${source.trust?.let(::trustWord) ?: STATE_NOT_READ}"
    is NotificationSource.SecurityEvent -> null
    NotificationSource.Unknown -> null
}

private fun severityWord(source: NotificationSource.Alert): String =
    source.severity.label.uppercase()

/**
 * What the interface says when it has an identifier but no state.
 *
 * Not "unknown", which in this product is a state the system reports. This is
 * NEXA saying it has not looked yet.
 */
const val STATE_NOT_READ = "STATE NOT READ"

private fun trustWord(trust: TrustState): String = when (trust) {
    TrustState.Trusted -> "TRUSTED"
    TrustState.Pending -> "PENDING"
    TrustState.Revoked -> "REVOKED"
    TrustState.Unverified -> "UNVERIFIED"
    TrustState.Unknown -> "UNKNOWN"
}

/** The subject's own tone. Never influenced by how its notification fared. */
fun sourceStatus(source: NotificationSource): NexaStatus? = when (source) {
    is NotificationSource.Alert -> source.severity.status
    is NotificationSource.Action -> NexaStatus.Information
    is NotificationSource.Trust -> NexaStatus.Information
    is NotificationSource.SecurityEvent -> NexaStatus.Information
    NotificationSource.Unknown -> null
}

// ============================================================
// THE BRIDGE — the only place both halves are read
// ============================================================

/**
 * States, in as many words, what the delivery outcome did *not* do.
 *
 * This is the sentence the whole checkpoint turns on. An operator looking at
 * a critical alert whose notification failed must come away understanding
 * two separate facts — the incident is critical and open, and separately, a
 * message about it did not arrive — and never the merged, wrong one.
 */
fun deliveryImpactStatement(record: NotificationRecord): String {
    val delivery = record.delivery
    return when (val source = record.source) {
        is NotificationSource.Alert -> alertImpact(source, delivery.state)
        is NotificationSource.Action -> actionImpact(source, delivery.state)
        is NotificationSource.Trust -> trustImpact(source, delivery.state)
        is NotificationSource.SecurityEvent ->
            "The security event is recorded regardless of what happened to this notification."
        NotificationSource.Unknown ->
            "NEXA cannot resolve what this notification was about. The delivery outcome describes the message only."
    }
}

private fun alertImpact(source: NotificationSource.Alert, state: DeliveryState): String {
    val severity = source.severity.label.uppercase()

    // No lifecycle read means no claim about the incident, in either
    // direction. The operator is pointed at where the real answer lives.
    val lifecycle = source.lifecycle?.label?.uppercase()
        ?: return "Alert ${source.alertId} was reported as $severity by the notification. Its current state has not been read from the alert service. Open the alert to see where the incident actually stands."

    return when {
        state.isFailure ->
            "Alert ${source.alertId} remains $lifecycle and $severity. A delivery failure does not change an incident's state or its severity, and does not reopen or close it."
        state == DeliveryState.Delivered ->
            "Alert ${source.alertId} remains $lifecycle. Delivery confirms the message reached its channel — it does not mean the alert has been read, acknowledged or resolved."
        state == DeliveryState.Unavailable ->
            "Alert ${source.alertId} remains $lifecycle and $severity. Not being able to read the delivery record changes nothing about the incident."
        else ->
            "Alert ${source.alertId} remains $lifecycle and $severity. The incident's state is independent of this notification."
    }
}

private fun actionImpact(source: NotificationSource.Action, state: DeliveryState): String {
    val simulated = source.executionMode == ExecutionMode.AuditOnly
    val execution = source.executionState?.label?.uppercase()
        ?: return actionImpactWithoutState(source, simulated)
    val simulationNote = if (simulated) {
        " This action ran in AUDIT_ONLY: no firewall mutation occurred."
    } else {
        ""
    }
    return when {
        state == DeliveryState.Delivered ->
            "The notification was delivered. The action ${source.actionId} is $execution — delivering a message about an action is not evidence of its outcome.$simulationNote"
        state.isFailure ->
            "The notification did not arrive. The action ${source.actionId} is $execution and its outcome is unchanged by the delivery failure.$simulationNote"
        else ->
            "The action ${source.actionId} is $execution. Its outcome is determined by the enforcement pipeline, not by this notification.$simulationNote"
    }
}

/**
 * An action whose execution state has not been read.
 *
 * The mode is known, because it travelled with the message. The outcome is
 * the enforcement pipeline to report, and nothing has reported it yet.
 */
private fun actionImpactWithoutState(
    source: NotificationSource.Action,
    simulated: Boolean
): String {
    val simulationNote = if (simulated) {
        " This action ran in AUDIT_ONLY: no firewall mutation occurred."
    } else {
        ""
    }
    return "The outcome of action ${source.actionId} has not been read from the enforcement pipeline. This record reports that a message was sent, which is not a statement about what the action did.$simulationNote"
}

private fun trustImpact(source: NotificationSource.Trust, state: DeliveryState): String {
    val trust = source.trust?.let(::trustWord)
        ?: return "The trust standing of identity ${source.identityId} has not been read. Notification delivery neither verifies an identity nor changes its standing, so nothing here says what that standing is."
    return when {
        state == DeliveryState.Delivered ->
            "The notification was delivered. Identity ${source.identityId} is $trust — delivering a message about an identity does not verify it or change its trust standing."
        state.isFailure ->
            "The notification did not arrive. Identity ${source.identityId} is $trust and its trust standing is unchanged."
        else ->
            "Identity ${source.identityId} is $trust. Trust standing is decided by verification, not by notification delivery."
    }
}

// ============================================================
// ROW COMPOSITION
// ============================================================

/** The row subtitle: what the message was and who it concerned. */
fun notificationSubtitle(record: NotificationRecord): String {
    val type = notificationTypeLabel(record.source)
    val target = record.target.displayLabel
    return if (target != null) "$type · $target" else type
}

// ============================================================
// FIELDS
// ============================================================

/** The delivery record's own fields. Nothing about the subject appears here. */
fun notificationDeliveryFields(record: NotificationRecord): List<NotificationField> {
    val delivery = record.delivery
    val fields = mutableListOf<NotificationField>()

    fields += NotificationField("DELIVERY ID", delivery.deliveryId, technical = true)
    fields += NotificationField("CHANNEL", delivery.channel.label)
    fields += NotificationField("STATE", delivery.stateLabel)
    fields += NotificationField("ATTEMPTS", attemptLine(delivery))
    fields += NotificationField("CREATED", delivery.createdLabel)
    fields += NotificationField("LAST ATTEMPT", delivery.lastAttemptLabel)
    delivery.nextRetryLabel?.let { fields += NotificationField("NEXT RETRY", it) }
    delivery.failureReason?.let { fields += NotificationField("REPORTED REASON", it) }

    return fields
}

/** The subject's fields, kept in their own block and clearly labelled as a snapshot. */
fun notificationSourceFields(record: NotificationRecord): List<NotificationField> {
    val fields = mutableListOf<NotificationField>()

    when (val source = record.source) {
        is NotificationSource.Alert -> {
            fields += NotificationField("ALERT", source.alertId, technical = true)
            fields += NotificationField("TITLE", source.title)
            fields += NotificationField("SEVERITY", source.severity.label)
            fields += NotificationField("ALERT STATE", source.lifecycle?.label ?: "Not read")
        }
        is NotificationSource.Action -> {
            fields += NotificationField("ACTION", source.actionId, technical = true)
            fields += NotificationField("ACTION CODE", source.actionCode, technical = true)
            fields += NotificationField(
                "EXECUTION STATE",
                source.executionState?.label ?: "Not read"
            )
            fields += NotificationField(
                "EXECUTION MODE",
                when (source.executionMode) {
                    ExecutionMode.AuditOnly -> "AUDIT_ONLY (simulated)"
                    ExecutionMode.Enforce -> "ENFORCE (live)"
                    ExecutionMode.Unknown -> "UNKNOWN"
                }
            )
        }
        is NotificationSource.Trust -> {
            fields += NotificationField("IDENTITY", source.identityId, technical = true)
            fields += NotificationField("SUBJECT", source.label)
            fields += NotificationField(
                "TRUST STANDING",
                source.trust?.let(::trustWord) ?: "Not read"
            )
        }
        is NotificationSource.SecurityEvent -> {
            fields += NotificationField("EVENT", source.eventId, technical = true)
            fields += NotificationField("SUMMARY", source.summary)
        }
        NotificationSource.Unknown -> Unit
    }

    when (val target = record.target) {
        is NotificationTarget.Device -> {
            fields += NotificationField("DEVICE", target.label)
            fields += NotificationField("MAC", target.mac, technical = true)
            // Labelled as observation so it is never read as the target's identity.
            target.ip?.let { fields += NotificationField("OBSERVED ADDRESS", it, technical = true) }
            fields += NotificationField("SCOPE", target.scope, technical = true)
        }
        is NotificationTarget.Identity -> {
            fields += NotificationField("IDENTITY", target.identityId, technical = true)
            target.scope?.let { fields += NotificationField("SCOPE", it, technical = true) }
        }
        // Only what the reference actually carried. No label, no scope, no
        // freshness: none of it was read, and filling it in would present a
        // target assembled from a message as one observed on the network.
        is NotificationTarget.UnresolvedDevice ->
            fields += NotificationField("MAC", target.mac, technical = true)
        is NotificationTarget.UnresolvedIdentity ->
            fields += NotificationField("IDENTITY", target.identityId, technical = true)
        NotificationTarget.None -> Unit
    }

    return fields
}

/**
 * Where a delivery record can take the operator.
 *
 * To the authoritative surface for the thing the message was about — never to
 * an execution. A delivery record is not a control, and responding to an
 * incident starts from the incident.
 */
fun notificationLinks(record: NotificationRecord): List<NotificationLink> {
    val links = mutableListOf<NotificationLink>()
    when (val source = record.source) {
        is NotificationSource.Alert -> links += NotificationLink.Alert(source.alertId)
        is NotificationSource.Trust -> links += NotificationLink.Identity(source.identityId)
        else -> Unit
    }
    when (val target = record.target) {
        is NotificationTarget.Device -> links += NotificationLink.Device(target.mac)
        is NotificationTarget.UnresolvedDevice -> links += NotificationLink.Device(target.mac)
        is NotificationTarget.UnresolvedIdentity ->
            if (links.none { it is NotificationLink.Identity }) {
                links += NotificationLink.Identity(target.identityId)
            }
        is NotificationTarget.Identity ->
            if (links.none { it is NotificationLink.Identity }) {
                links += NotificationLink.Identity(target.identityId)
            }
        NotificationTarget.None -> Unit
    }
    return links
}

val NotificationLink.label: String
    get() = when (this) {
        is NotificationLink.Alert -> "View alert"
        is NotificationLink.Device -> "View observed device"
        is NotificationLink.Identity -> "View identity"
    }

val NotificationLink.icon: ImageVector
    get() = when (this) {
        is NotificationLink.Alert -> NexaIcons.Alerts
        is NotificationLink.Device -> NexaIcons.Devices
        is NotificationLink.Identity -> NexaIcons.Identity
    }
