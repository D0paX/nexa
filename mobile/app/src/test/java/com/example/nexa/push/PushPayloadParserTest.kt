package com.example.nexa.push

import com.example.nexa.push.debug.PushFixtures
import com.example.nexa.ui.common.ExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The validation surface.
 *
 * Everything the parser sees arrived over a network from a sender this client
 * cannot authenticate. These tests are the record of what NEXA refuses.
 */
class PushPayloadParserTest {

    private fun accept(data: Map<String, String>): PushPayload {
        val result = PushPayloadParser.parse(data)
        assertTrue("expected acceptance, got $result", result is PushParseResult.Accepted)
        return (result as PushParseResult.Accepted).payload
    }

    private fun reject(data: Map<String, String>): PushParseResult.Rejected {
        val result = PushPayloadParser.parse(data)
        assertTrue("expected rejection, got $result", result is PushParseResult.Rejected)
        return result as PushParseResult.Rejected
    }

    // ============================================================
    // WELL-FORMED
    // ============================================================

    @Test
    fun `a well-formed alert is accepted with every field intact`() {
        val payload = accept(PushFixtures.criticalAlert)
        assertEquals(SUPPORTED_PUSH_SCHEMA_VERSION, payload.schemaVersion)
        assertEquals("NTF-9001", payload.notificationId)
        assertEquals(PushSourceType.Alert, payload.sourceType)
        assertEquals("ALRT-1089", payload.sourceId)
        assertEquals(PushSeverity.Critical, payload.severity)
        assertNull(payload.executionMode)
        assertEquals(PushTargetKind.Device, payload.targetRef?.kind)
    }

    @Test
    fun `every well-formed fixture parses`() {
        listOf(
            PushFixtures.criticalAlert,
            PushFixtures.warningAlert,
            PushFixtures.informationalDevice,
            PushFixtures.identityRevoked,
            PushFixtures.actionExecuting,
            PushFixtures.actionReconciling,
            PushFixtures.actionSucceeded,
            PushFixtures.actionFailed,
            PushFixtures.rollbackFailed,
            PushFixtures.auditOnlySimulation,
            PushFixtures.auditOnlyClaimingMutation,
            PushFixtures.unknownExecutionMode,
            PushFixtures.deletedObject,
            PushFixtures.staleAlert,
            PushFixtures.deviceReferencedByAddress,
            PushFixtures.addressesInBody
        ).forEach { assertNotNull(accept(it)) }
    }

    @Test
    fun `execution mode maps onto the shared phase 4 vocabulary`() {
        assertEquals(ExecutionMode.Enforce, accept(PushFixtures.actionExecuting).executionMode)
        assertEquals(ExecutionMode.AuditOnly, accept(PushFixtures.auditOnlySimulation).executionMode)
        assertEquals(ExecutionMode.Unknown, accept(PushFixtures.unknownExecutionMode).executionMode)
    }

    // ============================================================
    // SCHEMA VERSION
    // ============================================================

    @Test
    fun `an unsupported schema version is refused rather than guessed at`() {
        val rejected = reject(PushFixtures.unsupportedVersion)
        assertEquals(PushRejectionReason.UnsupportedSchemaVersion, rejected.reason)
    }

    @Test
    fun `a missing or malformed schema version is refused`() {
        assertEquals(
            PushRejectionReason.MissingSchemaVersion,
            reject(PushFixtures.criticalAlert - PushPayloadParser.KEY_SCHEMA_VERSION).reason
        )
        assertEquals(
            PushRejectionReason.MalformedSchemaVersion,
            reject(PushFixtures.malformedVersion).reason
        )
    }

    /**
     * The version is checked before anything else. A payload that is both
     * from an unknown schema and malformed elsewhere is refused for the
     * schema, because the other fields cannot be interpreted without it.
     */
    @Test
    fun `schema version is checked before the rest of the payload`() {
        val doubleTrouble = PushFixtures.unsupportedVersion +
            (PushPayloadParser.KEY_SOURCE_TYPE to "EXECUTE")
        assertEquals(
            PushRejectionReason.UnsupportedSchemaVersion,
            reject(doubleTrouble).reason
        )
    }

    // ============================================================
    // IDENTIFIERS
    // ============================================================

    @Test
    fun `a missing identifier is refused`() {
        assertEquals(
            PushRejectionReason.MissingField,
            reject(PushFixtures.missingNotificationId).reason
        )
    }

    @Test
    fun `an identifier carrying path syntax is refused`() {
        assertEquals(
            PushRejectionReason.InvalidIdentifier,
            reject(PushFixtures.invalidNotificationId).reason
        )
    }

    @Test
    fun `identifiers reject separators, whitespace and scheme syntax`() {
        listOf(
            "has space",
            "has/slash",
            "has\\backslash",
            "nexa://alert",
            "id?query=1",
            "id#fragment",
            "id&other",
            ""
        ).forEach { candidate ->
            val data = PushFixtures.criticalAlert +
                (PushPayloadParser.KEY_SOURCE_ID to candidate)
            val rejected = reject(data)
            assertTrue(
                "\"$candidate\" was not refused",
                rejected.reason == PushRejectionReason.InvalidIdentifier ||
                    rejected.reason == PushRejectionReason.MissingField
            )
        }
    }

    @Test
    fun `an oversized identifier is refused`() {
        val data = PushFixtures.criticalAlert +
            (PushPayloadParser.KEY_SOURCE_ID to "A".repeat(65))
        assertEquals(PushRejectionReason.InvalidIdentifier, reject(data).reason)
    }

    // ============================================================
    // ENUMS
    // ============================================================

    @Test
    fun `an unrecognised source type is refused, never defaulted`() {
        assertEquals(PushRejectionReason.InvalidEnum, reject(PushFixtures.invalidSourceType).reason)
    }

    @Test
    fun `an unrecognised severity is refused`() {
        assertEquals(PushRejectionReason.InvalidEnum, reject(PushFixtures.invalidSeverity).reason)
    }

    /**
     * A mode NEXA cannot read is an error, not an absence. Dropping it would
     * turn an unreadable execution mode into "this was not an execution".
     */
    @Test
    fun `an unrecognised execution mode is refused rather than dropped`() {
        assertEquals(
            PushRejectionReason.InvalidEnum,
            reject(PushFixtures.invalidExecutionMode).reason
        )
    }

    @Test
    fun `enum matching is exact and case-sensitive`() {
        listOf("alert", "Alert", " ALERT", "ALERT ").forEach { candidate ->
            val data = PushFixtures.criticalAlert +
                (PushPayloadParser.KEY_SOURCE_TYPE to candidate)
            assertEquals(
                "\"$candidate\" was accepted",
                PushRejectionReason.InvalidEnum,
                reject(data).reason
            )
        }
    }

    // ============================================================
    // TIMESTAMPS
    // ============================================================

    @Test
    fun `a non-numeric timestamp is refused`() {
        assertEquals(
            PushRejectionReason.InvalidTimestamp,
            reject(PushFixtures.invalidTimestamp).reason
        )
    }

    @Test
    fun `an implausible timestamp is refused`() {
        assertEquals(
            PushRejectionReason.InvalidTimestamp,
            reject(PushFixtures.implausibleTimestamp).reason
        )
    }

    // ============================================================
    // LENGTH
    // ============================================================

    @Test
    fun `an oversized body or title is refused rather than truncated`() {
        assertEquals(PushRejectionReason.FieldTooLong, reject(PushFixtures.oversizedBody).reason)
        assertEquals(PushRejectionReason.FieldTooLong, reject(PushFixtures.oversizedTitle).reason)
    }

    // ============================================================
    // SANITISATION
    // ============================================================

    /**
     * A body cannot forge structure. Newlines and control characters are the
     * cheapest way to make one sentence look like two, one of which appears
     * to come from the system.
     */
    @Test
    fun `a deceptive body cannot forge extra lines`() {
        val payload = accept(PushFixtures.deceptiveBody)
        assertFalse(payload.body.contains("\n"))
        assertFalse(payload.body.contains("\r"))
        assertFalse(payload.body.any { it.isISOControl() })
        assertEquals(payload.body, payload.body.trim())
    }

    @Test
    fun `sanitisation collapses whitespace runs`() {
        assertEquals("a b", PushPayloadParser.sanitizeText("a   \t  b"))
        assertEquals("a b", PushPayloadParser.sanitizeText("  a \n b  "))
        assertEquals("", PushPayloadParser.sanitizeText("   "))
    }

    @Test
    fun `a body that sanitises to nothing is refused`() {
        val data = PushFixtures.criticalAlert + (PushPayloadParser.KEY_BODY to "   \n\t ")
        assertEquals(PushRejectionReason.MissingField, reject(data).reason)
    }

    // ============================================================
    // TARGET
    // ============================================================

    @Test
    fun `half a target is refused`() {
        assertEquals(PushRejectionReason.InvalidEnum, reject(PushFixtures.halfTarget).reason)
    }

    @Test
    fun `a payload with no target at all is fine`() {
        val data = PushFixtures.criticalAlert -
            PushPayloadParser.KEY_TARGET_KIND - PushPayloadParser.KEY_TARGET_ID
        assertNull(accept(data).targetRef)
    }

    // ============================================================
    // EMPTY
    // ============================================================

    @Test
    fun `an empty payload is refused`() {
        assertEquals(PushRejectionReason.EmptyPayload, reject(PushFixtures.emptyPayload).reason)
    }

    // ============================================================
    // DIAGNOSTICS
    // ============================================================

    /**
     * A rejection names the field and the rule, never the value. A refused
     * payload is untrusted input, and echoing it into a diagnostic is how
     * untrusted input travels.
     */
    @Test
    fun `a rejection never echoes the offending value`() {
        val secretish = "SUPERSECRET-TOKEN-VALUE"
        val data = PushFixtures.criticalAlert +
            (PushPayloadParser.KEY_SOURCE_ID to "$secretish with spaces")
        val rejected = reject(data)
        assertFalse(rejected.detail.contains(secretish))
    }

    @Test
    fun `no malformed fixture ever throws`() {
        listOf(
            PushFixtures.emptyPayload,
            PushFixtures.unsupportedVersion,
            PushFixtures.malformedVersion,
            PushFixtures.missingNotificationId,
            PushFixtures.invalidNotificationId,
            PushFixtures.invalidSourceType,
            PushFixtures.invalidSeverity,
            PushFixtures.invalidExecutionMode,
            PushFixtures.invalidTimestamp,
            PushFixtures.implausibleTimestamp,
            PushFixtures.oversizedBody,
            PushFixtures.oversizedTitle,
            PushFixtures.halfTarget
        ).forEach { fixture ->
            assertTrue(PushPayloadParser.parse(fixture) is PushParseResult.Rejected)
        }
    }

    /**
     * Nothing crashes on arbitrary junk. A push that could kill the process
     * would be a denial of service delivered by notification.
     */
    @Test
    fun `arbitrary junk is handled without throwing`() {
        val junk = listOf(
            mapOf("" to ""),
            mapOf("schemaVersion" to ""),
            mapOf("schemaVersion" to "-1"),
            mapOf("schemaVersion" to "999999999999999999999"),
            PushFixtures.criticalAlert + ("unexpectedKey" to "value"),
            PushFixtures.criticalAlert.mapValues { "" }
        )
        junk.forEach { assertNotNull(PushPayloadParser.parse(it)) }
    }
}
