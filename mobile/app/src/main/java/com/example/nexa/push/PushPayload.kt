package com.example.nexa.push

import com.example.nexa.ui.common.ExecutionMode

/**
 * The wire contract for a NEXA push message.
 *
 * A push is a *delivery mechanism*. It is not authorization, not identity,
 * not trust, not execution and not proof that anything succeeded. Everything
 * in this file exists to keep that true even when the message on the wire is
 * malformed, out of date, or hostile.
 *
 * Two rules shape the model:
 *
 *  1. The payload carries *identifiers and hints*, never state NEXA will act
 *     on. Authoritative state is read from the system after the operator
 *     opens the app.
 *  2. The payload cannot name where to go. A destination is derived from a
 *     validated [sourceType] and a validated identifier — never from a route,
 *     URL or command string supplied by the sender.
 */

/** The only schema this build understands. An older or newer one is refused. */
const val SUPPORTED_PUSH_SCHEMA_VERSION = 1

/** What the message was about. Fixed vocabulary; anything else is rejected. */
enum class PushSourceType {
    Alert,
    Device,
    Identity,
    Action,
    System;

    companion object {
        /** Exact, case-sensitive match. No fuzzy parsing of a security enum. */
        fun fromWire(value: String?): PushSourceType? = when (value) {
            "ALERT" -> Alert
            "DEVICE" -> Device
            "IDENTITY" -> Identity
            "ACTION" -> Action
            "SYSTEM" -> System
            else -> null
        }
    }
}

enum class PushSeverity {
    Critical,
    Warning,
    Information;

    companion object {
        fun fromWire(value: String?): PushSeverity? = when (value) {
            "CRITICAL" -> Critical
            "WARNING" -> Warning
            "INFORMATION" -> Information
            else -> null
        }
    }
}

/** Execution mode on the wire, mapped onto the shared Phase 4 vocabulary. */
fun executionModeFromWire(value: String?): ExecutionMode? = when (value) {
    "ENFORCE" -> ExecutionMode.Enforce
    "AUDIT_ONLY" -> ExecutionMode.AuditOnly
    "UNKNOWN" -> ExecutionMode.Unknown
    else -> null
}

enum class PushTargetKind {
    Device,
    Identity;

    companion object {
        fun fromWire(value: String?): PushTargetKind? = when (value) {
            "DEVICE" -> Device
            "IDENTITY" -> Identity
            else -> null
        }
    }
}

/**
 * A hint about what the message concerned.
 *
 * Explicitly a hint. It is never treated as evidence that the target is still
 * present, still quarantined, still trusted, or still anything at all.
 */
data class PushTargetRef(
    val kind: PushTargetKind,
    val id: String
)

/**
 * A validated push message.
 *
 * Construction of this type is only possible through [PushPayloadParser], so
 * holding one is a guarantee that every field passed validation.
 */
data class PushPayload(
    val schemaVersion: Int,
    /** Stable delivery identifier. The deduplication key and the routing key. */
    val notificationId: String,
    val sourceType: PushSourceType,
    val sourceId: String,
    val title: String,
    val body: String,
    val severity: PushSeverity,
    val createdAtEpochSeconds: Long,
    /** Present only for execution messages. Null is not "live". */
    val executionMode: ExecutionMode? = null,
    val targetRef: PushTargetRef? = null
) {
    val isSimulated: Boolean get() = executionMode == ExecutionMode.AuditOnly
    val isLiveEnforcement: Boolean get() = executionMode == ExecutionMode.Enforce
}

// ============================================================
// PARSE RESULT
// ============================================================

/** Why a message was refused. Recorded as a safe diagnostic, never rendered raw. */
enum class PushRejectionReason {
    EmptyPayload,
    MissingSchemaVersion,
    MalformedSchemaVersion,
    UnsupportedSchemaVersion,
    MissingField,
    InvalidIdentifier,
    InvalidEnum,
    InvalidTimestamp,
    FieldTooLong
}

sealed interface PushParseResult {
    data class Accepted(val payload: PushPayload) : PushParseResult

    /**
     * [detail] names the field and the rule it broke. It never echoes the
     * offending value: a rejected payload is untrusted input, and repeating it
     * into a log or a diagnostic surface is how untrusted input travels.
     */
    data class Rejected(
        val reason: PushRejectionReason,
        val detail: String
    ) : PushParseResult
}
