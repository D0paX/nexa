package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.NexaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Two metrics in a row end up the same height.
 *
 * Found on a physical Galaxy S24: at 360dp the device caption wrapped to two
 * lines and the alert caption did not, so the cards measured 376px and 328px
 * and their bottom edges sat 16dp apart. The emulator is wider, both captions
 * fit on one line, and the pair looked correct — which is why the defect was
 * invisible until it ran on a phone.
 *
 * These run on a device because that is the only place the question can be
 * asked. Nothing in a JVM test lays text out, so nothing in a JVM test can
 * tell you whether a caption wrapped.
 */
class MetricRowTest {

    @get:Rule
    val rule = createComposeRule()

    private val wrappingCaption = "38 online · 3 quarantined · 2 unverified"
    private val shortCaption = "2 unacknowledged"

    /** Narrow enough that the longer caption has to wrap. */
    private val narrow = 340.dp

    @androidx.compose.runtime.Composable
    private fun Pair(fontScale: Float = 1f) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale)
        ) {
            NexaTheme {
                Box(modifier = Modifier.width(narrow)) {
                    MetricRow {
                        MetricSurface(
                            title = "ACTIVE DEVICES",
                            value = "42",
                            caption = wrappingCaption,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("devices")
                        )
                        MetricSurface(
                            title = "ACTIVE ALERTS",
                            value = "2",
                            caption = shortCaption,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .testTag("alerts")
                        )
                    }
                }
            }
        }
    }

    private fun heightOf(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().size.height

    private fun topOf(tag: String) =
        rule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y

    // ============================================================
    // THE DEFECT
    // ============================================================

    @Test
    fun cards_sharing_a_row_share_one_height() {
        rule.setContent { Pair() }
        assertEquals(
            "the pair measured independently again",
            heightOf("devices"),
            heightOf("alerts")
        )
    }

    @Test
    fun both_edges_align() {
        rule.setContent { Pair() }
        assertEquals(topOf("devices"), topOf("alerts"), 0.5f)
        assertEquals(
            topOf("devices") + heightOf("devices"),
            topOf("alerts") + heightOf("alerts"),
            0.5f
        )
    }

    /**
     * The row grew to the taller card rather than the shorter one shrinking
     * the other — which is what a truncated caption would look like from here.
     */
    @Test
    fun the_row_takes_the_taller_card_s_height() {
        rule.setContent {
            NexaTheme {
                Column {
                    // The short card on its own, at the width it gets in the
                    // row, so the comparison is like for like.
                    Box(modifier = Modifier.width(narrow / 2)) {
                        MetricSurface(
                            title = "ACTIVE ALERTS",
                            value = "2",
                            caption = shortCaption,
                            modifier = Modifier.testTag("solo")
                        )
                    }
                    Pair()
                }
            }
        }

        assertTrue(
            "the shared height is no taller than the short card measured alone",
            heightOf("alerts") > heightOf("solo")
        )
    }

    // ============================================================
    // WITHOUT LOSING ANYTHING
    // ============================================================

    @Test
    fun no_caption_is_dropped_to_make_the_heights_match() {
        rule.setContent { Pair() }
        rule.onNodeWithText(wrappingCaption).assertIsDisplayed()
        rule.onNodeWithText(shortCaption).assertIsDisplayed()
    }

    @Test
    fun titles_and_values_survive() {
        rule.setContent { Pair() }
        rule.onNodeWithText("ACTIVE DEVICES").assertIsDisplayed()
        rule.onNodeWithText("ACTIVE ALERTS").assertIsDisplayed()
        rule.onNodeWithText("42").assertIsDisplayed()
        rule.onNodeWithText("2").assertIsDisplayed()
    }

    // ============================================================
    // AND AT A LARGER TEXT SIZE
    // ============================================================

    /**
     * A bigger font makes more captions wrap, not fewer — the case where the
     * old layout was most visibly wrong.
     */
    @Test
    fun the_heights_still_match_at_a_larger_font_scale() {
        rule.setContent { Pair(fontScale = 1.5f) }
        assertEquals(heightOf("devices"), heightOf("alerts"))
    }

    @Test
    fun nothing_is_dropped_at_a_larger_font_scale() {
        rule.setContent { Pair(fontScale = 1.5f) }
        rule.onNodeWithText(wrappingCaption).assertIsDisplayed()
        rule.onNodeWithText(shortCaption).assertIsDisplayed()
    }
}
