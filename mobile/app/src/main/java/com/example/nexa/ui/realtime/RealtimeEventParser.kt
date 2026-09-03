package com.example.nexa.ui.realtime

import com.example.nexa.ui.alerts.AlertLifecycle
import com.example.nexa.ui.common.CircuitBreakerState
import com.example.nexa.ui.common.DeliveryState
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.common.TrustState
import com.example.nexa.ui.devices.DeviceEnforcement
import com.example.nexa.ui.devices.Presence
import com.example.nexa.ui.enforcement.ExecutionState

/**
 * Turns an untrusted frame into a validated [RealtimeEvent], or refuses it.
 *
 * Total by construction: every field is checked, every enum matched exactly,
 * every string bounded, and nothing inferred from a value that failed a
 * check. A malformed frame produces a rejection, never a partially-applied
 * state and never an exception.
 *
 * Pure and free of Android types, so the whole validation surface is unit
 * tested.
 */
object RealtimeEventParser {

    // --- Wire keys ---
    const val KEY_SCHEMA_VERSION = "schemaVersion"
    const val KEY_EVENT_ID = "eventId"
    const val KEY_SEQUENCE = "sequence"
    const val KEY_TYPE = "eventType"
    const val KEY_OCCURRED_AT = "occurredAt"
    const val KEY_SCOPE = "scope"
    const val KEY_SUBJECT_ID = "subjectId"

    // --- Payload keys ---
    const val KEY_PRESENCE = "presence"
    const val KEY_OBSERVED_ADDRESS = "observedAddress"
    const val KEY_LAST_SEEN = "lastSeen"
    const val KEY_ENFORCEMENT = "enforcement"
    const val KEY_TRUST = "trust"
    const val KEY_LIFECYCLE = "lifecycle"
    const val KEY_DELIVERY_STATE = "deliveryState"
    const val KEY_ATTEMPT_COUNT = "attemptCount"
    const val KEY_FAILURE_REASON = "failureReason"
    const val KEY_EXECUTION_STATE = "executionState"
    const val KEY_EXECUTION_MODE = "executionMode"
    const val KEY_RECONCILED = "reconciled"
    const val KEY_ACTION_CODE = "actionCode"
    const val KEY_BREAKER = "circuitBreaker"

    // --- Bounds ---
    const val MAX_IDENTIFIER_LENGTH = 64
    const val MAX_TEXT_LENGTH = 200
    const val MAX_ATTEMPTS = 1_000

    /** Identifiers must begin alphanumeric, so a bare ".." is not a name. */
    private val IDENTIFIER =
        Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,${MAX_IDENTIFIER_LENGTH - 1}}$")

    /** Scopes are NetworkScope names: uppercase, underscore-separated. */
    private val SCOPE = Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,${MAX_IDENTIFIER_LENGTH - 1}}$")

    private const val MIN_EPOCH_SECONDS = 1_577_836_800L
    private const val MAX_EPOCH_SECONDS = 4_102_444_800L

    fun parse(frame: Map<String, String>): RealtimeParseResult {
        if (frame.isEmpty()) return reject(RealtimeRejection.EmptyFrame, "frame carried no fields")

        // Schema version before anything else. A field named the same in two
        // versions need not mean the same thing, so an unknown version is
        // refused rather than read with the old parser.
        val rawVersion = frame[KEY_SCHEMA_VERSION]
            ?: return reject(RealtimeRejection.MissingSchemaVersion, KEY_SCHEMA_VERSION)
        val version = rawVersion.toIntOrNull()
            ?: return reject(RealtimeRejection.MalformedSchemaVersion, KEY_SCHEMA_VERSION)
        if (version != REALTIME_SCHEMA_VERSION) {
            return RealtimeParseResult.UnsupportedVersion(version)
        }

        val eventId = frame[KEY_EVENT_ID]
            ?: return reject(RealtimeRejection.MissingField, KEY_EVENT_ID)
        if (!IDENTIFIER.matches(eventId)) {
            return reject(RealtimeRejection.InvalidIdentifier, KEY_EVENT_ID)
        }

        val sequence = frame[KEY_SEQUENCE]?.toLongOrNull()
            ?: return reject(RealtimeRejection.InvalidSequence, KEY_SEQUENCE)
        if (sequence < 0) return reject(RealtimeRejection.InvalidSequence, KEY_SEQUENCE)

        val type = RealtimeEventType.fromWire(frame[KEY_TYPE])
            ?: return reject(RealtimeRejection.UnknownEventType, KEY_TYPE)

        val occurredAt = frame[KEY_OCCURRED_AT]?.toLongOrNull()
            ?: return reject(RealtimeRejection.InvalidTimestamp, KEY_OCCURRED_AT)
        if (occurredAt !in MIN_EPOCH_SECONDS..MAX_EPOCH_SECONDS) {
            return reject(RealtimeRejection.InvalidTimestamp, "$KEY_OCCURRED_AT out of range")
        }

        // Scope is mandatory on every event. Without it the client cannot tell
        // which segment a change belongs to, and applying a change to the
        // wrong scope is a cross-scope leak.
        val scope = frame[KEY_SCOPE] ?: return reject(RealtimeRejection.MissingField, KEY_SCOPE)
        if (!SCOPE.matches(scope)) return reject(RealtimeRejection.InvalidScope, KEY_SCOPE)

        val subjectId = frame[KEY_SUBJECT_ID]
        val subject = subjectFor(type, subjectId)
            ?: return reject(RealtimeRejection.InvalidSubject, KEY_SUBJECT_ID)

        val payload = when (val parsed = payloadFor(type, frame)) {
            is PayloadParse.Invalid -> return reject(parsed.reason, parsed.detail)
            is PayloadParse.Ok -> parsed.payload
        }

        return RealtimeParseResult.Accepted(
            RealtimeEvent(
                eventId = eventId,
                schemaVersion = version,
                sequence = sequence,
                type = type,
                occurredAtEpochSeconds = occurredAt,
                scope = scope,
                subject = subject,
                payload = payload
            )
        )
    }

    /**
     * Which kind of thing an event type is allowed to be about.
     *
     * Fixed per type rather than taken from the frame, so a publisher cannot
     * send a trust change "about" a delivery record and have the client
     * apply it somewhere unexpected.
     */
    private fun subjectFor(type: RealtimeEventType, id: String?): RealtimeSubject? {
        if (type == RealtimeEventType.CircuitBreakerChanged) return RealtimeSubject.Subsystem
        if (id == null || !IDENTIFIER.matches(id)) return null
        // An address is not a subject.
        if (looksLikeAddress(id)) return null

        return when (type) {
            RealtimeEventType.DeviceObserved,
            RealtimeEventType.DeviceAddressChanged,
            RealtimeEventType.DevicePresenceChanged,
            RealtimeEventType.DeviceEnforcementChanged -> RealtimeSubject.Device(id)

            RealtimeEventType.TrustChanged,
            RealtimeEventType.VerificationCompleted,
            RealtimeEventType.ReverificationRequested,
            RealtimeEventType.IdentityRevoked -> RealtimeSubject.Identity(id)

            RealtimeEventType.AlertRaised,
            RealtimeEventType.AlertAcknowledged,
            RealtimeEventType.AlertResolved,
            RealtimeEventType.AlertIgnored -> RealtimeSubject.Alert(id)

            RealtimeEventType.DeliveryStateChanged -> RealtimeSubject.Delivery(id)

            RealtimeEventType.ActionStateChanged -> RealtimeSubject.Action(id)

            RealtimeEventType.CircuitBreakerChanged -> RealtimeSubject.Subsystem
        }
    }

    private val IPV4 = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
    private val MAC = Regex("^([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

    fun looksLikeAddress(value: String): Boolean = IPV4.matches(value) || MAC.matches(value)

    private sealed interface PayloadParse {
        data class Ok(val payload: RealtimePayload) : PayloadParse
        data class Invalid(val reason: RealtimeRejection, val detail: String) : PayloadParse
    }

    private fun payloadFor(type: RealtimeEventType, frame: Map<String, String>): PayloadParse =
        when (type) {
            RealtimeEventType.DeviceObserved,
            RealtimeEventType.DevicePresenceChanged,
            RealtimeEventType.DeviceAddressChanged -> deviceObservation(frame)

            RealtimeEventType.DeviceEnforcementChanged -> enforcementChange(frame)

            RealtimeEventType.TrustChanged,
            RealtimeEventType.VerificationCompleted,
            RealtimeEventType.ReverificationRequested,
            RealtimeEventType.IdentityRevoked -> trustChange(frame)

            RealtimeEventType.AlertRaised,
            RealtimeEventType.AlertAcknowledged,
            RealtimeEventType.AlertResolved,
            RealtimeEventType.AlertIgnored -> alertChange(frame)

            RealtimeEventType.DeliveryStateChanged -> deliveryChange(frame)

            RealtimeEventType.ActionStateChanged -> actionChange(frame)

            RealtimeEventType.CircuitBreakerChanged -> breakerChange(frame)
        }

    private fun deviceObservation(frame: Map<String, String>): PayloadParse {
        val presence = when (frame[KEY_PRESENCE]) {
            "PRESENT" -> Presence.Present
            "ABSENT" -> Presence.Absent
            "UNKNOWN" -> Presence.Unknown
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_PRESENCE)
        }
        // Observation context, explicitly optional and explicitly not identity.
        val address = frame[KEY_OBSERVED_ADDRESS]
        if (address != null && address.length > MAX_IDENTIFIER_LENGTH) {
            return PayloadParse.Invalid(RealtimeRejection.FieldTooLong, KEY_OBSERVED_ADDRESS)
        }
        val lastSeen = frame[KEY_LAST_SEEN]
            ?: return PayloadParse.Invalid(RealtimeRejection.MissingField, KEY_LAST_SEEN)
        if (lastSeen.length > MAX_TEXT_LENGTH) {
            return PayloadParse.Invalid(RealtimeRejection.FieldTooLong, KEY_LAST_SEEN)
        }
        return PayloadParse.Ok(
            RealtimePayload.DeviceObservation(presence, address, sanitize(lastSeen))
        )
    }

    private fun enforcementChange(frame: Map<String, String>): PayloadParse {
        val enforcement = when (frame[KEY_ENFORCEMENT]) {
            "NORMAL" -> DeviceEnforcement.Normal
            "QUARANTINED" -> DeviceEnforcement.Quarantined
            "RECONCILING" -> DeviceEnforcement.Reconciling
            "FAILED" -> DeviceEnforcement.Failed
            "PAUSED" -> DeviceEnforcement.Paused
            "UNKNOWN" -> DeviceEnforcement.Unknown
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_ENFORCEMENT)
        }
        return PayloadParse.Ok(RealtimePayload.DeviceEnforcementChange(enforcement))
    }

    private fun trustChange(frame: Map<String, String>): PayloadParse {
        val trust = when (frame[KEY_TRUST]) {
            "TRUSTED" -> TrustState.Trusted
            "PENDING" -> TrustState.Pending
            "REVOKED" -> TrustState.Revoked
            "UNVERIFIED" -> TrustState.Unverified
            "UNKNOWN" -> TrustState.Unknown
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_TRUST)
        }
        return PayloadParse.Ok(RealtimePayload.TrustChange(trust))
    }

    private fun alertChange(frame: Map<String, String>): PayloadParse {
        val lifecycle = when (frame[KEY_LIFECYCLE]) {
            "NEW" -> AlertLifecycle.New
            "ACKNOWLEDGED" -> AlertLifecycle.Acknowledged
            "RESOLVED" -> AlertLifecycle.Resolved
            "IGNORED" -> AlertLifecycle.Ignored
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_LIFECYCLE)
        }
        return PayloadParse.Ok(RealtimePayload.AlertLifecycleChange(lifecycle))
    }

    private fun deliveryChange(frame: Map<String, String>): PayloadParse {
        val state = when (frame[KEY_DELIVERY_STATE]) {
            "PENDING" -> DeliveryState.Pending
            "SENT" -> DeliveryState.Sent
            "DELIVERED" -> DeliveryState.Delivered
            "RETRYING" -> DeliveryState.Retrying
            "FAILED" -> DeliveryState.Failed
            "EXHAUSTED" -> DeliveryState.Exhausted
            "UNAVAILABLE" -> DeliveryState.Unavailable
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_DELIVERY_STATE)
        }
        val attempts = frame[KEY_ATTEMPT_COUNT]?.toIntOrNull()
            ?: return PayloadParse.Invalid(RealtimeRejection.InvalidPayload, KEY_ATTEMPT_COUNT)
        if (attempts !in 0..MAX_ATTEMPTS) {
            return PayloadParse.Invalid(RealtimeRejection.InvalidPayload, KEY_ATTEMPT_COUNT)
        }
        val reason = frame[KEY_FAILURE_REASON]
        if (reason != null && reason.length > MAX_TEXT_LENGTH) {
            return PayloadParse.Invalid(RealtimeRejection.FieldTooLong, KEY_FAILURE_REASON)
        }
        return PayloadParse.Ok(
            RealtimePayload.DeliveryChange(state, attempts, reason?.let(::sanitize))
        )
    }

    private fun actionChange(frame: Map<String, String>): PayloadParse {
        val state = when (frame[KEY_EXECUTION_STATE]) {
            "REQUESTED" -> ExecutionState.Requested
            "AUTHORIZED" -> ExecutionState.Authorized
            "DENIED" -> ExecutionState.Denied
            "EXECUTING" -> ExecutionState.Executing
            "RECONCILING" -> ExecutionState.Reconciling
            "SUCCEEDED" -> ExecutionState.Succeeded
            "FAILED" -> ExecutionState.Failed
            "ROLLBACK_REQUESTED" -> ExecutionState.RollbackRequested
            "ROLLED_BACK" -> ExecutionState.RolledBack
            "ROLLBACK_FAILED" -> ExecutionState.RollbackFailed
            "UNKNOWN" -> ExecutionState.Unknown
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_EXECUTION_STATE)
        }
        // Mode is mandatory on an execution event. Without it the client
        // cannot tell a simulation from a firewall change, and defaulting it
        // either way would be inventing the most consequential fact there is.
        val mode = when (frame[KEY_EXECUTION_MODE]) {
            "ENFORCE" -> ExecutionMode.Enforce
            "AUDIT_ONLY" -> ExecutionMode.AuditOnly
            "UNKNOWN" -> ExecutionMode.Unknown
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_EXECUTION_MODE)
        }
        val reconciled = when (frame[KEY_RECONCILED]) {
            "true" -> true
            "false", null -> false
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidPayload, KEY_RECONCILED)
        }
        val actionCode = frame[KEY_ACTION_CODE]
            ?: return PayloadParse.Invalid(RealtimeRejection.MissingField, KEY_ACTION_CODE)
        if (!IDENTIFIER.matches(actionCode)) {
            return PayloadParse.Invalid(RealtimeRejection.InvalidIdentifier, KEY_ACTION_CODE)
        }
        return PayloadParse.Ok(
            RealtimePayload.ActionStateChange(state, mode, reconciled, actionCode)
        )
    }

    private fun breakerChange(frame: Map<String, String>): PayloadParse {
        val breaker = when (frame[KEY_BREAKER]) {
            "CLOSED" -> CircuitBreakerState.Closed
            "OPEN" -> CircuitBreakerState.Open
            "HALF_OPEN" -> CircuitBreakerState.HalfOpen
            else -> return PayloadParse.Invalid(RealtimeRejection.InvalidEnum, KEY_BREAKER)
        }
        return PayloadParse.Ok(RealtimePayload.CircuitBreakerChange(breaker))
    }

    /** Strips control characters so a payload cannot forge structure in a label. */
    fun sanitize(raw: String): String =
        raw.map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun reject(reason: RealtimeRejection, detail: String) =
        RealtimeParseResult.Rejected(reason, detail)
}

/** Why a frame was refused. A safe diagnostic; never the frame itself. */
enum class RealtimeRejection {
    EmptyFrame,
    MissingSchemaVersion,
    MalformedSchemaVersion,
    MissingField,
    InvalidIdentifier,
    InvalidSequence,
    UnknownEventType,
    InvalidTimestamp,
    InvalidScope,
    InvalidSubject,
    InvalidEnum,
    InvalidPayload,
    FieldTooLong
}

sealed interface RealtimeParseResult {
    data class Accepted(val event: RealtimeEvent) : RealtimeParseResult

    /**
     * A frame from a schema this build does not know.
     *
     * Distinct from a rejection: it is held rather than treated as corrupt,
     * because the right response is a client update, not a resync.
     */
    data class UnsupportedVersion(val version: Int) : RealtimeParseResult

    data class Rejected(val reason: RealtimeRejection, val detail: String) : RealtimeParseResult
}
