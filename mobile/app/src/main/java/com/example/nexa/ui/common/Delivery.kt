package com.example.nexa.ui.common

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus

/**
 * Phase 3 notification delivery, shared by every feature that talks about it.
 *
 * Delivery is the transport lifecycle of a security message. It is not the
 * message, and it is emphatically not the incident the message was about:
 *
 *   SecurityEvent  is not an Alert
 *   Alert          is not a Notification
 *   Notification   is not its delivery
 *
 * An alert whose notification failed is still exactly as open, as severe and
 * as unhandled as it was a moment before. One definition lives here so the
 * alert surface and the notification center cannot drift into two different
 * ideas of what "failed" means.
 */

// ============================================================
// STATE
// ============================================================

enum class DeliveryState {
    /** Queued, not yet sent. */
    Pending,

    /** Handed to the channel; delivery not yet confirmed. */
    Sent,

    /** The channel confirmed delivery. Says nothing about it being read or acted on. */
    Delivered,

    /** A previous attempt failed and another is scheduled. */
    Retrying,

    /** An attempt failed. Further attempts may still be possible. */
    Failed,

    /** Retries are spent. No further attempt will be made. */
    Exhausted,

    /**
     * The delivery record could not be read.
     *
     * Not the same as "not delivered": NEXA does not know, and converting
     * that into a failure would be inventing a fact.
     */
    Unavailable;

    val isFailure: Boolean get() = this == Failed || this == Exhausted

    /** No further change is expected without a new send. */
    val isTerminal: Boolean get() = this == Delivered || this == Exhausted

    val isInFlight: Boolean get() = this == Pending || this == Sent || this == Retrying
}

// ============================================================
// ATTEMPTS
// ============================================================

/**
 * One delivery attempt.
 *
 * [attempt] is the attempt's own number from the delivery record, not its
 * position in a list — a list may be ordered newest-first, and an operator
 * counting rows would then read the attempts backwards.
 */
data class DeliveryAttempt(
    val attempt: Int,
    val channel: String,
    val state: DeliveryState,
    val timeLabel: String,
    val detail: String? = null
)

// ============================================================
// PRESENTATION
// ============================================================

val DeliveryState.label: String
    get() = when (this) {
        DeliveryState.Delivered -> "Delivered"
        DeliveryState.Sent -> "Sent"
        DeliveryState.Pending -> "Pending"
        DeliveryState.Retrying -> "Retrying"
        DeliveryState.Failed -> "Failed"
        DeliveryState.Exhausted -> "Exhausted"
        DeliveryState.Unavailable -> "Unavailable"
    }

/**
 * The tone delivery takes.
 *
 * Deliberately quieter than the severity scale an incident uses. A failed
 * notification is an operational problem with the transport, not a security
 * event, and dressing it in the same red as a critical alert would teach an
 * operator to read the two as equally urgent — which is exactly backwards,
 * because the incident is what is still happening.
 *
 * [DeliveryState.Exhausted] is the one that keeps danger: it is terminal, and
 * it means nobody was told.
 */
val DeliveryState.status: NexaStatus
    get() = when (this) {
        DeliveryState.Delivered -> NexaStatus.Secure
        DeliveryState.Sent -> NexaStatus.Information
        DeliveryState.Pending -> NexaStatus.Information
        DeliveryState.Retrying -> NexaStatus.Warning
        DeliveryState.Failed -> NexaStatus.Warning
        DeliveryState.Exhausted -> NexaStatus.Danger
        DeliveryState.Unavailable -> NexaStatus.Unknown
    }

/**
 * The shape of a delivery state.
 *
 * Each state has its own glyph rather than borrowing one that already means
 * something else in this product: a retry is not a trust reverification, and
 * a failed message is not a critical security event, however similar the two
 * pairs look at a glance.
 */
val DeliveryState.icon: ImageVector
    get() = when (this) {
        DeliveryState.Delivered -> NexaIcons.Delivered
        DeliveryState.Sent -> NexaIcons.NotificationDelivery
        DeliveryState.Pending -> NexaIcons.Pending
        DeliveryState.Retrying -> NexaIcons.Retry
        DeliveryState.Failed -> NexaIcons.DeliveryFailed
        DeliveryState.Exhausted -> NexaIcons.DeliveryExhausted
        DeliveryState.Unavailable -> NexaIcons.Unknown
    }
