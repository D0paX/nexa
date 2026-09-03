package com.example.nexa.ui.common

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * How NEXA speaks to a screen reader.
 *
 * The rule this file exists to hold: the screen-reader user and the sighted
 * user must receive the same truth. Not a summary of it, not a friendlier
 * version of it, and never a more confident one. Everything that matters on
 * this product is a distinction — trusted is not authorized, simulation is
 * not live, stale is not current — and an accessibility layer that blurs any
 * of them has built a second, less careful NEXA.
 *
 * The second rule is quieter but shapes most of the code below: an accessible
 * interface is not one where every element speaks. It is one where the *right*
 * units speak. A device row that announces its own icon, then its name, then
 * its presence icon, then its presence, then its trust icon, then its trust,
 * is technically labelled and practically unusable. Grouping is the work.
 *
 * Nothing here decides what is true. These helpers take a label the product
 * already computed and attach it to the semantics tree.
 */

/**
 * Marks text as a heading so a screen reader can jump between sections.
 *
 * Used sparingly: screen titles and the major section divisions. Marking
 * every label a heading turns heading navigation back into linear navigation,
 * which is the thing it exists to avoid.
 */
fun Modifier.nexaHeading(): Modifier = semantics { heading() }

/**
 * Makes a composite row one accessible item with one action.
 *
 * A row whose whole surface is clickable should be a single stop that says
 * what it contains and what activating it does. Without merging, the click
 * target and its several text children are separate stops, and the user hears
 * the row's contents twice — once as the row and once as its parts.
 *
 * [actionLabel] describes what activation does ("Open device details"), not
 * what the row is; the merged children already say that.
 */
fun Modifier.nexaRowAction(spoken: String, actionLabel: String? = null): Modifier =
    // Cleared rather than merged. Merging leaves the children in the tree as
    // their own focus stops, so a five-part device row stays five swipes and
    // the operator hears its presence twice — once from the icon and once
    // from the line that already said it. Clearing makes the row the unit it
    // looks like, with one sentence assembled in a known order.
    //
    // Safe only because these rows have no interactive children. A row that
    // contained its own controls must not use this: it would swallow them.
    clearAndSetSemantics {
        contentDescription = spoken
        if (actionLabel != null) onClick(label = actionLabel) { false }
    }

/**
 * Marks a control the user can see but cannot currently use.
 *
 * Dimming alone is invisible to a screen reader, and removing the control
 * entirely is worse: something the operator expected to find has silently
 * vanished. This keeps it in the tree, announced as disabled, with the reason
 * available where one exists.
 */
fun Modifier.nexaDisabled(reason: String? = null): Modifier =
    semantics {
        disabled()
        if (reason != null) stateDescription = reason
    }

/**
 * Whether a selectable value is currently chosen.
 *
 * Attached alongside the `selected` semantic rather than instead of it: the
 * boolean is what assistive technology filters and navigates by, and the
 * spoken phrase is what a person hears. Colour is never the carrier.
 */
fun Modifier.nexaSelection(selected: Boolean): Modifier =
    semantics { stateDescription = if (selected) "Selected" else "Not selected" }

/**
 * The spoken form of the filter control's label.
 *
 * "Filters · 3" is right on screen and wrong out loud — a screen reader reads
 * the separator, and "filters middle dot three" is not a sentence. The
 * visible label keeps its typography; this says what it means.
 */
fun filterCountSpoken(activeCount: Int): String = when {
    activeCount <= 0 -> "Filters"
    activeCount == 1 -> "Filters, 1 active"
    else -> "Filters, $activeCount active"
}

/**
 * The spoken form of a result count.
 *
 * Mirrors [resultCountLabel] so the two can never drift, and expands the
 * shorthand the visible label uses: "4 of 12 devices" is already a sentence,
 * but a bare "8 devices" benefits from saying whether anything is narrowing
 * the list.
 */
fun resultCountSpoken(
    visibleCount: Int,
    sourceCount: Int,
    noun: String,
    pluralNoun: String = "${noun}s"
): String {
    val word = if (visibleCount == 1) noun else pluralNoun
    return if (visibleCount == sourceCount) {
        "$visibleCount $word"
    } else {
        "Showing $visibleCount of $sourceCount $pluralNoun"
    }
}

/**
 * A summary line, said rather than punctuated.
 *
 * The counts across the product are joined with a middle dot, which reads
 * cleanly and speaks badly — a screen reader announces the separator itself.
 * The typography keeps its shorthand and the announcement gets commas.
 *
 * Deliberately a transformation of the visible string rather than a second
 * way of computing the same numbers. Two count implementations would be two
 * chances to disagree, and the sighted and spoken answers must match.
 */
fun spokenSummaryLine(label: String): String =
    label.replace(" · ", ", ").replace("·", ",")
