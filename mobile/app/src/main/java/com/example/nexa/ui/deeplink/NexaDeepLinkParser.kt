package com.example.nexa.ui.deeplink

/**
 * Turns an untrusted URI string into a validated [NexaDeepLink], or refuses it.
 *
 * Written against the raw string rather than `android.net.Uri` for two
 * reasons. It makes the whole validation surface unit-testable without a
 * device, and it means NEXA is not relying on a platform parser's tolerance
 * for malformed input to decide what a security link says.
 *
 * A link arriving here may have come from a notification, a browser, another
 * app, or an adb command typed by someone curious. None of that changes how
 * hard it is checked.
 */
object NexaDeepLinkParser {

    /** Longer than any legitimate NEXA link. */
    const val MAX_URI_LENGTH = 512
    const val MAX_IDENTIFIER_LENGTH = 64

    /**
     * Identifiers are conservative on purpose.
     *
     * Alphanumeric plus dash, underscore and dot. No slash (a path
     * separator), no colon (a scheme separator), no percent (an encoding
     * escape), no whitespace, no control characters. An identifier that needs
     * any of those is not an identifier NEXA issued.
     *
     * It must also *begin* with an alphanumeric, which is what refuses "..",
     * "." and anything else made only of punctuation. NEXA identifiers all
     * start with a letter or a digit, so the rule costs nothing and closes the
     * one shape that reads as a path element rather than a name.
     */
    private val IDENTIFIER =
        Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,${MAX_IDENTIFIER_LENGTH - 1}}$")

    /** An address is never an identity, so a link may not carry one as an id. */
    private val IPV4 = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
    private val MAC = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

    fun parse(raw: String?, defaultSource: DeepLinkSource = DeepLinkSource.External): DeepLinkParse {
        if (raw.isNullOrBlank()) return invalid(DeepLinkRejection.Empty, "no link supplied")
        if (raw.length > MAX_URI_LENGTH) return invalid(DeepLinkRejection.TooLong, "link too long")
        if (raw.any { it.isISOControl() }) {
            return invalid(DeepLinkRejection.MalformedUri, "link contains control characters")
        }

        // --- Scheme ---
        val schemeSeparator = "://"
        val separatorIndex = raw.indexOf(schemeSeparator)
        if (separatorIndex <= 0) {
            return invalid(DeepLinkRejection.MalformedUri, "link has no scheme")
        }
        val scheme = raw.substring(0, separatorIndex)
        if (!scheme.equals(NEXA_DEEP_LINK_SCHEME, ignoreCase = true)) {
            return invalid(DeepLinkRejection.UnsupportedScheme, "unsupported scheme")
        }

        val remainder = raw.substring(separatorIndex + schemeSeparator.length)
        if (remainder.isBlank()) {
            return invalid(DeepLinkRejection.MalformedUri, "link has no destination")
        }

        // --- Query, split off before the path is read ---
        val queryIndex = remainder.indexOf('?')
        val pathPart = if (queryIndex >= 0) remainder.substring(0, queryIndex) else remainder
        val queryPart = if (queryIndex >= 0) remainder.substring(queryIndex + 1) else ""

        // A fragment is meaningless to NEXA and is refused rather than
        // silently dropped, so a link cannot carry a hidden tail.
        if (pathPart.contains('#') || queryPart.contains('#')) {
            return invalid(DeepLinkRejection.MalformedUri, "link contains a fragment")
        }

        val segments = pathPart.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) {
            return invalid(DeepLinkRejection.MalformedUri, "link has no destination")
        }

        // --- Version, before anything else is interpreted ---
        val version = segments[0]
        if (version != NEXA_DEEP_LINK_VERSION) {
            return DeepLinkParse.UnsupportedVersion(version.take(MAX_IDENTIFIER_LENGTH))
        }

        val rest = segments.drop(1)
        if (rest.isEmpty()) {
            return invalid(DeepLinkRejection.UnknownDestination, "link names no destination")
        }
        if (rest.size > 2) {
            return invalid(DeepLinkRejection.MalformedUri, "link has too many segments")
        }

        val source = parseSource(queryPart, defaultSource)
        val destination = rest[0].lowercase()
        val identifier = rest.getOrNull(1)

        // --- Root contexts take no identifier ---
        if (destination in DeepLinkSegments.roots && identifier == null) {
            return DeepLinkParse.Accepted(rootLink(destination, source))
        }

        // --- Object contexts take exactly one ---
        if (destination !in DeepLinkSegments.objects) {
            return invalid(DeepLinkRejection.UnknownDestination, "unrecognised destination")
        }
        if (identifier == null) {
            return invalid(DeepLinkRejection.MissingIdentifier, "destination needs an identifier")
        }
        if (!isValidIdentifier(identifier)) {
            return invalid(DeepLinkRejection.InvalidIdentifier, "identifier failed validation")
        }

        return DeepLinkParse.Accepted(objectLink(destination, identifier, source))
    }

    /**
     * Whether a string may be used as a NEXA identifier in a link.
     *
     * Addresses are rejected explicitly rather than merely failing the pattern
     * by accident: a MAC would otherwise pass a looser rule, and neither a MAC
     * nor an IP is an identity.
     */
    fun isValidIdentifier(value: String): Boolean {
        if (!IDENTIFIER.matches(value)) return false
        if (IPV4.matches(value)) return false
        if (MAC.matches(value)) return false
        return true
    }

    private fun rootLink(destination: String, source: DeepLinkSource): NexaDeepLink =
        when (destination) {
            DeepLinkSegments.OVERVIEW -> NexaDeepLink.Overview(source)
            DeepLinkSegments.DEVICES -> NexaDeepLink.Devices(source)
            DeepLinkSegments.ALERTS -> NexaDeepLink.Alerts(source)
            DeepLinkSegments.AUDIT -> NexaDeepLink.Audit(source)
            else -> NexaDeepLink.Notifications(source)
        }

    private fun objectLink(
        destination: String,
        identifier: String,
        source: DeepLinkSource
    ): NexaDeepLink = when (destination) {
        DeepLinkSegments.ALERT -> NexaDeepLink.Alert(identifier, source)
        DeepLinkSegments.DEVICE -> NexaDeepLink.Device(identifier, source)
        DeepLinkSegments.IDENTITY -> NexaDeepLink.Identity(identifier, source)
        DeepLinkSegments.AUDIT_RECORD -> NexaDeepLink.AuditRecord(identifier, source)
        else -> NexaDeepLink.Notification(identifier, source)
    }

    /**
     * Reads the source hint.
     *
     * Unrecognised or absent values fall back to the caller's default rather
     * than failing the link: source is a navigation convenience, and nothing
     * about it is load-bearing. Any other query parameter is ignored — extra
     * parameters cannot influence a destination, so they cannot be a way in.
     */
    private fun parseSource(query: String, fallback: DeepLinkSource): DeepLinkSource {
        if (query.isBlank()) return fallback
        val value = query.split('&')
            .firstOrNull { it.startsWith("$SOURCE_PARAM=") }
            ?.substringAfter('=')
            ?: return fallback
        if (value.length > MAX_IDENTIFIER_LENGTH) return fallback
        return DeepLinkSource.fromWire(value)
    }

    private const val SOURCE_PARAM = "src"

    private fun invalid(reason: DeepLinkRejection, detail: String): DeepLinkParse.Invalid =
        DeepLinkParse.Invalid(reason, detail)
}

/** Why a link was refused. Recorded as a diagnostic, never shown raw to an operator. */
enum class DeepLinkRejection {
    Empty,
    TooLong,
    MalformedUri,
    UnsupportedScheme,
    UnknownDestination,
    MissingIdentifier,
    InvalidIdentifier
}

/**
 * The parser's verdict.
 *
 * [UnsupportedVersion] is separate from [Invalid] because they are different
 * facts: one is a link NEXA cannot read, the other is a link NEXA is too old
 * to read. Only the second is worth telling an operator to update for.
 */
sealed interface DeepLinkParse {
    data class Accepted(val link: NexaDeepLink) : DeepLinkParse

    data class UnsupportedVersion(val version: String) : DeepLinkParse

    /** [detail] names the rule that failed and never echoes the offending link. */
    data class Invalid(val reason: DeepLinkRejection, val detail: String) : DeepLinkParse
}
