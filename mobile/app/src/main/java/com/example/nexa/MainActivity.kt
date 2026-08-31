package com.example.nexa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.nexa.push.PushNotifier
import com.example.nexa.ui.deeplink.DeepLinkRouter
import com.example.nexa.ui.deeplink.DeepLinkSource
import com.example.nexa.theme.NexaAtmosphere
import com.example.nexa.theme.NexaTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Cold start from a link. The router holds the resolution until the
    // navigation host composes and consumes it, so nothing races the graph
    // being ready.
    handleDeepLinkIntent(intent)

    enableEdgeToEdge()
    setContent {
      NexaTheme {
        NexaAtmosphere {
            MainNavigation()
        }
      }
    }
  }

  /** A link arriving while the task was already running. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLinkIntent(intent)
  }

  /**
   * Hands an incoming link to the router.
   *
   * The activity does no parsing of its own. It reads the URI off the intent
   * and passes the string on; validation, access and existence are all the
   * router's job, and it treats this string exactly as it treats one typed
   * into a browser.
   *
   * The source hint is derived from the intent's own action rather than from
   * anything inside the link, and it is used for navigation behaviour only —
   * no security decision reads it. A link is not trusted for arriving from a
   * notification.
   */
  private fun handleDeepLinkIntent(intent: Intent?) {
    val uri = intent?.data?.toString() ?: return
    val source = if (intent.getBooleanExtra(PushNotifier.EXTRA_FROM_NOTIFICATION, false)) {
      DeepLinkSource.Notification
    } else {
      DeepLinkSource.External
    }
    DeepLinkRouter.submit(uri, source)
  }
}
