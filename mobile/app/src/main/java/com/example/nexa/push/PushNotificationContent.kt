package com.example.nexa.push

import com.example.nexa.ui.common.ExecutionMode

/**
 * What a NEXA push says in the Android shade.
 *
 * Pure, so every word the operating system will show can be asserted in a
 * unit test. Three things are decided here and nowhere else:
 *
 *  - which channel, and therefore how loud the notification is;
 *  - what the full text says once the device is unlocked;
 *  - what the *lock screen* says instead, which is much less.
 *
 * The system notification stays native and concise. It is a pointer into
 * NEXA, not a miniature of it.
 */

/** The channels NEXA posts to. Three, matching three real levels of urgency. */
object PushChannels {
    /** A critical security incident. The only channel that interrupts. */
    const val CRITICAL_ALERTS = "nexa_critical_security_alerts"

    /** Everything else about the network: warnings, devices, identities. */
    const val SECURITY_NOTICES = "nexa_security_notices"

    /** Outcomes of enforcement and trust actions. */
    const val ACTION_RESULTS = "nexa_action_results"

    val all: List<String> = listOf(CRITICAL_ALERTS, SECURITY_NOTICES, ACTION_RESULTS)
}

/** How loud a channel is, mapped onto platform importance at registration. */
enum class PushImportance { High, Default, Low }

/**
 * A ready-to-post notification.
 *
 * [publicText] is what the lock screen gets. It is deliberately generic: an
 * unlocked phone can show which device and which alert, a locked one on a
 * table cannot.
 */
data class PushNotificationContent(
    val channelId: String,
    val importance: PushImportance,
    val title: String,
    val text: String,
    val publicTitle: String,
    val publicText: String,
    /** Stable per-notification, so a repeat replaces rather than stacks. */
    val tag: String
)

// ============================================================
// PRIVACY
// ============================================================

private val MAC_PATTERN = Regex("\\b([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b")
private val IPV4_PATTERN = Regex("\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b")

/**
 * Removes network addresses from anything bound for the notification shade.
 *
 * A MAC or an IP in a banner is readable over someone's shoulder, survives in
 * screenshots, and syncs to paired devices — for no operational gain, because
 * the operator is one tap from the full record. Addresses stay in the app.
 *
 * Applied to sender-supplied text as well as our own, so a backend that puts
 * an address in a body cannot leak it onto a lock screen.
 */
fun redactNetworkAddresses(text: String): String =
    text.replace(MAC_PATTERN, "[address hidden]")
        .replace(IPV4_PATTERN, "[address hidden]")

// ============================================================
// SIMULATION SAFETY
// ============================================================

/**
 * Words that assert a real firewall change.
 *
 * A simulated run may not use one. This is the same guard the in-app
 * AUDIT_ONLY surfaces carry, applied at the point where NEXA has the least
 * control over the wording — the sender wrote it, and the operating system
 * will render it verbatim.
 */
private val MUTATION_CLAIMS = listOf(
    "quarantined",
    "released",
    "blocked",
    "enforced",
    "reconciled",
    "applied"
)

private const val SIMULATION_DISCLAIMER = "no firewall mutation occurred"

/** True when a simulated body claims a mutation without disclaiming it. */
fun claimsMutationWithoutDisclaimer(body: String): Boolean {
    val lower = body.lowercase()
    if (lower.contains(SIMULATION_DISCLAIMER)) return false
    return MUTATION_CLAIMS.any { lower.contains(it) }
}

/**
 * The body for a simulated action.
 *
 * If the sender described a mutation, the description is dropped rather than
 * shown with a correction bolted on. A notification is glanced at, and half a
 * sentence of a banner claiming a device was quarantined is worse than a
 * plain, accurate line.
 */
fun simulationBody(payload: PushPayload): String {
    val safe = if (claimsMutationWithoutDisclaimer(payload.body)) {
        "A simulated action completed."
    } else {
        payload.body
    }
    return "$safe No firewall mutation occurred."
}

// ============================================================
// CONTENT
// ============================================================

/**
 * Builds what the shade will show.
 *
 * Titles state the category rather than a conclusion. NEXA never adds a claim
 * the payload did not carry, and never upgrades a delivered message into a
 * statement about system state.
 */
fun notificationContentFor(payload: PushPayload): PushNotificationContent {
    val simulated = payload.executionMode == ExecutionMode.AuditOnly

    val channelId = when {
        simulated -> PushChannels.ACTION_RESULTS
        payload.sourceType == PushSourceType.Action -> PushChannels.ACTION_RESULTS
        payload.sourceType == PushSourceType.Alert &&
            payload.severity == PushSeverity.Critical -> PushChannels.CRITICAL_ALERTS
        else -> PushChannels.SECURITY_NOTICES
    }

    val importance = when (channelId) {
        PushChannels.CRITICAL_ALERTS -> PushImportance.High
        PushChannels.ACTION_RESULTS -> PushImportance.Default
        else -> PushImportance.Low
    }

    val title = when {
        simulated -> "SIMULATION"
        payload.sourceType == PushSourceType.Action -> actionTitle(payload)
        payload.sourceType == PushSourceType.Alert -> alertTitle(payload)
        payload.sourceType == PushSourceType.Identity -> "IDENTITY NOTICE"
        payload.sourceType == PushSourceType.Device -> "DEVICE NOTICE"
        else -> "SYSTEM STATUS"
    }

    val body = if (simulated) simulationBody(payload) else payload.body
    val text = redactNetworkAddresses("$body · ${payload.sourceId}")

    return PushNotificationContent(
        channelId = channelId,
        importance = importance,
        title = title,
        text = text,
        // The lock screen learns only that NEXA has something to say, and how
        // urgent it is. Everything else waits for an unlock.
        publicTitle = "NEXA",
        publicText = publicTextFor(payload, simulated),
        tag = payload.notificationId
    )
}

private fun alertTitle(payload: PushPayload): String = when (payload.severity) {
    PushSeverity.Critical -> "CRITICAL SECURITY ALERT"
    PushSeverity.Warning -> "SECURITY ALERT"
    PushSeverity.Information -> "SECURITY NOTICE"
}

/**
 * An action title states the mode, never the outcome.
 *
 * Whether the action succeeded is the enforcement pipeline's to report, and
 * the operator reads it from the record — not from a banner that arrived
 * before reconciliation finished.
 */
private fun actionTitle(payload: PushPayload): String = when (payload.executionMode) {
    ExecutionMode.AuditOnly -> "SIMULATION"
    ExecutionMode.Unknown -> "ACTION UPDATE — MODE UNKNOWN"
    ExecutionMode.Enforce, null -> "ACTION UPDATE"
}

private fun publicTextFor(payload: PushPayload, simulated: Boolean): String = when {
    simulated -> "A simulation result is available."
    payload.sourceType == PushSourceType.Alert &&
        payload.severity == PushSeverity.Critical -> "A critical security alert requires attention."
    payload.sourceType == PushSourceType.Alert -> "A security alert was raised."
    payload.sourceType == PushSourceType.Action -> "An action update is available."
    else -> "A security notification is available."
}
