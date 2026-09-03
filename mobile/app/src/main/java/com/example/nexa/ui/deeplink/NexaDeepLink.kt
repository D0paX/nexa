package com.example.nexa.ui.deeplink

/**
 * The NEXA deep-link contract.
 *
 * A deep link means one thing: **open this context**. It never means execute
 * an action, and the type system says so — there is no variant here that
 * corresponds to quarantining, releasing or reverifying anything. A link can
 * ask NEXA to show an operator a device; whether that device can be acted on
 * is decided afterwards, inside the app, by the same eligibility and
 * confirmation rules as always.
 *
 * The canonical form is:
 *
 *     nexa://v1/{destination}
 *     nexa://v1/{destination}/{identifier}
 *
 * Examples:
 *
 *     nexa://v1/overview
 *     nexa://v1/alert/ALRT-1092
 *     nexa://v1/device/DEV-1001
 *     nexa://v1/identity/TID-88F1
 *     nexa://v1/audit/EVT-4401
 *     nexa://v1/notification/NTF-7002
 *
 * The version is the first path element so it is the first thing validated:
 * a link from a future format is refused before any of its segments are
 * interpreted, because a segment named the same in two versions need not mean
 * the same thing.
 *
 * A later `https://` entry point can reuse everything below it — only the
 * parser needs to learn a second surface form.
 */

/** The only link version this build understands. */
const val NEXA_DEEP_LINK_VERSION = "v1"

const val NEXA_DEEP_LINK_SCHEME = "nexa"

/**
 * Where a link came from.
 *
 * Recorded because it is occasionally useful for navigation behaviour, and
 * deliberately inert for everything else: no security decision anywhere reads
 * this. A link from a notification is validated exactly as hard as a link
 * pasted from a browser.
 */
enum class DeepLinkSource {
    Notification,
    InApp,
    External;

    companion object {
        fun fromWire(value: String?): DeepLinkSource = when (value) {
            "push" -> Notification
            "app" -> InApp
            else -> External
        }
    }

    val wireValue: String
        get() = when (this) {
            Notification -> "push"
            InApp -> "app"
            External -> "external"
        }
}

/**
 * A validated link.
 *
 * Only constructible through [NexaDeepLinkParser] or by building one
 * internally from already-trusted values, so holding one means every
 * identifier in it passed validation.
 */
sealed interface NexaDeepLink {

    val source: DeepLinkSource

    // --- Root contexts ---

    data class Overview(override val source: DeepLinkSource = DeepLinkSource.External) : NexaDeepLink
    data class Devices(override val source: DeepLinkSource = DeepLinkSource.External) : NexaDeepLink
    data class Alerts(override val source: DeepLinkSource = DeepLinkSource.External) : NexaDeepLink
    data class Audit(override val source: DeepLinkSource = DeepLinkSource.External) : NexaDeepLink
    data class Notifications(override val source: DeepLinkSource = DeepLinkSource.External) :
        NexaDeepLink

    // --- Contexts about one object ---

    data class Alert(
        val alertId: String,
        override val source: DeepLinkSource = DeepLinkSource.External
    ) : NexaDeepLink

    /**
     * Addressed by the Phase 1 DeviceRecord identifier.
     *
     * Not a MAC and emphatically not an IP. An address is an observation, and
     * a link that addressed a device by one would be inviting exactly the
     * stale-identifier confusion Phase 4 exists to prevent. The resolver looks
     * the id up in the inventory and refuses the link when it finds nothing.
     */
    data class Device(
        val deviceId: String,
        override val source: DeepLinkSource = DeepLinkSource.External
    ) : NexaDeepLink

    data class Identity(
        val identityId: String,
        override val source: DeepLinkSource = DeepLinkSource.External
    ) : NexaDeepLink

    data class AuditRecord(
        val eventId: String,
        override val source: DeepLinkSource = DeepLinkSource.External
    ) : NexaDeepLink

    data class Notification(
        val deliveryId: String,
        override val source: DeepLinkSource = DeepLinkSource.External
    ) : NexaDeepLink
}

// ============================================================
// DESTINATION VOCABULARY
// ============================================================

/**
 * The path segments a link may name.
 *
 * A closed set. Anything else is refused rather than interpreted, which is
 * what stops a sender from steering NEXA anywhere its author did not intend.
 * Note what is absent: there is no segment for an action, a confirmation, an
 * execution or a firewall operation.
 */
internal object DeepLinkSegments {
    const val OVERVIEW = "overview"
    const val DEVICES = "devices"
    const val ALERTS = "alerts"
    const val AUDIT = "audit"
    const val NOTIFICATIONS = "notifications"

    const val ALERT = "alert"
    const val DEVICE = "device"
    const val IDENTITY = "identity"
    const val AUDIT_RECORD = "audit"
    const val NOTIFICATION = "notification"

    /** Roots take no identifier. */
    val roots = setOf(OVERVIEW, DEVICES, ALERTS, AUDIT, NOTIFICATIONS)

    /** These take exactly one. */
    val objects = setOf(ALERT, DEVICE, IDENTITY, AUDIT_RECORD, NOTIFICATION)
}

// ============================================================
// RENDERING
// ============================================================

/**
 * The canonical URI for a link.
 *
 * Used to hand a link through an Android intent, where it is re-parsed and
 * re-validated on arrival. Round-tripping through the same text form the
 * outside world uses means the internal path gets the same scrutiny as the
 * external one, rather than a trusted shortcut nobody tests.
 */
fun NexaDeepLink.toUri(): String {
    val base = "$NEXA_DEEP_LINK_SCHEME://$NEXA_DEEP_LINK_VERSION"
    val path = when (this) {
        is NexaDeepLink.Overview -> DeepLinkSegments.OVERVIEW
        is NexaDeepLink.Devices -> DeepLinkSegments.DEVICES
        is NexaDeepLink.Alerts -> DeepLinkSegments.ALERTS
        is NexaDeepLink.Audit -> DeepLinkSegments.AUDIT
        is NexaDeepLink.Notifications -> DeepLinkSegments.NOTIFICATIONS
        is NexaDeepLink.Alert -> "${DeepLinkSegments.ALERT}/$alertId"
        is NexaDeepLink.Device -> "${DeepLinkSegments.DEVICE}/$deviceId"
        is NexaDeepLink.Identity -> "${DeepLinkSegments.IDENTITY}/$identityId"
        is NexaDeepLink.AuditRecord -> "${DeepLinkSegments.AUDIT_RECORD}/$eventId"
        is NexaDeepLink.Notification -> "${DeepLinkSegments.NOTIFICATION}/$deliveryId"
    }
    return "$base/$path?src=${source.wireValue}"
}

/**
 * A link reduced to something safe to log.
 *
 * The destination kind and the version, never the identifier. Identifiers are
 * opaque, but they still name which incident or which device someone is
 * looking at, and that does not belong in a log line.
 */
fun NexaDeepLink.redacted(): String {
    val kind = when (this) {
        is NexaDeepLink.Overview -> DeepLinkSegments.OVERVIEW
        is NexaDeepLink.Devices -> DeepLinkSegments.DEVICES
        is NexaDeepLink.Alerts -> DeepLinkSegments.ALERTS
        is NexaDeepLink.Audit -> DeepLinkSegments.AUDIT
        is NexaDeepLink.Notifications -> DeepLinkSegments.NOTIFICATIONS
        is NexaDeepLink.Alert -> DeepLinkSegments.ALERT
        is NexaDeepLink.Device -> DeepLinkSegments.DEVICE
        is NexaDeepLink.Identity -> DeepLinkSegments.IDENTITY
        is NexaDeepLink.AuditRecord -> DeepLinkSegments.AUDIT_RECORD
        is NexaDeepLink.Notification -> DeepLinkSegments.NOTIFICATION
    }
    val identified = this !is NexaDeepLink.Overview && this !is NexaDeepLink.Devices &&
        this !is NexaDeepLink.Alerts && this !is NexaDeepLink.Audit &&
        this !is NexaDeepLink.Notifications
    return if (identified) {
        "$NEXA_DEEP_LINK_SCHEME://$NEXA_DEEP_LINK_VERSION/$kind/[id] (${source.wireValue})"
    } else {
        "$NEXA_DEEP_LINK_SCHEME://$NEXA_DEEP_LINK_VERSION/$kind (${source.wireValue})"
    }
}
