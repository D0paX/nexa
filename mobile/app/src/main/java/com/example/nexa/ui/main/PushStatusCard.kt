package com.example.nexa.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType
import com.example.nexa.theme.NexaWarning
import com.example.nexa.push.PushTokenManager
import com.example.nexa.push.PushTokenState
import com.example.nexa.ui.components.GlassSurface
import com.example.nexa.ui.components.NexaIcon
import com.example.nexa.ui.components.NexaOutlinedButton

/**
 * Whether this device can receive push messages, and what to do if it cannot.
 *
 * It lives on the Notification Center rather than firing on first launch,
 * because this is the one screen where the permission is obviously about
 * something: an operator reading delivery records is exactly the operator who
 * has a reason to decide about notifications.
 *
 * The wording is careful in one particular way. Notifications being off is a
 * delivery limitation and nothing more — NEXA keeps observing, alerting,
 * enforcing and recording either way. A card that implied otherwise would be
 * pressuring the operator with a false claim about their security posture.
 */
@Composable
fun PushStatusCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tokenState by PushTokenManager.state.collectAsStateWithLifecycle()

    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    // Set once the operator has been asked and said no, which is when the
    // system dialog stops appearing and the settings route becomes the only
    // honest thing to offer.
    var permissionRefused by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsEnabled = granted
        permissionRefused = !granted
    }

    if (notificationsEnabled) {
        DeliveryTransportRow(tokenState)
        return
    }

    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = NexaIcons.Offline,
                    size = NexaTokens.IconMedium,
                    tint = NexaWarning
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(
                    text = "Push delivery is off",
                    style = NexaType.Title,
                    color = NexaTextPrimary
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
            Text(
                text = "NEXA cannot notify this device. Observation, alerting and enforcement are unaffected — this changes how you are told, not what the system does.",
                style = NexaType.BodySecondary,
                color = NexaTextSecondary
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))

            if (permissionRefused || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                // Either the operator has already declined, or the platform has
                // no runtime permission to ask for and the switch lives in
                // settings. Either way NEXA points at the system control rather
                // than trying to work around it.
                NexaOutlinedButton(
                    text = "Open notification settings",
                    onClick = { context.openNotificationSettings() },
                    icon = NexaIcons.Forward
                )
            } else {
                NexaOutlinedButton(
                    text = "Enable notifications",
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    icon = NexaIcons.NotificationDelivery
                )
            }
        }
    }
}

/**
 * Whether a delivery endpoint exists for this installation.
 *
 * Shows a fingerprint, never the registration token. The fingerprint is
 * enough to compare two installations during a support conversation and
 * discloses nothing that could be used to address this device.
 */
@Composable
private fun DeliveryTransportRow(state: PushTokenState) {
    val text = when (state) {
        is PushTokenState.Available -> "Push delivery ready · endpoint ${state.fingerprint}"
        is PushTokenState.Unavailable -> state.reason
        PushTokenState.Unknown -> "Push delivery status not determined yet."
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        NexaIcon(
            icon = NexaIcons.NotificationDelivery,
            size = NexaTokens.IconSmall,
            tint = NexaTextMuted
        )
        Spacer(modifier = Modifier.width(NexaTokens.SpacingXSmall))
        Text(text = text, style = NexaType.Metadata, color = NexaTextMuted)
    }
}

private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
