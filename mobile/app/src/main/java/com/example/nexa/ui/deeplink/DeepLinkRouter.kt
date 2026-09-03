package com.example.nexa.ui.deeplink

import android.util.Log
import androidx.navigation3.runtime.NavKey
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one way a link enters NEXA.
 *
 * Notifications, in-app references and external intents all arrive here and
 * get the same treatment: parse, validate, check access, check existence,
 * then navigate. Nothing downstream of this object performs URI parsing, and
 * nothing upstream of it knows what a screen is.
 */
object DeepLinkRouter {

    private val _pending = MutableStateFlow<DeepLinkResolution?>(null)

    /** The resolution waiting to be acted on by the navigation host. */
    val pending: StateFlow<DeepLinkResolution?> = _pending.asStateFlow()

    @Volatile
    private var resolver: NexaDeepLinkResolver = NexaDeepLinkResolver(PreviewDeepLinkCatalog)

    /** Swaps in a real data-backed catalog, or a policy once sessions exist. */
    fun configure(resolver: NexaDeepLinkResolver) {
        this.resolver = resolver
    }

    /** An untrusted URI, from wherever. */
    fun submit(raw: String?, source: DeepLinkSource) {
        val resolution = resolver.resolve(raw, source)
        // The link itself is never logged: identifiers name which incident or
        // which device someone is looking at.
        Log.i(TAG, "Deep link resolved: ${resolution.diagnostic()}")
        _pending.value = resolution
    }

    /** A link built internally from already-trusted values. */
    fun submit(link: NexaDeepLink) {
        _pending.value = resolver.resolve(link)
    }

    /** Called once the host has navigated, so rotation does not re-navigate. */
    fun consume() {
        _pending.value = null
    }

    private const val TAG = "NexaDeepLink"
}

/** Destination kind and outcome only. Never an identifier, never the raw URI. */
fun DeepLinkResolution.diagnostic(): String = when (this) {
    is DeepLinkResolution.Resolved -> "resolved ${link.redacted()}"
    is DeepLinkResolution.Invalid -> "invalid ($reason)"
    is DeepLinkResolution.UnsupportedVersion -> "unsupported version"
    is DeepLinkResolution.ObjectUnavailable -> "unavailable ($obj)"
    is DeepLinkResolution.Unauthorized -> "unauthorized"
    is DeepLinkResolution.SessionExpired -> "session expired"
}

// ============================================================
// NAVIGATION ADAPTER
// ============================================================

/**
 * The only place a NEXA link becomes a Compose destination.
 *
 * Exhaustive over a closed set, and every arm lands on a screen that reads
 * state. There is deliberately no arm producing ActionConfirmation: that
 * screen submits enforcement requests, and no link — internal, external,
 * forged or otherwise — can reach it. Responding to an incident still starts
 * from the incident.
 */
fun DeepLinkResolution.toNavKey(): NavKey = when (this) {
    is DeepLinkResolution.Resolved -> when (val target = link) {
        is NexaDeepLink.Overview -> Overview
        is NexaDeepLink.Devices -> Devices
        is NexaDeepLink.Alerts -> Alerts
        is NexaDeepLink.Audit -> Audit
        is NexaDeepLink.Notifications -> NotificationCenter
        is NexaDeepLink.Alert -> AlertDetail(target.alertId)
        // The address comes from the inventory, never from the link.
        is NexaDeepLink.Device -> DeviceDetail(resolvedDeviceMac ?: return linkProblem())
        is NexaDeepLink.Identity -> IdentityDetail(target.identityId)
        is NexaDeepLink.AuditRecord -> AuditDetail(target.eventId)
        is NexaDeepLink.Notification -> NotificationDetail(target.deliveryId)
    }
    else -> linkProblem()
}

/**
 * The surface an operator lands on when a link goes nowhere.
 *
 * Carries the finished operator-facing strings rather than a parser reason,
 * both chosen from a closed set of NEXA's own wording. Nothing from the link
 * reaches it.
 */
private fun DeepLinkResolution.linkProblem(): NavKey =
    LinkProblem(title = operatorTitle(), message = operatorMessage())
