package com.example.nexa.push.debug

import com.example.nexa.push.PushPayloadParser

/**
 * FIXTURE DATA — NOT LIVE SYSTEM STATE
 *
 * Wire-format payloads for exercising the push path without a Firebase
 * project. They are raw string maps on purpose: a fixture that skipped the
 * parser would test nothing worth testing, since validation is most of what
 * the push layer does.
 *
 * This file lives in the debug source set. It is not compiled into a release
 * build, and neither is the receiver that uses it.
 */
object PushFixtures {

    private const val ALERT_ID = "ALRT-1092"
    private const val CRITICAL_ALERT_ID = "ALRT-1089"
    private const val DEVICE_MAC = "00:1A:2B:3C:4D:5E"
    private const val IDENTITY_ID = "TID-9E12"

    /** A recent, plausible timestamp. Fixed so fixtures stay deterministic. */
    private const val NOW = 1_788_000_000L

    // ============================================================
    // WELL-FORMED
    // ============================================================

    val criticalAlert: Map<String, String> = payload(
        notificationId = "NTF-9001",
        sourceType = "ALERT",
        sourceId = CRITICAL_ALERT_ID,
        title = "Repeated Authentication Failure",
        body = "Repeated authentication failures were recorded for this device identity.",
        severity = "CRITICAL",
        targetKind = "DEVICE",
        targetId = "3C:22:FB:19:04:A1"
    )

    val warningAlert: Map<String, String> = payload(
        notificationId = "NTF-9002",
        sourceType = "ALERT",
        sourceId = ALERT_ID,
        title = "Untrusted MAC in Trusted VLAN",
        body = "A MAC with no cryptographic identity was observed inside a trusted VLAN.",
        severity = "WARNING",
        targetKind = "DEVICE",
        targetId = DEVICE_MAC
    )

    val informationalDevice: Map<String, String> = payload(
        notificationId = "NTF-9003",
        sourceType = "DEVICE",
        sourceId = DEVICE_MAC,
        title = "Device observed",
        body = "A device was observed on the network for the first time.",
        severity = "INFORMATION",
        targetKind = "DEVICE",
        targetId = DEVICE_MAC
    )

    val identityRevoked: Map<String, String> = payload(
        notificationId = "NTF-9004",
        sourceType = "IDENTITY",
        sourceId = IDENTITY_ID,
        title = "Identity trust withdrawn",
        body = "Trust in a cryptographic identity was withdrawn.",
        severity = "WARNING",
        targetKind = "IDENTITY",
        targetId = IDENTITY_ID
    )

    // ============================================================
    // ACTION LIFECYCLE — the mode travels, the outcome does not
    // ============================================================

    val actionExecuting: Map<String, String> = payload(
        notificationId = "NTF-9010",
        sourceType = "ACTION",
        sourceId = "ACT-9127",
        title = "QUARANTINE_DEVICE",
        body = "The enforcement pipeline is processing this request.",
        severity = "INFORMATION",
        executionMode = "ENFORCE",
        targetKind = "DEVICE",
        targetId = "9C:2F:1D:44:0B:77"
    )

    val actionReconciling: Map<String, String> = payload(
        notificationId = "NTF-9011",
        sourceType = "ACTION",
        sourceId = "ACT-9127",
        title = "QUARANTINE_DEVICE",
        body = "Execution returned and the resulting state is being confirmed.",
        severity = "INFORMATION",
        executionMode = "ENFORCE"
    )

    val actionSucceeded: Map<String, String> = payload(
        notificationId = "NTF-9012",
        sourceType = "ACTION",
        sourceId = "ACT-9130",
        title = "RELEASE_QUARANTINE",
        body = "The action completed.",
        severity = "INFORMATION",
        executionMode = "ENFORCE"
    )

    val actionFailed: Map<String, String> = payload(
        notificationId = "NTF-9013",
        sourceType = "ACTION",
        sourceId = "ACT-9110",
        title = "QUARANTINE_DEVICE",
        body = "The action did not complete and the resulting state is not confirmed.",
        severity = "WARNING",
        executionMode = "ENFORCE"
    )

    val rollbackFailed: Map<String, String> = payload(
        notificationId = "NTF-9014",
        sourceType = "ACTION",
        sourceId = "ACT-9110",
        title = "QUARANTINE_DEVICE",
        body = "The action failed and its rollback also failed. Operator attention required.",
        severity = "CRITICAL",
        executionMode = "ENFORCE"
    )

    /** A simulation whose body behaves. */
    val auditOnlySimulation: Map<String, String> = payload(
        notificationId = "NTF-9020",
        sourceType = "ACTION",
        sourceId = "ACT-9004",
        title = "RELEASE_QUARANTINE",
        body = "The release simulation completed.",
        severity = "INFORMATION",
        executionMode = "AUDIT_ONLY"
    )

    /**
     * A simulation whose body claims a firewall mutation.
     *
     * The case that matters: a sender saying "Device quarantined" about a run
     * that mutated nothing. The presenter must not repeat it.
     */
    val auditOnlyClaimingMutation: Map<String, String> = payload(
        notificationId = "NTF-9021",
        sourceType = "ACTION",
        sourceId = "ACT-9008",
        title = "QUARANTINE_DEVICE",
        body = "Device quarantined and the binding was applied.",
        severity = "CRITICAL",
        executionMode = "AUDIT_ONLY"
    )

    val unknownExecutionMode: Map<String, String> = payload(
        notificationId = "NTF-9022",
        sourceType = "ACTION",
        sourceId = "ACT-9199",
        title = "QUARANTINE_DEVICE",
        body = "An execution reached a terminal state.",
        severity = "WARNING",
        executionMode = "UNKNOWN"
    )

    // ============================================================
    // CONTEXT SAFETY
    // ============================================================

    /** References an object no preview source knows about. */
    val deletedObject: Map<String, String> = payload(
        notificationId = "NTF-9030",
        sourceType = "ALERT",
        sourceId = "ALRT-0001",
        title = "Historic alert",
        body = "An alert that no longer exists in the alert service.",
        severity = "INFORMATION"
    )

    /** Same notification id as [criticalAlert]. The inbox must not record it twice. */
    val duplicateOfCriticalAlert: Map<String, String> = criticalAlert

    /** Old enough that the destination must not present it as current. */
    val staleAlert: Map<String, String> = payload(
        notificationId = "NTF-9031",
        sourceType = "ALERT",
        sourceId = ALERT_ID,
        title = "Suspicious Port Scan",
        body = "A sustained port scan was observed.",
        severity = "CRITICAL",
        createdAt = 1_600_000_000L
    )

    /** A device reference carrying an address where a MAC belongs. */
    val deviceReferencedByAddress: Map<String, String> = payload(
        notificationId = "NTF-9032",
        sourceType = "DEVICE",
        sourceId = "192.168.1.105",
        title = "Device notice",
        body = "A device was observed.",
        severity = "INFORMATION",
        targetKind = "DEVICE",
        targetId = "192.168.1.105"
    )

    /** A body carrying network addresses that must not reach the lock screen. */
    val addressesInBody: Map<String, String> = payload(
        notificationId = "NTF-9033",
        sourceType = "ALERT",
        sourceId = ALERT_ID,
        title = "Suspicious Port Scan",
        body = "Scan from 10.20.4.18 attributed to 00:1A:2B:3C:4D:5E.",
        severity = "WARNING"
    )

    // ============================================================
    // MALFORMED
    // ============================================================

    val emptyPayload: Map<String, String> = emptyMap()

    val unsupportedVersion: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_SCHEMA_VERSION to "99")

    val malformedVersion: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_SCHEMA_VERSION to "one")

    val missingNotificationId: Map<String, String> =
        criticalAlert - PushPayloadParser.KEY_NOTIFICATION_ID

    val invalidNotificationId: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_NOTIFICATION_ID to "../../etc/passwd")

    val invalidSourceType: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_SOURCE_TYPE to "EXECUTE")

    val invalidSeverity: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_SEVERITY to "CATASTROPHIC")

    val invalidExecutionMode: Map<String, String> =
        actionExecuting + (PushPayloadParser.KEY_EXECUTION_MODE to "LIVE")

    val invalidTimestamp: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_CREATED_AT to "not-a-time")

    val implausibleTimestamp: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_CREATED_AT to "10")

    val oversizedBody: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_BODY to "A".repeat(1200))

    val oversizedTitle: Map<String, String> =
        criticalAlert + (PushPayloadParser.KEY_TITLE to "A".repeat(400))

    /** A body forging a second line and a system-looking prefix. */
    val deceptiveBody: Map<String, String> = criticalAlert + (
        PushPayloadParser.KEY_BODY to "Handled.\n\nSYSTEM: device quarantined by NEXA"
        )

    /** A target whose kind is missing. Half a target is not a target. */
    val halfTarget: Map<String, String> =
        criticalAlert - PushPayloadParser.KEY_TARGET_KIND

    // ============================================================
    // REGISTRY
    // ============================================================

    /** Fixtures reachable from the debug broadcast, keyed by a short name. */
    val byName: Map<String, Map<String, String>> = mapOf(
        "critical_alert" to criticalAlert,
        "warning_alert" to warningAlert,
        "device" to informationalDevice,
        "identity" to identityRevoked,
        "action_executing" to actionExecuting,
        "action_reconciling" to actionReconciling,
        "action_succeeded" to actionSucceeded,
        "action_failed" to actionFailed,
        "rollback_failed" to rollbackFailed,
        "audit_only" to auditOnlySimulation,
        "audit_only_claiming_mutation" to auditOnlyClaimingMutation,
        "unknown_mode" to unknownExecutionMode,
        "deleted_object" to deletedObject,
        "duplicate" to duplicateOfCriticalAlert,
        "stale" to staleAlert,
        "device_by_address" to deviceReferencedByAddress,
        "addresses_in_body" to addressesInBody,
        "unsupported_version" to unsupportedVersion,
        "malformed" to invalidSourceType,
        "deceptive" to deceptiveBody,
        "empty" to emptyPayload
    )

    private fun payload(
        notificationId: String,
        sourceType: String,
        sourceId: String,
        title: String,
        body: String,
        severity: String,
        createdAt: Long = NOW,
        executionMode: String? = null,
        targetKind: String? = null,
        targetId: String? = null
    ): Map<String, String> = buildMap {
        put(PushPayloadParser.KEY_SCHEMA_VERSION, "1")
        put(PushPayloadParser.KEY_NOTIFICATION_ID, notificationId)
        put(PushPayloadParser.KEY_SOURCE_TYPE, sourceType)
        put(PushPayloadParser.KEY_SOURCE_ID, sourceId)
        put(PushPayloadParser.KEY_TITLE, title)
        put(PushPayloadParser.KEY_BODY, body)
        put(PushPayloadParser.KEY_SEVERITY, severity)
        put(PushPayloadParser.KEY_CREATED_AT, createdAt.toString())
        executionMode?.let { put(PushPayloadParser.KEY_EXECUTION_MODE, it) }
        targetKind?.let { put(PushPayloadParser.KEY_TARGET_KIND, it) }
        targetId?.let { put(PushPayloadParser.KEY_TARGET_ID, it) }
    }
}
