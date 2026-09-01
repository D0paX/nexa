package com.example.nexa

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.nexa.push.PushNotifier
import com.example.nexa.push.PushTokenManager
import com.example.nexa.ui.realtime.PreviewRealtimeScenario
import com.example.nexa.ui.realtime.PreviewRealtimeTransport
import com.example.nexa.ui.realtime.RealtimeConnectionManager

/**
 * Process startup.
 *
 * The notification subsystem is set up exactly once here, rather than from a
 * composable that could run again on any recomposition.
 */
class NexaApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        PushNotifier.ensureChannels(this)
        registerActivityLifecycleCallbacks(ForegroundTracker)

        // Asks Firebase for the current token. Without a google-services.json
        // this settles on "transport unavailable" and the app carries on:
        // push is a delivery convenience, not a dependency of the product.
        PushTokenManager.refresh()

        // The realtime transport. No NEXA backend publishes a stream yet, so
        // this is the deterministic preview one; a real transport replaces it
        // here and nothing above this line changes.
        RealtimeConnectionManager.configure(
            transport = previewTransport,
            scopes = PreviewRealtimeScenario.scopes
        )
    }

    /** Kept so the debug trigger can drive the same instance the app uses. */
    val previewTransport = PreviewRealtimeTransport()

    /**
     * Whether an operator is currently looking at NEXA.
     *
     * Used for one decision: a message that arrives while the app is open goes
     * to the Notification Center only. Posting it to the shade as well would
     * show the same fact twice on the same screen.
     */
    companion object {
        @Volatile
        var isInForeground: Boolean = false
            private set
    }

    private object ForegroundTracker : ActivityLifecycleCallbacks {
        private var startedActivities = 0

        override fun onActivityStarted(activity: Activity) {
            startedActivities += 1
            isInForeground = startedActivities > 0
            // A console in front of an operator keeps a live stream; one that
            // is not does not hold a socket open for nothing.
            RealtimeConnectionManager.onForeground()
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities = (startedActivities - 1).coerceAtLeast(0)
            isInForeground = startedActivities > 0
            if (!isInForeground) RealtimeConnectionManager.onBackground()
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
