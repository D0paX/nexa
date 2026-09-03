package com.example.nexa.ui.workflow

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.nexa.MainActivity
import com.example.nexa.ui.deeplink.DeepLinkRouter
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * INSTRUMENTED — a link is acted on once, not once per configuration change.
 *
 * `onCreate` runs again every time the activity is recreated: a rotation, a
 * font-scale change, a theme change, a locale change. `getIntent()` still
 * returns the intent that launched the task, however long ago that was, so
 * handling it there without asking whether this is a new start re-navigates
 * the operator to the link's destination on top of wherever they actually
 * were.
 *
 * Found in QA on a Galaxy S24. Opening a device from a link, starting an
 * action, and rotating buried the confirmation under a second copy of the
 * device screen — and every further rotation added another pair, so Back
 * walked out through "Device Context, Confirm Action, Device Context,
 * Confirm Action". Nothing executed and nothing was lost, but the operator
 * was moved without asking and the stack grew without bound.
 *
 * The router already knew the shape of this: [DeepLinkRouter.consume] is
 * documented as being called "once the host has navigated, so rotation does
 * not re-navigate". Consuming worked. The activity submitted the same link
 * again afterwards.
 *
 * Asserted through the screen rather than through the router, because the
 * router is empty either way by the time anything can look at it — the host
 * consumes a re-submitted link as promptly as a new one. What differs is
 * where the operator ends up, so that is what this checks.
 */
class LaunchIntentRecreationTest {

    @get:Rule
    val rule = createEmptyComposeRule()

    private val deviceLink = "nexa://v1/device/DEV-1001"

    /** The screen a device link opens. */
    private val linkDestination = "Device Context"

    @Before
    fun setUp() = DeepLinkRouter.consume()

    @After
    fun tearDown() = DeepLinkRouter.consume()

    private fun launchIntent() = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(deviceLink),
        ApplicationProvider.getApplicationContext(),
        MainActivity::class.java
    )

    // ============================================================
    // THE DEFECT
    // ============================================================

    @Test
    fun a_recreation_does_not_navigate_back_to_the_launch_link() {
        ActivityScenario.launch<MainActivity>(launchIntent()).use { scenario ->
            rule.waitForIdle()
            rule.onNodeWithText(linkDestination).assertIsDisplayed()

            // Walk away from the link's destination the ordinary way.
            rule.onNodeWithContentDescription("Back").performClick()
            rule.waitForIdle()
            assertEquals(
                "Back did not leave the link's destination",
                0,
                rule.onAllNodesWithText(linkDestination).fetchSemanticsNodes().size
            )

            scenario.recreate()
            rule.waitForIdle()

            // Still where the operator went, not back where the link pointed.
            assertEquals(
                "the launch link was delivered again and moved the operator",
                0,
                rule.onAllNodesWithText(linkDestination).fetchSemanticsNodes().size
            )
        }
    }

    /** And not on the second, third or fourth recreation either. */
    @Test
    fun repeated_recreations_never_navigate_back_to_it() {
        ActivityScenario.launch<MainActivity>(launchIntent()).use { scenario ->
            rule.waitForIdle()
            rule.onNodeWithContentDescription("Back").performClick()
            rule.waitForIdle()

            repeat(4) { attempt ->
                scenario.recreate()
                rule.waitForIdle()
                assertEquals(
                    "the link came back on recreation number ${attempt + 1}",
                    0,
                    rule.onAllNodesWithText(linkDestination).fetchSemanticsNodes().size
                )
            }
        }
    }

    // ============================================================
    // WITHOUT LOSING WHAT THE LINK IS FOR
    // ============================================================

    /**
     * The link still works. A test that only proved recreation changes
     * nothing would also pass if links had stopped opening anything.
     */
    @Test
    fun the_launch_link_still_opens_its_destination() {
        ActivityScenario.launch<MainActivity>(launchIntent()).use {
            rule.waitForIdle()
            rule.onNodeWithText(linkDestination).assertIsDisplayed()
        }
    }

    /** And a recreation leaves the destination in place when nobody left it. */
    @Test
    fun staying_on_the_destination_survives_a_recreation() {
        ActivityScenario.launch<MainActivity>(launchIntent()).use { scenario ->
            rule.waitForIdle()
            rule.onNodeWithText(linkDestination).assertIsDisplayed()

            scenario.recreate()
            rule.waitForIdle()

            rule.onNodeWithText(linkDestination).assertIsDisplayed()
        }
    }
}
