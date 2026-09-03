package com.example.nexa.ui.common

/**
 * How current a piece of displayed state is.
 *
 * Shared by every feature that shows observed system state, because the
 * rule is the same everywhere: NEXA never presents an old observation as
 * current reality, and never claims certainty it does not have.
 */
sealed interface DataFreshness {
    /** Confirmed just now. */
    data object Live : DataFreshness

    /** Real, but old enough that it should not be trusted for a decision. */
    data class Stale(val lastUpdatedLabel: String) : DataFreshness

    /** NEXA does not know how current this is. */
    data object Unknown : DataFreshness
}

/** Stated plainly — never implying more certainty than NEXA has. */
val DataFreshness.label: String
    get() = when (this) {
        is DataFreshness.Live -> "Updated just now"
        is DataFreshness.Stale -> lastUpdatedLabel
        is DataFreshness.Unknown -> "Current state unavailable"
    }

/** Only a live confirmation is safe to act on without qualification. */
val DataFreshness.isTrustworthy: Boolean
    get() = this is DataFreshness.Live
