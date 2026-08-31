package com.example.nexa.ui.deeplink

import com.example.nexa.push.PushInbox
import com.example.nexa.ui.alerts.AlertDetailUiState
import com.example.nexa.ui.alerts.AlertsPreview
import com.example.nexa.ui.audit.AuditDetailUiState
import com.example.nexa.ui.audit.AuditPreview
import com.example.nexa.ui.devices.DevicesPreview
import com.example.nexa.ui.identity.IdentityDetailUiState
import com.example.nexa.ui.identity.IdentityPreview
import com.example.nexa.ui.notifications.NotificationDetailUiState
import com.example.nexa.ui.notifications.NotificationPreview

/**
 * Existence, answered by the sources the app currently reads.
 *
 * These are the same preview sources every screen uses, so a link resolves
 * against exactly what the operator would see if they navigated by hand. When
 * a real data layer arrives, this class is what gets replaced — the resolver
 * above it does not change.
 *
 * Every method asks about now. A link created yesterday about an alert that
 * has since been archived resolves to unavailable, because that is what the
 * source says today.
 */
object PreviewDeepLinkCatalog : DeepLinkCatalog {

    override fun alertExists(alertId: String): Boolean =
        AlertsPreview.detailFor(alertId) is AlertDetailUiState.Content

    /**
     * Looks the device up by its stable record identifier and returns the
     * address it currently has.
     *
     * The link never carries the address. Reading it fresh here is what makes
     * an old link safe: if the device has been re-addressed since, the route
     * follows the device, not the stale value someone else wrote down.
     */
    override fun currentMacForDevice(deviceId: String): String? =
        DevicesPreview.inventory.firstOrNull { it.id.equals(deviceId, ignoreCase = true) }?.mac

    override fun identityExists(identityId: String): Boolean =
        IdentityPreview.detailFor(identityId) is IdentityDetailUiState.Content

    override fun auditEventExists(eventId: String): Boolean =
        AuditPreview.detailFor(eventId) is AuditDetailUiState.Content

    /**
     * Delivery records live in two places: the preview history, and messages
     * that arrived on this device during this session.
     */
    override fun deliveryExists(deliveryId: String): Boolean {
        if (NotificationPreview.detailFor(deliveryId) is NotificationDetailUiState.Content) {
            return true
        }
        return PushInbox.records.value.any { it.id.equals(deliveryId, ignoreCase = true) }
    }
}
