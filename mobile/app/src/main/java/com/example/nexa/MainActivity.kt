package com.example.nexa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nexa.push.PushIntentKeys
import com.example.nexa.push.PushNavigation
import com.example.nexa.push.pushDestinationFromExtras
import com.example.nexa.theme.NexaAtmosphere
import com.example.nexa.theme.NexaTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Cold start from a notification tap: the intent is already here, and the
    // navigation host picks the destination up as soon as it composes.
    handlePushIntent(intent)

    enableEdgeToEdge()
    setContent {
      NexaTheme {
        NexaAtmosphere {
            MainNavigation()
        }
      }
    }
  }

  /** A tap while the task was already running. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handlePushIntent(intent)
  }

  /**
   * Turns notification extras into a navigation request.
   *
   * The extras are re-validated on the way in. Any app on the device can
   * construct an intent with these names, so what arrives here gets the same
   * treatment as what arrives off the network: a destination kind from a
   * closed vocabulary and an identifier that passes the same checks, or
   * nothing happens.
   *
   * A tap can only ever ask to be shown something. There is no encoding for
   * an action, so there is nothing here that could execute one.
   */
  private fun handlePushIntent(intent: Intent?) {
    val kind = intent?.getStringExtra(PushIntentKeys.EXTRA_DESTINATION) ?: return
    val id = intent.getStringExtra(PushIntentKeys.EXTRA_ID)
    val destination = pushDestinationFromExtras(kind, id) ?: return
    PushNavigation.request(destination)
  }
}
