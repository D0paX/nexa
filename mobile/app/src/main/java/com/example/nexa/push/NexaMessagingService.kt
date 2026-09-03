package com.example.nexa.push

import android.util.Log
import com.example.nexa.NexaApplication
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The FCM entry point.
 *
 * Deliberately the thinnest layer in the push stack. It validates, records
 * and shows — it does not decide anything about security state, and it does
 * not know what an alert or an action is beyond an identifier.
 *
 * Firebase calls these methods on a background thread, so parsing and
 * validation never touch the main thread. Work that outlives the callback
 * runs on [scope].
 */
class NexaMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * A new registration token.
     *
     * The token is wrapped immediately, so from this line onwards the raw
     * value cannot reach a log, a crash report or a string template by
     * accident. Only the fingerprint is ever recorded.
     */
    override fun onNewToken(token: String) {
        val wrapped = PushToken(token)
        Log.i(TAG, "Registration token refreshed (fp=${wrapped.fingerprint})")
        scope.launch {
            PushTokenManager.onTokenRefreshed(wrapped)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        // Data-only messages, so NEXA controls presentation in every app
        // state. A notification-payload message would be rendered by the
        // system before this code could check anything about it.
        when (val result = PushPayloadParser.parse(message.data)) {
            is PushParseResult.Rejected -> {
                // The reason and the field name. Never the payload: it failed
                // validation, which is exactly when its contents are least
                // trustworthy.
                Log.w(TAG, "Rejected push: ${result.reason} (${result.detail})")
                PushInbox.onRejectedPush(result)
            }

            is PushParseResult.Accepted -> {
                val payload = result.payload
                val isNew = PushInbox.onIncomingPush(payload)
                if (!isNew) {
                    Log.i(TAG, "Duplicate push ignored (${payload.notificationId})")
                    return
                }

                // With the app in front of the operator, the Notification
                // Center already shows the record. Posting a shade
                // notification on top of it would be the same fact twice.
                if (!NexaApplication.isInForeground) {
                    PushNotifier.show(applicationContext, payload)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private companion object {
        const val TAG = "NexaPush"
    }
}
