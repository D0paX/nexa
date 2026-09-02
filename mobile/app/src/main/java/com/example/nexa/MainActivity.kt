package com.example.nexa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import com.example.nexa.push.PushNotifier
import com.example.nexa.ui.deeplink.DeepLinkRouter
import com.example.nexa.ui.deeplink.DeepLinkSource
import com.example.nexa.theme.NexaAtmosphere
import com.example.nexa.theme.NexaSystemBars
import com.example.nexa.theme.SystemBarIcons
import com.example.nexa.theme.NexaTheme

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Cold start from a link. The router holds the resolution until the
    // navigation host composes and consumes it, so nothing races the graph
    // being ready.
    handleDeepLinkIntent(intent)

    // Edge to edge, with the system's indicators told which surface they are
    // being drawn over. Without the styles this call resolves the appearance
    // from the phone's night setting, which NEXA does not follow: the app
    // paints the same light surface either way, so on a phone in dark mode the
    // clock, signal, SIM and battery came out white on near-white.
    //
    // The scrim argument is the colour to put behind a bar. It stays
    // transparent, so the light NEXA surface continues under the bars exactly
    // as before and nothing is painted to hide the problem. The second is a
    // fallback the platform reaches for only where it cannot invert its own
    // icons at all — the navigation bar below API 26 — where a faint wash is
    // the difference between readable indicators and none.
    enableEdgeToEdge(
      statusBarStyle = nexaSystemBarStyle(),
      navigationBarStyle = nexaSystemBarStyle()
    )
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

/**
 * The bar style [NexaSystemBars] asks for, expressed in platform terms.
 *
 * Both bars get the same answer because they sit over the same surface. It is
 * resolved once, when the window is created, rather than watched or recomposed
 * — the appearance is a property of the window, not of anything on screen.
 */
private fun nexaSystemBarStyle(): SystemBarStyle =
  when (NexaSystemBars.icons()) {
    SystemBarIcons.Dark -> SystemBarStyle.light(
      scrim = android.graphics.Color.TRANSPARENT,
      darkScrim = LEGACY_BAR_SCRIM
    )
    SystemBarIcons.Light -> SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT)
  }

/**
 * Reached only on API levels that cannot draw dark navigation-bar icons.
 * Everything from API 26 up inverts the icons instead and leaves the bar
 * transparent.
 */
private const val LEGACY_BAR_SCRIM = 0x40000000
