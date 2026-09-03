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
    //
    // Only on a genuinely new start. onCreate also runs when the activity is
    // recreated — a rotation, a font-scale change, a theme change — and
    // getIntent() still returns the intent that launched the task however long
    // ago that was. Handling it again re-navigated the operator to the link's
    // destination on top of wherever they actually were: opening a device from
    // a notification and then rotating buried the action confirmation under a
    // second copy of the device screen, and every further recreation added
    // another pair. The back stack that Android restores already contains
    // where they were; a link that arrives after this point comes through
    // onNewIntent, which is unaffected.
    if (savedInstanceState == null) {
      handleDeepLinkIntent(intent)
    }

    // Screen capture is deliberately not blocked, and the reasoning belongs
    // next to where the flag would go rather than in a document nobody reads
    // beside this file.
    //
    // FLAG_SECURE stops screenshots and blanks the recents preview. What NEXA
    // shows is network posture an operator already administers — device
    // labels, MAC addresses, scopes, trust standing, enforcement state. It
    // shows no credential, no key material and no token: the identity screens
    // display a fingerprint and the push status a fingerprint, both of which
    // exist precisely so that the real values never appear. So the material
    // the flag would protect is not the material on screen.
    //
    // Against that, the flag is expensive in a way that is easy to
    // underestimate. Screenshots are how an operator escalates: the fastest
    // honest description of a quarantined device with a failed reconciliation
    // is a picture of the screen that says so. Blocking it does not stop
    // anyone who has the unlocked phone — they can simply open the app — it
    // stops the person trying to explain what they are looking at.
    //
    // The threat it does address is an attacker holding the unlocked device,
    // who has already won more than a screenshot. The trade is therefore
    // real usability against a marginal gain, and it is refused. If NEXA ever
    // displays key material, a session token or an operator credential, this
    // decision changes and this is the line to change.

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
