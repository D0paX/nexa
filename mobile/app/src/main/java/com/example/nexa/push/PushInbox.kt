package com.example.nexa.push

import com.example.nexa.ui.alerts.AlertSeverity
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.notifications.NotificationChannel
import com.example.nexa.ui.notifications.NotificationDeliverySummary
import com.example.nexa.ui.notifications.NotificationRecord
import com.example.nexa.ui.notifications.NotificationSource
import com.example.nexa.ui.notifications.NotificationTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where arriving push messages land.
 *
 * The single seam between the transport and the read model. The messaging
 * service knows how to receive; the Notification Center knows how to present;
 * neither knows about the other. A realtime source added later pushes through
 * the same door without either side changing.
 *
 * In memory and process-scoped. Records created here describe messages that
 * reached *this* handset, and the authoritative delivery history lives on the
 * backend — persisting a partial local copy would create a second, quietly
 * disagreeing account of what was delivered. The limitation is real and
 * documented: records seen here do not survive the process.
 */
object PushInbox {

    private val _records = MutableStateFlow<List<NotificationRecord>>(emptyList())
    val records: StateFlow<List<NotificationRecord>> = _records.asStateFlow()

    private val _rejections = MutableStateFlow<List<PushRejectionDiagnostic>>(emptyList())

    /**
     * Refused messages, kept as safe diagnostics.
     *
     * Reason and field name only. The offending payload is never retained: it
     * failed validation, which is precisely when its contents are least
     * trustworthy and most likely to be an attempt at something.
     */
    val rejections: StateFlow<List<PushRejectionDiagnostic>> = _rejections.asStateFlow()

    /**
     * Records an arrived, validated message.
     *
     * Returns false when the notification id has already been seen, which is
     * the deduplication FCM makes necessary: the same message can legitimately
     * arrive twice, and an operator counting delivery records should not see
     * one incident twice because the transport retried.
     */
    fun onIncomingPush(payload: PushPayload): Boolean {
        val existing = _records.value
        if (existing.any { it.id == payload.notificationId }) return false
        _records.value = (listOf(recordFor(payload)) + existing).take(MAX_RECORDS)
        return true
    }

    fun onRejectedPush(rejection: PushParseResult.Rejected) {
        _rejections.value = (
            listOf(PushRejectionDiagnostic(rejection.reason, rejection.detail)) + _rejections.value
            ).take(MAX_REJECTIONS)
    }

    /** Test and process-restart hygiene. */
    fun clear() {
        _records.value = emptyList()
        _rejections.value = emptyList()
    }

    /**
     * How many delivery records the in-memory inbox keeps.
     *
     * It kept every message for the life of the process, which grew without
     * limit and made the deduplication scan linear in everything ever
     * received — quadratic across a long session.
     *
     * Bounding it is safe because this is not the record of what happened.
     * The audit trail is; this is the convenience surface that shows what
     * arrived on this device, it already does not survive a process restart,
     * and the Notification Center pages twenty-five at a time. The limit is
     * generous against both.
     *
     * The one consequence worth stating: deduplication can only recognise a
     * repeat while the original is still retained. That covers the case it
     * exists for — a transport retrying the same message — and does not cover
     * the same id arriving again after two hundred others, which is not a
     * retry.
     */
    private const val MAX_RECORDS = 200

    private const val MAX_REJECTIONS = 20

    /**
     * Turns a validated message into a delivery record.
     *
     * Two decisions matter here, and both are refusals to claim more than is
     * known:
     *
     *  - The delivery state is [DeliveryState.Unavailable]. A message reaching
     *    this handset is not the notification service reporting DELIVERED;
     *    that is a backend fact this client has not read. The record says the
     *    message arrived here and that the authoritative state is unknown,
     *    which is the whole truth available at this moment.
     *
     *  - The source carries identifiers and the severity the sender claimed,
     *    and no lifecycle, execution state or trust standing at all. Those are
     *    read from their own services. A push cannot tell NEXA that an alert
     *    is resolved or that an action succeeded.
     */
    internal fun recordFor(payload: PushPayload): NotificationRecord = NotificationRecord(
        id = payload.notificationId,
        subject = payload.title,
        delivery = NotificationDeliverySummary(
            deliveryId = payload.notificationId,
            state = DeliveryState.Unavailable,
            channel = NotificationChannel.Push,
            attemptCount = 0,
            maxAttempts = null,
            createdLabel = "Received on this device",
            lastAttemptLabel = "Received on this device",
            receivedOnThisDevice = true,
            ageMinutes = 0
        ),
        source = sourceFor(payload),
        target = targetFor(payload)
    )

    private fun sourceFor(payload: PushPayload): NotificationSource = when (payload.sourceType) {
        PushSourceType.Alert -> NotificationSource.Alert(
            alertId = payload.sourceId,
            title = payload.title,
            severity = severityFor(payload.severity),
            // Not read. The alert service owns this.
            lifecycle = null
        )

        PushSourceType.Action -> NotificationSource.Action(
            actionId = payload.sourceId,
            actionCode = payload.title,
            // Not read. The enforcement pipeline owns this.
            executionState = null,
            // The mode did travel with the message, and losing it would make a
            // simulation indistinguishable from a live run.
            executionMode = payload.executionMode ?: com.example.nexa.ui.common.ExecutionMode.Unknown
        )

        PushSourceType.Identity -> NotificationSource.Trust(
            identityId = payload.sourceId,
            label = payload.title,
            // Not read. Trust is established by verification, never by a message.
            trust = null
        )

        PushSourceType.Device,
        PushSourceType.System -> NotificationSource.SecurityEvent(
            eventId = payload.sourceId,
            summary = payload.title
        )
    }

    private fun severityFor(severity: PushSeverity): AlertSeverity = when (severity) {
        PushSeverity.Critical -> AlertSeverity.Critical
        PushSeverity.Warning -> AlertSeverity.Warning
        PushSeverity.Information -> AlertSeverity.Information
    }

    /**
     * The target, as an unresolved reference.
     *
     * Only the identifier the message carried. A device reference that is not
     * a MAC gets no target at all rather than one built around an address.
     */
    private fun targetFor(payload: PushPayload): NotificationTarget {
        val ref = payload.targetRef ?: return NotificationTarget.None
        return when (ref.kind) {
            PushTargetKind.Device ->
                if (isRoutableMac(ref.id)) {
                    NotificationTarget.UnresolvedDevice(ref.id)
                } else {
                    NotificationTarget.None
                }
            PushTargetKind.Identity -> NotificationTarget.UnresolvedIdentity(ref.id)
        }
    }
}

/** A refused message, reduced to what is safe to keep. */
data class PushRejectionDiagnostic(
    val reason: PushRejectionReason,
    val detail: String
)
