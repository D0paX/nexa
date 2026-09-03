package com.example.nexa.push

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.nexa.MainActivity
import com.example.nexa.R
import com.example.nexa.ui.deeplink.toUri

/**
 * Posts NEXA notifications through the platform.
 *
 * Deliberately thin: what the notification *says* is decided by
 * [notificationContentFor], which is pure and tested. This file only knows
 * how to hand that to Android.
 *
 * No security logic lives here. Nothing in this file decides whether an
 * action ran, whether a device is trusted, or what state anything is in.
 */
object PushNotifier {

    /**
     * Registers the channels.
     *
     * Called once at process start. Channels are how Android lets an operator
     * silence the noisy parts of an app without silencing the urgent ones, so
     * NEXA gives them three real levels rather than routing everything
     * through one and calling it all important.
     */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                PushChannels.CRITICAL_ALERTS,
                context.getString(R.string.channel_critical_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_critical_alerts_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                PushChannels.SECURITY_NOTICES,
                context.getString(R.string.channel_security_notices),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_security_notices_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )

        manager.createNotificationChannel(
            NotificationChannel(
                PushChannels.ACTION_RESULTS,
                context.getString(R.string.channel_action_results),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_action_results_description)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
    }

    /**
     * Shows a validated message.
     *
     * Returns false when the platform will not display it — permission not
     * granted, or the operator has silenced the channel. That is their
     * decision and NEXA does not work around it.
     */
    fun show(context: Context, payload: PushPayload): Boolean {
        val content = notificationContentFor(payload)
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false

        val notification = NotificationCompat.Builder(context, content.channelId)
            // A monochrome status-bar glyph, as Android requires. The
            // full-colour launcher icon is a different asset for a different
            // job and is left alone.
            .setSmallIcon(R.drawable.ic_stat_nexa)
            .setColor(BRAND_ACCENT)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.text))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            // Full text only once the device is unlocked. The lock screen gets
            // the generic version below.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(context, content))
            .setContentIntent(contentIntent(context, payload))
            // No action buttons. A one-tap enforcement command in the shade
            // would bypass every confirmation the product is built around.
            .build()

        return try {
            manager.notify(content.tag, NOTIFICATION_ID, notification)
            true
        } catch (security: SecurityException) {
            // Permission revoked between the check and the post.
            false
        }
    }

    fun cancel(context: Context, notificationId: String) {
        NotificationManagerCompat.from(context).cancel(notificationId, NOTIFICATION_ID)
    }

    private fun publicVersion(context: Context, content: PushNotificationContent): Notification =
        NotificationCompat.Builder(context, content.channelId)
            .setSmallIcon(R.drawable.ic_stat_nexa)
            .setColor(BRAND_ACCENT)
            .setContentTitle(content.publicTitle)
            .setContentText(content.publicText)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

    /**
     * The tap target.
     *
     * Carries a canonical NEXA deep link, which is re-parsed and re-validated
     * on arrival exactly like a link from anywhere else. Routing the internal
     * path through the same text form the outside world uses means it gets
     * the same scrutiny, rather than a trusted shortcut nobody tests.
     *
     * A link cannot carry a command: the format has no way to express one.
     */
    private fun contentIntent(context: Context, payload: PushPayload): PendingIntent {
        val link = deepLinkFor(payload)
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(link.toUri()))
            .putExtra(EXTRA_FROM_NOTIFICATION, true)
            // Resume the existing task rather than stacking a second copy of
            // the app behind the notification.
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        return PendingIntent.getActivity(
            context,
            payload.notificationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Marks an intent NEXA itself built for a notification tap.
     *
     * Used only to label the source for navigation purposes. It is forgeable
     * like any extra, and nothing security-relevant reads it — a link is
     * validated identically whatever it claims about where it came from.
     */
    const val EXTRA_FROM_NOTIFICATION = "com.example.nexa.push.FROM_NOTIFICATION"

    /** NEXA red, used only as the small-icon accent Android tints. */
    private const val BRAND_ACCENT = 0xFFD11A2A.toInt()

    /** One id, distinguished by tag, so a repeat replaces rather than stacks. */
    private const val NOTIFICATION_ID = 1
}
