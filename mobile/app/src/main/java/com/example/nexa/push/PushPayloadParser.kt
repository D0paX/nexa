package com.example.nexa.push

/**
 * Turns an untrusted map of strings into a validated [PushPayload], or
 * refuses it.
 *
 * Everything arriving here is hostile until proved otherwise. It came over a
 * network, from a sender this client cannot authenticate, into a security
 * tool. So the parser is total: every field is checked, every enum is matched
 * exactly, every string is bounded, and nothing is inferred from a value that
 * failed a check.
 *
 * Pure and free of Android types, so the whole validation surface is unit
 * tested without a device.
 */
object PushPayloadParser {

    // --- Wire keys ---
    const val KEY_SCHEMA_VERSION = "schemaVersion"
    const val KEY_NOTIFICATION_ID = "notificationId"
    const val KEY_SOURCE_TYPE = "sourceType"
    const val KEY_SOURCE_ID = "sourceId"
    const val KEY_TITLE = "title"
    const val KEY_BODY = "body"
    const val KEY_SEVERITY = "severity"
    const val KEY_CREATED_AT = "createdAt"
    const val KEY_EXECUTION_MODE = "executionMode"
    const val KEY_TARGET_KIND = "targetKind"
    const val KEY_TARGET_ID = "targetId"

    // --- Bounds ---
    const val MAX_IDENTIFIER_LENGTH = 64
    const val MAX_TITLE_LENGTH = 120
    const val MAX_BODY_LENGTH = 400

    /**
     * Identifiers are conservative on purpose.
     *
     * No spaces, no punctuation beyond dash, underscore, dot and colon — colon
     * so a MAC address passes. Nothing that could be read as a path, a scheme
     * or a separator by anything downstream.
     */
    private val IDENTIFIER = Regex("^[A-Za-z0-9._:-]{1,$MAX_IDENTIFIER_LENGTH}$")

    /** Sanity window for a timestamp: 2020-01-01 to 2100-01-01. */
    private const val MIN_EPOCH_SECONDS = 1_577_836_800L
    private const val MAX_EPOCH_SECONDS = 4_102_444_800L

    fun parse(data: Map<String, String>): PushParseResult {
        if (data.isEmpty()) {
            return reject(PushRejectionReason.EmptyPayload, "payload carried no data")
        }

        // Schema version first. An unrecognised schema is refused before any
        // other field is read: a field named the same in two schema versions
        // does not have to mean the same thing.
        val rawVersion = data[KEY_SCHEMA_VERSION]
            ?: return reject(PushRejectionReason.MissingSchemaVersion, KEY_SCHEMA_VERSION)
        val version = rawVersion.toIntOrNull()
            ?: return reject(
                PushRejectionReason.MalformedSchemaVersion,
                "$KEY_SCHEMA_VERSION is not an integer"
            )
        if (version != SUPPORTED_PUSH_SCHEMA_VERSION) {
            return reject(
                PushRejectionReason.UnsupportedSchemaVersion,
                "schema $version is not supported by this build"
            )
        }

        val notificationId = data[KEY_NOTIFICATION_ID]
            ?: return reject(PushRejectionReason.MissingField, KEY_NOTIFICATION_ID)
        if (!IDENTIFIER.matches(notificationId)) {
            return reject(PushRejectionReason.InvalidIdentifier, KEY_NOTIFICATION_ID)
        }

        val sourceType = PushSourceType.fromWire(data[KEY_SOURCE_TYPE])
            ?: return reject(PushRejectionReason.InvalidEnum, KEY_SOURCE_TYPE)

        val sourceId = data[KEY_SOURCE_ID]
            ?: return reject(PushRejectionReason.MissingField, KEY_SOURCE_ID)
        if (!IDENTIFIER.matches(sourceId)) {
            return reject(PushRejectionReason.InvalidIdentifier, KEY_SOURCE_ID)
        }

        val severity = PushSeverity.fromWire(data[KEY_SEVERITY])
            ?: return reject(PushRejectionReason.InvalidEnum, KEY_SEVERITY)

        val rawCreatedAt = data[KEY_CREATED_AT]
            ?: return reject(PushRejectionReason.MissingField, KEY_CREATED_AT)
        val createdAt = rawCreatedAt.toLongOrNull()
            ?: return reject(
                PushRejectionReason.InvalidTimestamp,
                "$KEY_CREATED_AT is not an integer"
            )
        if (createdAt !in MIN_EPOCH_SECONDS..MAX_EPOCH_SECONDS) {
            return reject(
                PushRejectionReason.InvalidTimestamp,
                "$KEY_CREATED_AT outside the plausible range"
            )
        }

        val rawTitle = data[KEY_TITLE] ?: return reject(PushRejectionReason.MissingField, KEY_TITLE)
        val rawBody = data[KEY_BODY] ?: return reject(PushRejectionReason.MissingField, KEY_BODY)

        // Length is checked before sanitising, so an oversized field is refused
        // rather than silently truncated into something that reads like a
        // different, shorter message.
        if (rawTitle.length > MAX_TITLE_LENGTH) {
            return reject(PushRejectionReason.FieldTooLong, KEY_TITLE)
        }
        if (rawBody.length > MAX_BODY_LENGTH) {
            return reject(PushRejectionReason.FieldTooLong, KEY_BODY)
        }

        val title = sanitizeText(rawTitle)
        val body = sanitizeText(rawBody)
        if (title.isEmpty()) return reject(PushRejectionReason.MissingField, KEY_TITLE)
        if (body.isEmpty()) return reject(PushRejectionReason.MissingField, KEY_BODY)

        // Execution mode is optional, but a present-and-unrecognised value is an
        // error rather than a null: silently dropping it would turn a mode NEXA
        // could not read into "not an execution at all".
        val rawMode = data[KEY_EXECUTION_MODE]
        val executionMode = if (rawMode == null) {
            null
        } else {
            executionModeFromWire(rawMode)
                ?: return reject(PushRejectionReason.InvalidEnum, KEY_EXECUTION_MODE)
        }

        val targetRef = when (val parsed = parseTarget(data)) {
            is TargetParse.Invalid -> return reject(parsed.reason, parsed.detail)
            is TargetParse.Absent -> null
            is TargetParse.Present -> parsed.ref
        }

        return PushParseResult.Accepted(
            PushPayload(
                schemaVersion = version,
                notificationId = notificationId,
                sourceType = sourceType,
                sourceId = sourceId,
                title = title,
                body = body,
                severity = severity,
                createdAtEpochSeconds = createdAt,
                executionMode = executionMode,
                targetRef = targetRef
            )
        )
    }

    private sealed interface TargetParse {
        data object Absent : TargetParse
        data class Present(val ref: PushTargetRef) : TargetParse
        data class Invalid(val reason: PushRejectionReason, val detail: String) : TargetParse
    }

    private fun parseTarget(data: Map<String, String>): TargetParse {
        val rawKind = data[KEY_TARGET_KIND]
        val rawId = data[KEY_TARGET_ID]
        if (rawKind == null && rawId == null) return TargetParse.Absent

        // Half a target is not a target. Accepting one would leave NEXA with an
        // identifier whose kind it has to guess.
        val kind = PushTargetKind.fromWire(rawKind)
            ?: return TargetParse.Invalid(PushRejectionReason.InvalidEnum, KEY_TARGET_KIND)
        if (rawId == null || !IDENTIFIER.matches(rawId)) {
            return TargetParse.Invalid(PushRejectionReason.InvalidIdentifier, KEY_TARGET_ID)
        }
        return TargetParse.Present(PushTargetRef(kind, rawId))
    }

    /**
     * Strips what a sender could use to forge structure inside a rendered
     * string: control characters, newlines, and runs of whitespace that let a
     * body fake a second line or a system-looking prefix.
     */
    fun sanitizeText(raw: String): String =
        raw.map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(WHITESPACE_RUN, " ")
            .trim()

    private val WHITESPACE_RUN = Regex("\\s+")

    private fun reject(reason: PushRejectionReason, detail: String): PushParseResult.Rejected =
        PushParseResult.Rejected(reason, detail)
}
