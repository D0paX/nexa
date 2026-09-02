package com.example.nexa.push.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.util.Log
import com.example.nexa.NexaApplication
import com.example.nexa.push.PushInbox
import com.example.nexa.push.PushNotifier
import com.example.nexa.push.PushParseResult
import com.example.nexa.push.PushPayloadParser
import com.example.nexa.ui.common.DegradedScenario
import com.example.nexa.ui.realtime.PreviewRealtimeScenario
import com.example.nexa.ui.realtime.RealtimeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Injects a fixture payload so the push path can be exercised without a
 * Firebase project.
 *
 * Two things keep this out of anyone's way:
 *
 *  1. It lives in the debug source set, so the class and its manifest entry
 *     are not part of a release build at all.
 *  2. It re-checks the debuggable flag at runtime, so even a build that
 *     merged this manifest by mistake would refuse to act on the broadcast.
 *
 * It takes the *same* path a real message takes — parser, inbox, presenter —
 * rather than shortcutting into the inbox. A test route that skipped
 * validation would be testing a code path that never runs in production.
 *
 *   adb shell am broadcast \
 *     -n com.example.nexa/com.example.nexa.push.debug.NexaPushTestReceiver \
 *     -a com.example.nexa.debug.TEST_PUSH --es fixture critical_alert
 */
class NexaPushTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!context.isDebuggable()) {
            Log.w(TAG, "Refusing test push: build is not debuggable")
            return
        }
        if (intent.action == ACTION_TEST_STATE) {
            applyStateScenario(intent)
            return
        }
        if (intent.action == ACTION_TEST_REALTIME) {
            playRealtimeScenario(context)
            return
        }
        if (intent.action != ACTION_TEST_PUSH) return

        val name = intent.getStringExtra(EXTRA_FIXTURE) ?: DEFAULT_FIXTURE
        val fixture = PushFixtures.byName[name]
        if (fixture == null) {
            Log.w(TAG, "Unknown fixture: $name. Known: ${PushFixtures.byName.keys.sorted()}")
            return
        }

        when (val result = PushPayloadParser.parse(fixture)) {
            is PushParseResult.Rejected -> {
                Log.i(TAG, "Fixture '$name' rejected: ${result.reason} (${result.detail})")
                PushInbox.onRejectedPush(result)
            }
            is PushParseResult.Accepted -> {
                val isNew = PushInbox.onIncomingPush(result.payload)
                if (!isNew) {
                    Log.i(TAG, "Fixture '$name' is a duplicate; no second record created")
                    return
                }
                // Mirrors the service: the shade is used only when the
                // operator is not already looking at the app.
                if (!NexaApplication.isInForeground) {
                    PushNotifier.show(context.applicationContext, result.payload)
                } else {
                    Log.i(TAG, "Fixture '$name' recorded; app foregrounded so no shade post")
                }
            }
        }
    }

    /**
     * Plays the deterministic realtime scenario through the real transport.
     *
     * Same path a server stream would take: frames go through the parser, the
     * sequencer and the reducer. A trigger that injected state directly would
     * exercise a path that never runs in production.
     */
    private fun playRealtimeScenario(context: Context) {
        val app = context.applicationContext as? NexaApplication ?: return
        CoroutineScope(Dispatchers.Default).launch {
            // Re-anchor first. The scripted frames carry fixed sequences, and
            // a sequence the client has already applied is a replay it will
            // rightly refuse — so after anything else has published (an action
            // lifecycle, say) the scenario would silently do nothing. Asking
            // for a fresh snapshot is what a real client does when it needs a
            // new baseline, and it makes the scenario replayable on demand.
            RealtimeStore.anchor(
                sequence = PreviewRealtimeScenario.SNAPSHOT_SEQUENCE,
                scopes = PreviewRealtimeScenario.scopes
            )
            Log.i(TAG, "Playing preview realtime scenario")
            app.previewTransport.playScenario()
        }
    }

    /**
     * Switches the app into a degraded condition for review.
     *
     * Offline, stale, unavailable and degraded are the states nobody can
     * summon on demand — there is no backend to unplug — and they are exactly
     * the states where a security console is most likely to lie. This makes
     * them reviewable.
     *
     * Debug-only, twice over: the class is not compiled into a release build,
     * and the runtime check above refuses to act in a non-debuggable one.
     */
    private fun applyStateScenario(intent: Intent) {
        val name = intent.getStringExtra(EXTRA_SCENARIO)
        if (name.equals("clear", ignoreCase = true)) {
            DegradedScenario.activate(null)
            Log.i(TAG, "State scenario cleared")
            return
        }
        val scenario = DegradedScenario.Scenario.fromName(name)
        if (scenario == null) {
            Log.w(
                TAG,
                "Unknown scenario: $name. Known: " +
                    DegradedScenario.Scenario.entries.map { it.name.lowercase() } + " and 'clear'"
            )
            return
        }
        DegradedScenario.activate(scenario)
        Log.i(TAG, "State scenario active: $scenario")
    }

    private fun Context.isDebuggable(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private companion object {
        const val TAG = "NexaPushTest"
        const val ACTION_TEST_PUSH = "com.example.nexa.debug.TEST_PUSH"
        const val ACTION_TEST_REALTIME = "com.example.nexa.debug.TEST_REALTIME"
        const val ACTION_TEST_STATE = "com.example.nexa.debug.TEST_STATE"
        const val EXTRA_SCENARIO = "scenario"
        const val EXTRA_FIXTURE = "fixture"
        const val DEFAULT_FIXTURE = "critical_alert"
    }
}
