package com.example.nexa.ui.common

/**
 * Why a list is showing nothing.
 *
 * This is the distinction Phase 5.20 made for availability, applied one level
 * down. "Nothing matched what you asked for" and "there is nothing here" are
 * different facts, and both are different from "NEXA could not read this" —
 * which is not represented here at all, because it belongs to
 * [NexaAvailability] and is decided before a list is ever resolved.
 *
 * Keeping them apart is what stops a search for a device that does not exist
 * from reading like an inventory that is empty, or worse, like a service that
 * failed.
 */
sealed interface NexaResults {

    /** Records are being shown. */
    data object Present : NexaResults

    /**
     * The source genuinely holds nothing, and NEXA is sure of that.
     *
     * Distinct from every no-match case below: nothing was asked for and
     * nothing came back.
     */
    data object SourceEmpty : NexaResults

    /**
     * The source holds records; none of them survived what the operator asked
     * for. [reason] says which control did the narrowing, so the recovery
     * offered is the one that will actually help.
     */
    data class NoMatch(val reason: NexaNoMatchReason) : NexaResults

    val isEmpty: Boolean get() = this !is Present
}

enum class NexaNoMatchReason {
    /** Only the search query is narrowing. */
    Search,

    /** Only the filter set is narrowing. */
    Filters,

    /** Both are, and either could be the one to relax. */
    SearchAndFilters
}

/**
 * Classifies the state of a resolved list.
 *
 * [sourceCount] is the authoritative collection the screen holds — before
 * search and filters, after availability has already decided the data is
 * readable at all. [visibleCount] is what came out of the pipeline.
 */
fun nexaResults(
    sourceCount: Int,
    visibleCount: Int,
    queryActive: Boolean,
    filtersActive: Boolean
): NexaResults = when {
    visibleCount > 0 -> NexaResults.Present
    sourceCount == 0 -> NexaResults.SourceEmpty
    queryActive && filtersActive -> NexaResults.NoMatch(NexaNoMatchReason.SearchAndFilters)
    queryActive -> NexaResults.NoMatch(NexaNoMatchReason.Search)
    filtersActive -> NexaResults.NoMatch(NexaNoMatchReason.Filters)
    // Nothing is narrowing and nothing is visible from a non-empty source.
    // That should be unreachable; reporting it as empty is the honest
    // fallback, and it is never reported as a match failure the operator
    // could fix by clearing a control they never touched.
    else -> NexaResults.SourceEmpty
}

/**
 * The count shown beside a list.
 *
 * The rule is that the number must describe the list under it. When nothing
 * is narrowing, that is simply the total. When something is, the visible
 * count leads and the total follows, so an operator can see both that four
 * devices are shown and that the inventory holds twelve — without ever being
 * told "12 devices" above a list of four.
 */
fun resultCountLabel(
    visibleCount: Int,
    sourceCount: Int,
    noun: String,
    pluralNoun: String = "${noun}s"
): String {
    val word = if (visibleCount == 1) noun else pluralNoun
    return if (visibleCount == sourceCount) {
        "$visibleCount $word"
    } else {
        "$visibleCount of $sourceCount $pluralNoun"
    }
}

/**
 * A count and its noun, agreeing.
 *
 * The screens were writing "3 device(s) carry an existing enforcement
 * binding" and "1 notification delivery failure(s)" — the parenthesis that
 * means a developer had a count and did not want to think about grammar. It
 * reads as unfinished, and on the command centre it is the first sentence an
 * operator sees.
 *
 * [resultCountLabel] above already knew how to do this for lists; this is the
 * same rule for a count that appears inside a sentence. Same default, same
 * override for the words English declines to be regular about.
 */
fun countLabel(count: Int, noun: String, pluralNoun: String = "${noun}s"): String =
    "$count ${if (count == 1) noun else pluralNoun}"
