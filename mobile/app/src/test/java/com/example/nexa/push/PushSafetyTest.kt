package com.example.nexa.push

import com.example.nexa.ActionConfirmation
import com.example.nexa.AlertDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.IdentityDetail
import com.example.nexa.NotificationCenter
import com.example.nexa.NotificationDetail
import com.example.nexa.LinkProblem
import com.example.nexa.push.debug.PushFixtures
import com.example.nexa.ui.common.ExecutionMode
import com.example.nexa.ui.deeplink.DeepLinkResolution
import com.example.nexa.ui.deeplink.DeepLinkSource
import com.example.nexa.ui.deeplink.NexaDeepLink
import com.example.nexa.ui.deeplink.NexaDeepLinkParser
import com.example.nexa.ui.deeplink.PreviewDeepLinkCatalog
import com.example.nexa.ui.deeplink.NexaDeepLinkResolver
import com.example.nexa.ui.deeplink.toNavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a push must never be able to do.
 *
 * A notification is a delivery mechanism. It is not authorization, not
 * identity, not trust, not execution and not proof that anything happened.
 * Every test here is one way that could go wrong.
 */
class PushSafetyTest {

    private fun payloadOf(fixture: Map<String, String>): PushPayload =
        (PushPayloadParser.parse(fixture) as PushParseResult.Accepted).payload

    private val allWellFormed: List<PushPayload> = listOf(
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
    ).map(::payloadOf)

    private val resolver = NexaDeepLinkResolver(PreviewDeepLinkCatalog)

    private fun navKeyFor(payload: PushPayload) =
        resolver.resolve(deepLinkFor(payload)).toNavKey()

    // ============================================================
    // A TAP CANNOT EXECUTE
    // ============================================================

    /**
     * The load-bearing test. No payload, of any source type, in any mode,
     * becomes a link that reaches the screen submitting enforcement requests.
     */
    @Test
    fun `no push can route into the action confirmation flow`() {
        allWellFormed.forEach { payload ->
            val key = navKeyFor(payload)
            assertFalse("${payload.notificationId} routed to $key", key is ActionConfirmation)
        }
    }

    @Test
    fun `every push destination lands on a surface that only reads state`() {
        allWellFormed.forEach { payload ->
            val key = navKeyFor(payload)
            assertTrue(
                "${payload.notificationId} routed to $key",
                key is AlertDetail || key is DeviceDetail || key is IdentityDetail ||
                    key is NotificationDetail || key == NotificationCenter ||
                    key is LinkProblem
            )
        }
    }

    /** The push layer produces links, never screens. */
    @Test
    fun `a push produces a deep link with no executable variant`() {
        allWellFormed.forEach { payload ->
            val link = deepLinkFor(payload)
            assertEquals(DeepLinkSource.Notification, link.source)
        }
    }

    /**
     * An action notification leads to its delivery record, never to a
     * re-execution. The record states what was requested and links onward.
     */
    @Test
    fun `an action push routes to its delivery record`() {
        val payload = payloadOf(PushFixtures.actionExecuting)
        assertEquals(
            NexaDeepLink.Notification(payload.notificationId, DeepLinkSource.Notification),
            deepLinkFor(payload)
        )
    }

    @Test
    fun `enforcement action codes are never routing instructions`() {
        listOf("QUARANTINE_DEVICE", "RELEASE_QUARANTINE", "REQUIRE_REVERIFICATION").forEach { code ->
            val data = PushFixtures.criticalAlert +
                (PushPayloadParser.KEY_SOURCE_TYPE to code)
            // A source type is a closed vocabulary; an action code is not in it.
            assertTrue(PushPayloadParser.parse(data) is PushParseResult.Rejected)
        }
    }

    // ============================================================
    // AN ADDRESS IS NOT A TARGET
    // ============================================================

    /**
     * The stale-identifier rule, applied to the link format. A device push
     * carrying an address produces no device link: reconstructing a target
     * from an address is exactly what Phase 4 exists to prevent.
     */
    @Test
    fun `a device push carrying an address gets no device link`() {
        val payload = payloadOf(PushFixtures.deviceReferencedByAddress)
        val link = deepLinkFor(payload)
        assertFalse(link is NexaDeepLink.Device)
        assertEquals(
            NexaDeepLink.Notification(payload.notificationId, DeepLinkSource.Notification),
            link
        )
    }

    @Test
    fun `a device push addressed by record id produces a device link`() {
        val payload = payloadOf(PushFixtures.informationalDevice)
        val link = deepLinkFor(payload)
        assertTrue(link is NexaDeepLink.Device)
        assertEquals("DEV-1001", (link as NexaDeepLink.Device).deviceId)
    }

    @Test
    fun `no address or MAC is ever accepted as a link identifier`() {
        assertFalse(NexaDeepLinkParser.isValidIdentifier("192.168.1.105"))
        assertFalse(NexaDeepLinkParser.isValidIdentifier("00:1A:2B:3C:4D:5E"))
        assertFalse(NexaDeepLinkParser.isValidIdentifier("00-1A-2B-3C-4D-5E"))
        assertTrue(NexaDeepLinkParser.isValidIdentifier("DEV-1001"))
    }

    /** The resolved address comes from the inventory, never from the link. */
    @Test
    fun `a device link resolves its address from the inventory`() {
        val resolution = resolver.resolve(NexaDeepLink.Device("DEV-1001"))
        assertTrue(resolution is DeepLinkResolution.Resolved)
        assertEquals(
            "00:1A:2B:3C:4D:5E",
            (resolution as DeepLinkResolution.Resolved).resolvedDeviceMac
        )
    }

    // ============================================================
    // AUDIT_ONLY
    // ============================================================

    @Test
    fun `a simulated push is presented as a simulation`() {
        val content = notificationContentFor(payloadOf(PushFixtures.auditOnlySimulation))
        assertEquals("SIMULATION", content.title)
        assertTrue(content.text.lowercase().contains("no firewall mutation occurred"))
    }

    /**
     * The forged-claim case. A sender says "Device quarantined" about a run
     * that mutated nothing; the shade must not repeat it.
     */
    @Test
    fun `a simulated push claiming a mutation is not allowed to say so`() {
        val payload = payloadOf(PushFixtures.auditOnlyClaimingMutation)
        assertEquals(ExecutionMode.AuditOnly, payload.executionMode)

        val content = notificationContentFor(payload)
        val text = content.text.lowercase()
        assertFalse("shade repeated the claim: ${content.text}", text.contains("quarantined"))
        assertFalse(text.contains("was applied"))
        assertTrue(text.contains("no firewall mutation occurred"))
    }

    @Test
    fun `every simulated payload carries the disclaimer whatever its body says`() {
        listOf(
            PushFixtures.auditOnlySimulation,
            PushFixtures.auditOnlyClaimingMutation
        ).map(::payloadOf).forEach { payload ->
            val content = notificationContentFor(payload)
            assertTrue(
                payload.notificationId,
                content.text.lowercase().contains("no firewall mutation occurred")
            )
        }
    }

    @Test
    fun `the mutation guard recognises enforcement claims`() {
        assertTrue(claimsMutationWithoutDisclaimer("Device quarantined."))
        assertTrue(claimsMutationWithoutDisclaimer("Binding was applied."))
        assertTrue(claimsMutationWithoutDisclaimer("Traffic blocked."))
        assertFalse(
            claimsMutationWithoutDisclaimer("Simulated quarantine — no firewall mutation occurred.")
        )
        assertFalse(claimsMutationWithoutDisclaimer("A simulated action completed."))
    }

    /** A live run is not dressed up as a simulation either. */
    @Test
    fun `a live push is never labelled a simulation`() {
        val content = notificationContentFor(payloadOf(PushFixtures.actionExecuting))
        assertEquals("ACTION UPDATE", content.title)
        assertFalse(content.text.lowercase().contains("no firewall mutation"))
    }

    @Test
    fun `an unknown execution mode says so rather than picking one`() {
        val content = notificationContentFor(payloadOf(PushFixtures.unknownExecutionMode))
        assertTrue(content.title.contains("MODE UNKNOWN"))
    }

    // ============================================================
    // THE SHADE CLAIMS NOTHING
    // ============================================================

    /**
     * NEXA never adds a conclusion the payload did not carry. Titles state a
     * category; whether an action succeeded is the pipeline to report and the
     * operator reads it from the record.
     */
    @Test
    fun `no notification title asserts an outcome`() {
        val forbidden = listOf("succeeded", "success", "completed", "quarantined", "released")
        allWellFormed.forEach { payload ->
            val title = notificationContentFor(payload).title.lowercase()
            forbidden.forEach { claim ->
                assertFalse("${payload.notificationId} title says \"$claim\"", title.contains(claim))
            }
        }
    }

    // ============================================================
    // PRIVACY
    // ============================================================

    @Test
    fun `network addresses never reach the notification shade`() {
        val payload = payloadOf(PushFixtures.addressesInBody)
        val content = notificationContentFor(payload)
        assertFalse(content.text.contains("10.20.4.18"))
        assertFalse(content.text.contains("00:1A:2B:3C:4D:5E"))
        assertTrue(content.text.contains("[address hidden]"))
    }

    @Test
    fun `no notification content leaks an address on any fixture`() {
        allWellFormed.forEach { payload ->
            val content = notificationContentFor(payload)
            listOf(content.title, content.text, content.publicTitle, content.publicText)
                .forEach { field ->
                    assertFalse(
                        "${payload.notificationId} exposed a MAC: $field",
                        Regex("\\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b").containsMatchIn(field)
                    )
                    assertFalse(
                        "${payload.notificationId} exposed an address: $field",
                        Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b").containsMatchIn(field)
                    )
                }
        }
    }

    /**
     * The lock screen learns that NEXA has something to say and how urgent it
     * is. It does not learn which device, which identity or which incident.
     */
    @Test
    fun `the lock screen version carries no identifiers`() {
        allWellFormed.forEach { payload ->
            val content = notificationContentFor(payload)
            assertEquals("NEXA", content.publicTitle)
            assertFalse(content.publicText.contains(payload.sourceId))
            assertFalse(content.publicText.contains(payload.notificationId))
            payload.targetRef?.let {
                assertFalse(content.publicText.contains(it.id))
            }
        }
    }

    // ============================================================
    // CHANNELS
    // ============================================================

    @Test
    fun `only a critical alert reaches the interrupting channel`() {
        assertEquals(
            PushChannels.CRITICAL_ALERTS,
            notificationContentFor(payloadOf(PushFixtures.criticalAlert)).channelId
        )
        assertEquals(
            PushImportance.High,
            notificationContentFor(payloadOf(PushFixtures.criticalAlert)).importance
        )

        listOf(
            PushFixtures.warningAlert,
            PushFixtures.informationalDevice,
            PushFixtures.identityRevoked
        ).map(::payloadOf).forEach { payload ->
            val content = notificationContentFor(payload)
            assertEquals(payload.notificationId, PushChannels.SECURITY_NOTICES, content.channelId)
            assertEquals(payload.notificationId, PushImportance.Low, content.importance)
        }
    }

    @Test
    fun `action results have their own channel, simulations included`() {
        listOf(PushFixtures.actionExecuting, PushFixtures.auditOnlySimulation)
            .map(::payloadOf)
            .forEach {
                assertEquals(
                    PushChannels.ACTION_RESULTS,
                    notificationContentFor(it).channelId
                )
            }
    }

    @Test
    fun `every channel used is one NEXA registers`() {
        allWellFormed.forEach { payload ->
            assertTrue(notificationContentFor(payload).channelId in PushChannels.all)
        }
    }

    // ============================================================
    // TOKEN SECURITY
    // ============================================================

    /**
     * The ordinary ways a secret escapes are a log line, a crash report and a
     * string template. All three call toString.
     */
    @Test
    fun `a token never prints itself`() {
        // Structurally token-shaped but obviously synthetic, so a credential
        // scanner reading this repository has nothing to flag.
        val raw = "EXAMPLE-ENDPOINT:NOT-A-REAL-REGISTRATION-TOKEN-0000"
        val token = PushToken(raw)

        assertFalse(token.toString().contains(raw))
        assertFalse("$token".contains(raw))
        assertFalse(listOf(token).toString().contains(raw))
        assertTrue(token.toString().contains(token.fingerprint))
    }

    @Test
    fun `a fingerprint is stable, short and not the token`() {
        val raw = "some-registration-token-value"
        val first = PushToken(raw).fingerprint
        val second = PushToken(raw).fingerprint
        assertEquals(first, second)
        assertEquals(8, first.length)
        assertFalse(raw.contains(first))
    }

    @Test
    fun `different tokens fingerprint differently`() {
        assertFalse(PushToken("token-a").fingerprint == PushToken("token-b").fingerprint)
    }

    @Test
    fun `token state carries a fingerprint and never a token`() {
        val raw = "another-registration-token"
        val state = PushTokenState.Available(PushToken(raw).fingerprint)
        assertFalse(state.toString().contains(raw))
    }

    /**
     * Possessing a delivery address is not being someone. Nothing in the push
     * layer accepts a token as identity, and the registrar contract says the
     * backend must not either.
     */
    @Test
    fun `no backend registration happens without a configured endpoint`() = kotlinx.coroutines.test.runTest {
        val result = NoOpPushTokenRegistrar.register(PushToken("unused"))
        assertEquals(PushRegistrationResult.NotConfigured, result)
        assertNotNull(NoOpPushTokenRegistrar.unregister())
    }
}
