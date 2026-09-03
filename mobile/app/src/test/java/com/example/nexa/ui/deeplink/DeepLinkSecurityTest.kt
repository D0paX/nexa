package com.example.nexa.ui.deeplink

import com.example.nexa.ActionConfirmation
import com.example.nexa.AlertDetail
import com.example.nexa.Alerts
import com.example.nexa.Audit
import com.example.nexa.AuditDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.Devices
import com.example.nexa.IdentityDetail
import com.example.nexa.LinkProblem
import com.example.nexa.NotificationCenter
import com.example.nexa.NotificationDetail
import com.example.nexa.Overview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a deep link must never be able to do.
 *
 * A link is a request to open a context. It is not authorization, not
 * execution, not enforcement, and not a source of truth about anything it
 * names. Every test here is one way that could go wrong.
 */
class DeepLinkSecurityTest {

    private val catalog = object : DeepLinkCatalog {
        override fun alertExists(alertId: String) = alertId == "ALRT-1092"
        override fun currentMacForDevice(deviceId: String) =
            if (deviceId == "DEV-1001") "00:1A:2B:3C:4D:5E" else null
        override fun identityExists(identityId: String) = identityId == "TID-88F1"
        override fun auditEventExists(eventId: String) = eventId == "EVT-4401"
        override fun deliveryExists(deliveryId: String) = deliveryId == "NTF-7002"
    }

    private val resolver = NexaDeepLinkResolver(catalog)

    private fun navKey(raw: String) = resolver.resolve(raw).toNavKey()

    // ============================================================
    // NO LINK EXECUTES ANYTHING
    // ============================================================

    /**
     * The load-bearing property of this checkpoint. No link — well-formed,
     * malformed, or deliberately shaped to look like a command — reaches the
     * screen that submits enforcement requests.
     */
    @Test
    fun `no link can reach the action confirmation screen`() {
        listOf(
            "nexa://v1/overview",
            "nexa://v1/devices",
            "nexa://v1/alerts",
            "nexa://v1/audit",
            "nexa://v1/notifications",
            "nexa://v1/alert/ALRT-1092",
            "nexa://v1/device/DEV-1001",
            "nexa://v1/identity/TID-88F1",
            "nexa://v1/audit/EVT-4401",
            "nexa://v1/notification/NTF-7002",
            "nexa://v1/quarantine/DEV-1001",
            "nexa://v1/release/DEV-1001",
            "nexa://v1/reverify/TID-88F1",
            "nexa://v1/action/ACT-9127",
            "nexa://v1/confirm/ACT-9127",
            "nexa://v1/execute/QUARANTINE_DEVICE",
            "nexa://v1/alert/ALRT-1092?action=QUARANTINE_DEVICE",
            "nexa://v1/device/DEV-1001?execute=quarantine",
            "nexa://v2/quarantine/DEV-1001",
            "https://evil.example.com/quarantine",
            "intent://quarantine",
            ""
        ).forEach { raw ->
            val key = navKey(raw)
            assertFalse("\"$raw\" reached $key", key is ActionConfirmation)
        }
    }

    /**
     * The type system, not just the router. There is no link variant for an
     * action, so an executable route cannot be constructed even internally.
     */
    @Test
    fun `every destination the adapter can produce only reads state`() {
        listOf(
            "nexa://v1/overview" to Overview,
            "nexa://v1/devices" to Devices,
            "nexa://v1/alerts" to Alerts,
            "nexa://v1/audit" to Audit,
            "nexa://v1/notifications" to NotificationCenter,
            "nexa://v1/alert/ALRT-1092" to AlertDetail("ALRT-1092"),
            "nexa://v1/device/DEV-1001" to DeviceDetail("00:1A:2B:3C:4D:5E"),
            "nexa://v1/identity/TID-88F1" to IdentityDetail("TID-88F1"),
            "nexa://v1/audit/EVT-4401" to AuditDetail("EVT-4401"),
            "nexa://v1/notification/NTF-7002" to NotificationDetail("NTF-7002")
        ).forEach { (raw, expected) ->
            assertEquals(raw, expected, navKey(raw))
        }
    }

    @Test
    fun `an action-looking link lands on the problem surface, not an action`() {
        listOf(
            "nexa://v1/quarantine/DEV-1001",
            "nexa://v1/release/DEV-1001",
            "nexa://v1/reverify/TID-88F1"
        ).forEach { raw ->
            assertTrue("\"$raw\" did not land safely", navKey(raw) is LinkProblem)
        }
    }

    // ============================================================
    // NO ARBITRARY NAVIGATION
    // ============================================================

    @Test
    fun `an external scheme cannot become a NEXA destination`() {
        listOf(
            "https://example.com/v1/alert/ALRT-1092",
            "file:///data/data/com.example.nexa/databases",
            "content://settings/secure",
            "market://details?id=com.example",
            "tel:+15551234567",
            "sms:+15551234567"
        ).forEach { raw ->
            val resolution = resolver.resolve(raw)
            assertTrue(
                "\"$raw\" resolved",
                resolution is DeepLinkResolution.Invalid
            )
            assertTrue(navKey(raw) is LinkProblem)
        }
    }

    @Test
    fun `a link cannot smuggle a nested uri through an identifier`() {
        listOf(
            "nexa://v1/alert/https://evil.example.com",
            "nexa://v1/alert/intent:%23Intent%3B",
            "nexa://v1/device/content://x"
        ).forEach { raw ->
            assertTrue("\"$raw\" resolved", navKey(raw) is LinkProblem)
        }
    }

    // ============================================================
    // IP IS NOT IDENTITY
    // ============================================================

    /**
     * A device link addressed by an address is refused outright. Even if the
     * catalog happened to know that address, resolving it would mean treating
     * an observation as a target — the stale-identifier mistake Phase 4 exists
     * to prevent.
     */
    @Test
    fun `an address can never address a device`() {
        listOf(
            "nexa://v1/device/192.168.1.105",
            "nexa://v1/device/10.20.4.11",
            "nexa://v1/device/00:1A:2B:3C:4D:5E"
        ).forEach { raw ->
            val resolution = resolver.resolve(raw)
            assertTrue("\"$raw\" resolved", resolution is DeepLinkResolution.Invalid)
            assertEquals(
                DeepLinkRejection.InvalidIdentifier,
                (resolution as DeepLinkResolution.Invalid).reason
            )
        }
    }

    /** The address a device route uses is read from the catalog, not the link. */
    @Test
    fun `the device address comes from the inventory`() {
        val resolution = resolver.resolve("nexa://v1/device/DEV-1001")
        assertEquals(
            "00:1A:2B:3C:4D:5E",
            (resolution as DeepLinkResolution.Resolved).resolvedDeviceMac
        )
        assertEquals(DeviceDetail("00:1A:2B:3C:4D:5E"), resolution.toNavKey())
    }

    /**
     * A re-addressed device follows the device. The link is unchanged and the
     * route updates, which is the whole reason a link carries no address.
     */
    @Test
    fun `a re-addressed device resolves to its new address`() {
        val movedCatalog = object : DeepLinkCatalog by catalog {
            override fun currentMacForDevice(deviceId: String) =
                if (deviceId == "DEV-1001") "AA:BB:CC:DD:EE:FF" else null
        }
        val moved = NexaDeepLinkResolver(movedCatalog).resolve("nexa://v1/device/DEV-1001")
        assertEquals(
            DeviceDetail("AA:BB:CC:DD:EE:FF"),
            (moved as DeepLinkResolution.Resolved).toNavKey()
        )
    }

    // ============================================================
    // DELETED AND MISSING OBJECTS
    // ============================================================

    /**
     * A valid link to something gone is a different fact from an invalid
     * link, and an operator is told the difference.
     */
    @Test
    fun `a valid link to a missing object is unavailable, not invalid`() {
        listOf(
            "nexa://v1/alert/ALRT-0001" to DeepLinkObject.Alert,
            "nexa://v1/device/DEV-9999" to DeepLinkObject.Device,
            "nexa://v1/identity/TID-0000" to DeepLinkObject.Identity,
            "nexa://v1/audit/EVT-0000" to DeepLinkObject.AuditRecord,
            "nexa://v1/notification/NTF-0000" to DeepLinkObject.Notification
        ).forEach { (raw, obj) ->
            val resolution = resolver.resolve(raw)
            assertTrue("$raw was not unavailable", resolution is DeepLinkResolution.ObjectUnavailable)
            assertEquals(obj, (resolution as DeepLinkResolution.ObjectUnavailable).obj)
            assertTrue(resolution.operatorMessage().contains("no longer available"))
        }
    }

    @Test
    fun `invalid and unavailable read differently to an operator`() {
        val invalid = resolver.resolve("nexa://v1/quarantine/X")
        val unavailable = resolver.resolve("nexa://v1/alert/ALRT-0001")
        assertEquals("This NEXA link is not valid.", invalid.operatorMessage())
        assertEquals("This alert is no longer available.", unavailable.operatorMessage())
        assertFalse(invalid.operatorMessage() == unavailable.operatorMessage())
    }

    /** Nothing is rebuilt from the URI when the object is gone. */
    @Test
    fun `a missing object produces no destination carrying its identifier`() {
        val key = navKey("nexa://v1/alert/ALRT-0001")
        assertTrue(key is LinkProblem)
        assertFalse((key as LinkProblem).message.contains("ALRT-0001"))
        assertFalse(key.title.contains("ALRT-0001"))
    }

    // ============================================================
    // OPERATOR WORDING
    // ============================================================

    /** Parser reasoning is never shown. It helps nobody and tells a prober something. */
    @Test
    fun `operator wording never leaks parser detail`() {
        listOf(
            "nexa://v1/alert/../../etc/passwd",
            "nexa://v1/alert/ALRT-1092/extra",
            "https://evil.example.com",
            "nexa://v9/alert/ALRT-1092"
        ).forEach { raw ->
            val message = resolver.resolve(raw).operatorMessage()
            listOf("segment", "regex", "parse", "scheme", "identifier", "null")
                .forEach { term ->
                    assertFalse(
                        "\"$raw\" leaked \"$term\": $message",
                        message.lowercase().contains(term)
                    )
                }
        }
    }

    @Test
    fun `an unsupported version says so rather than guessing`() {
        val resolution = resolver.resolve("nexa://v2/alert/ALRT-1092")
        assertTrue(resolution is DeepLinkResolution.UnsupportedVersion)
        assertTrue(resolution.operatorMessage().contains("newer version"))
        assertTrue(resolution.toNavKey() is LinkProblem)
    }

    // ============================================================
    // AUTHORIZATION
    // ============================================================

    /**
     * A denied context reveals nothing about whether the object exists. The
     * access check runs before the catalog is consulted.
     */
    @Test
    fun `a denied link does not disclose whether the object exists`() {
        val denying = object : DeepLinkAccessPolicy {
            override fun canOpen(link: NexaDeepLink) = DeepLinkAccess.Denied("out of scope")
        }
        val restricted = NexaDeepLinkResolver(catalog, denying)

        val existing = restricted.resolve("nexa://v1/alert/ALRT-1092")
        val missing = restricted.resolve("nexa://v1/alert/ALRT-0001")
        assertTrue(existing is DeepLinkResolution.Unauthorized)
        assertTrue(missing is DeepLinkResolution.Unauthorized)
        assertEquals(existing.operatorMessage(), missing.operatorMessage())
        assertEquals("You do not have access to this security context.", existing.operatorMessage())
    }

    @Test
    fun `an expired session is its own outcome`() {
        val expired = object : DeepLinkAccessPolicy {
            override fun canOpen(link: NexaDeepLink) = DeepLinkAccess.SessionExpired
        }
        val resolution = NexaDeepLinkResolver(catalog, expired).resolve("nexa://v1/alert/ALRT-1092")
        assertTrue(resolution is DeepLinkResolution.SessionExpired)
        assertTrue(resolution.operatorMessage().contains("session has expired", ignoreCase = true))
    }

    /** A link cannot opt itself out of the policy. */
    @Test
    fun `no link content can bypass the access policy`() {
        val denying = object : DeepLinkAccessPolicy {
            override fun canOpen(link: NexaDeepLink) = DeepLinkAccess.Denied("denied")
        }
        val restricted = NexaDeepLinkResolver(catalog, denying)
        listOf(
            "nexa://v1/alert/ALRT-1092?src=app",
            "nexa://v1/alert/ALRT-1092?authorized=true",
            "nexa://v1/alert/ALRT-1092?admin=1",
            "nexa://v1/overview"
        ).forEach { raw ->
            assertTrue("\"$raw\" bypassed", restricted.resolve(raw) is DeepLinkResolution.Unauthorized)
        }
    }

    // ============================================================
    // SOURCE IS NOT TRUST
    // ============================================================

    /**
     * A link from a notification is validated exactly as hard as one typed by
     * a stranger. Source affects nothing that matters.
     */
    @Test
    fun `source does not change any outcome`() {
        DeepLinkSource.entries.forEach { source ->
            assertTrue(
                resolver.resolve("nexa://v1/quarantine/X", source) is DeepLinkResolution.Invalid
            )
            assertTrue(
                resolver.resolve("nexa://v1/alert/ALRT-0001", source)
                    is DeepLinkResolution.ObjectUnavailable
            )
            val ok = resolver.resolve("nexa://v1/alert/ALRT-1092", source)
            assertEquals(AlertDetail("ALRT-1092"), ok.toNavKey())
        }
    }

    // ============================================================
    // DETERMINISM
    // ============================================================

    @Test
    fun `the same link resolves identically every time`() {
        val raw = "nexa://v1/alert/ALRT-1092"
        val first = resolver.resolve(raw)
        val second = resolver.resolve(raw)
        assertEquals(first, second)
        assertEquals(first.toNavKey(), second.toNavKey())
    }

    @Test
    fun `resolution never throws on any input`() {
        listOf(
            null, "", " ", "nexa", "nexa://", "nexa://v1", "nexa://v1/",
            "nexa://v1/alert", "nexa://v1/alert/", "nexa://v1//alert//x",
            "nexa://v1/alert/x?=&=&", "A".repeat(5000)
        ).forEach { raw ->
            assertNotNull("threw on \"$raw\"", resolver.resolve(raw))
            assertNotNull(resolver.resolve(raw).toNavKey())
        }
    }
}
