package com.example.nexa.ui.deeplink

/**
 * Decides what a validated link actually opens.
 *
 * Parsing proves a link is well-formed. Resolving asks the further questions:
 * may this operator open it, does the thing it names still exist, and what is
 * its current identifier. All three are answered against authoritative state,
 * never against the link — a link is a request, not a source of truth.
 *
 * Pure and free of Android and Compose types, so the whole decision surface
 * is unit tested.
 */
class NexaDeepLinkResolver(
    private val catalog: DeepLinkCatalog,
    private val accessPolicy: DeepLinkAccessPolicy = OpenAccessPolicy
) {

    fun resolve(raw: String?, defaultSource: DeepLinkSource = DeepLinkSource.External): DeepLinkResolution =
        when (val parsed = NexaDeepLinkParser.parse(raw, defaultSource)) {
            is DeepLinkParse.Invalid -> DeepLinkResolution.Invalid(parsed.reason, parsed.detail)
            is DeepLinkParse.UnsupportedVersion ->
                DeepLinkResolution.UnsupportedVersion(parsed.version)
            is DeepLinkParse.Accepted -> resolve(parsed.link)
        }

    fun resolve(link: NexaDeepLink): DeepLinkResolution {
        // Authorization first. A link that an operator may not open should not
        // even reveal whether the object behind it exists.
        when (val access = accessPolicy.canOpen(link)) {
            is DeepLinkAccess.Denied -> return DeepLinkResolution.Unauthorized(link, access.reason)
            DeepLinkAccess.SessionExpired -> return DeepLinkResolution.SessionExpired(link)
            DeepLinkAccess.Allowed -> Unit
        }

        return when (link) {
            // Root contexts always exist.
            is NexaDeepLink.Overview,
            is NexaDeepLink.Devices,
            is NexaDeepLink.Alerts,
            is NexaDeepLink.Audit,
            is NexaDeepLink.Notifications -> DeepLinkResolution.Resolved(link)

            is NexaDeepLink.Alert ->
                if (catalog.alertExists(link.alertId)) {
                    DeepLinkResolution.Resolved(link)
                } else {
                    DeepLinkResolution.ObjectUnavailable(link, DeepLinkObject.Alert)
                }

            // The device route is still keyed by MAC, so the resolver reads the
            // current one from the inventory. That lookup is also the existence
            // check: a device id nothing knows about produces no route at all
            // rather than a screen built around an identifier from the link.
            is NexaDeepLink.Device -> {
                val mac = catalog.currentMacForDevice(link.deviceId)
                if (mac == null) {
                    DeepLinkResolution.ObjectUnavailable(link, DeepLinkObject.Device)
                } else {
                    DeepLinkResolution.Resolved(link, resolvedDeviceMac = mac)
                }
            }

            is NexaDeepLink.Identity ->
                if (catalog.identityExists(link.identityId)) {
                    DeepLinkResolution.Resolved(link)
                } else {
                    DeepLinkResolution.ObjectUnavailable(link, DeepLinkObject.Identity)
                }

            is NexaDeepLink.AuditRecord ->
                if (catalog.auditEventExists(link.eventId)) {
                    DeepLinkResolution.Resolved(link)
                } else {
                    DeepLinkResolution.ObjectUnavailable(link, DeepLinkObject.AuditRecord)
                }

            is NexaDeepLink.Notification ->
                if (catalog.deliveryExists(link.deliveryId)) {
                    DeepLinkResolution.Resolved(link)
                } else {
                    DeepLinkResolution.ObjectUnavailable(link, DeepLinkObject.Notification)
                }
        }
    }
}

// ============================================================
// CATALOG
// ============================================================

/** What kind of thing a link named, for wording an unavailable result. */
enum class DeepLinkObject {
    Alert,
    Device,
    Identity,
    AuditRecord,
    Notification
}

/**
 * Existence, answered by the authoritative source.
 *
 * An interface so the resolver can be tested without preview data, and so a
 * real data layer replaces the preview implementation without the resolver
 * changing. Every method answers about *now*: a link is old, the objects it
 * names may be gone, and this is where that is discovered.
 */
interface DeepLinkCatalog {
    fun alertExists(alertId: String): Boolean

    /**
     * The device's current MAC, or null when the device is unknown.
     *
     * Deliberately a lookup rather than a boolean: the identifier in the link
     * is a stable device id, and the address it currently resolves to is read
     * fresh every time. An address that changed since the link was created
     * resolves to the new one, which is the entire reason a link does not
     * carry an address itself.
     */
    fun currentMacForDevice(deviceId: String): String?

    fun identityExists(identityId: String): Boolean
    fun auditEventExists(eventId: String): Boolean
    fun deliveryExists(deliveryId: String): Boolean
}

// ============================================================
// ACCESS
// ============================================================

/**
 * Whether the current operator may open a context.
 *
 * NEXA's Android prototype has no authentication layer, so the shipped policy
 * allows every context and the app relies on each screen's own rules — a deep
 * link has never been able to bypass those, because it only ever chooses
 * which screen opens.
 *
 * The abstraction exists now so that adding sessions later is a matter of
 * supplying a different policy, rather than retrofitting a check into a
 * navigation path that never had one. No credentials are invented here.
 */
interface DeepLinkAccessPolicy {
    fun canOpen(link: NexaDeepLink): DeepLinkAccess
}

sealed interface DeepLinkAccess {
    data object Allowed : DeepLinkAccess
    data class Denied(val reason: String) : DeepLinkAccess

    /** A session existed and has lapsed. Distinct from never having had one. */
    data object SessionExpired : DeepLinkAccess
}

/** The policy used while no authentication layer exists. */
object OpenAccessPolicy : DeepLinkAccessPolicy {
    override fun canOpen(link: NexaDeepLink): DeepLinkAccess = DeepLinkAccess.Allowed
}

// ============================================================
// RESOLUTION
// ============================================================

/**
 * What happened to a link.
 *
 * The distinction between [Invalid] and [ObjectUnavailable] is deliberate and
 * user-visible: a link NEXA cannot read is a different problem from a link
 * NEXA read perfectly well that points at something no longer there. Telling
 * an operator "invalid link" when the truth is "that alert was resolved and
 * archived" would send them looking for the wrong fault.
 */
sealed interface DeepLinkResolution {

    /**
     * [resolvedDeviceMac] carries the address read from the inventory for a
     * device link, because the device route is still MAC-keyed. It is a
     * routing detail resolved from authoritative state, never a value the
     * link supplied.
     */
    data class Resolved(
        val link: NexaDeepLink,
        val resolvedDeviceMac: String? = null
    ) : DeepLinkResolution

    data class Invalid(val reason: DeepLinkRejection, val detail: String) : DeepLinkResolution

    data class UnsupportedVersion(val version: String) : DeepLinkResolution

    data class ObjectUnavailable(
        val link: NexaDeepLink,
        val obj: DeepLinkObject
    ) : DeepLinkResolution

    data class Unauthorized(val link: NexaDeepLink, val reason: String) : DeepLinkResolution

    data class SessionExpired(val link: NexaDeepLink) : DeepLinkResolution
}

// ============================================================
// OPERATOR-FACING WORDING
// ============================================================

/**
 * What an operator is told.
 *
 * Never the parser's reasoning. "Link has too many segments" tells someone
 * looking at a broken notification nothing they can act on, and tells someone
 * probing the app something they should not have.
 */
fun DeepLinkResolution.operatorMessage(): String = when (this) {
    is DeepLinkResolution.Resolved -> ""
    is DeepLinkResolution.Invalid -> "This NEXA link is not valid."
    is DeepLinkResolution.UnsupportedVersion ->
        "This NEXA link was created for a newer version of the app."
    is DeepLinkResolution.ObjectUnavailable -> when (obj) {
        DeepLinkObject.Alert -> "This alert is no longer available."
        DeepLinkObject.Device -> "This device is no longer available."
        DeepLinkObject.Identity -> "This identity is no longer available."
        DeepLinkObject.AuditRecord -> "This security record is no longer available."
        DeepLinkObject.Notification -> "This delivery record is no longer available."
    }
    is DeepLinkResolution.Unauthorized -> "You do not have access to this security context."
    is DeepLinkResolution.SessionExpired -> "Your session has expired. Sign in and try again."
}

fun DeepLinkResolution.operatorTitle(): String = when (this) {
    is DeepLinkResolution.Resolved -> ""
    is DeepLinkResolution.Invalid -> "Link not valid"
    is DeepLinkResolution.UnsupportedVersion -> "Link not supported"
    is DeepLinkResolution.ObjectUnavailable -> "No longer available"
    is DeepLinkResolution.Unauthorized -> "Access denied"
    is DeepLinkResolution.SessionExpired -> "Session expired"
}
