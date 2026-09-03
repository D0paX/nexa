package com.example.nexa.ui.common

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaStatus

/**
 * How much NEXA actually knows.
 *
 * The single most dangerous thing a security console can do is present an
 * absence of knowledge as knowledge of absence. "No alerts" when the alert
 * service failed, "Normal" when enforcement state could not be read, "42
 * devices" from an inventory that is hours old — each of these is a true-
 * looking sentence built out of nothing, and an operator has no way to tell.
 *
 * So the vocabulary is written down once, here, and every surface maps into
 * it. Nine states, each meaning something different from all the others:
 *
 *   Current      NEXA has fresh authoritative state
 *   Stale        NEXA has real state that is no longer current
 *   Unknown      NEXA cannot establish the state at all
 *   Empty        NEXA asked, was answered, and the answer was "nothing"
 *   Offline      this device cannot reach the network path
 *   Unavailable  the service could not answer
 *   Degraded     part of the answer arrived; completeness is not assured
 *   Error        a specific operation failed
 *   Loading      the answer has been asked for and has not arrived
 *
 * The distinctions that matter most, and that the tests enforce:
 *
 *   Empty is not Unavailable   — nothing there, versus nobody answered
 *   Empty is not Offline       — nothing there, versus we could not ask
 *   Stale is not Unknown       — old truth, versus no truth
 *   Unknown is not Unverified  — not looked, versus looked and not proven
 *   Unavailable is not Error   — could not answer, versus something broke
 *   Degraded is not Empty      — some of it, versus none of it
 */
enum class NexaAvailability {
    Loading,
    Current,
    Stale,
    Degraded,
    Empty,
    Unknown,
    Offline,
    Unavailable,
    Error;

    /**
     * Whether the state is complete and current enough to be acted upon.
     *
     * Only [Current] qualifies. Everything else is either old, partial,
     * absent or unreadable, and a high-impact action taken on any of them is
     * an action taken on a guess.
     *
     * [Empty] is deliberately excluded: an empty answer is a fine thing to
     * display, but there is nothing in it to act on.
     */
    val isActionable: Boolean get() = this == Current

    /** Whether displayed content may be described as reflecting reality now. */
    val isCurrent: Boolean get() = this == Current

    /** Whether NEXA has *some* real data, however old or partial. */
    val hasData: Boolean get() = this == Current || this == Stale || this == Degraded

    /**
     * Whether this is a statement about NEXA's ability to see, rather than
     * about the system being observed.
     */
    val isVisibilityProblem: Boolean
        get() = this == Offline || this == Unavailable || this == Unknown ||
            this == Error || this == Degraded
}

/** The word an operator reads. One wording, everywhere. */
val NexaAvailability.label: String
    get() = when (this) {
        NexaAvailability.Loading -> "Loading"
        NexaAvailability.Current -> "Current"
        NexaAvailability.Stale -> "Stale"
        NexaAvailability.Degraded -> "Incomplete"
        NexaAvailability.Empty -> "Empty"
        NexaAvailability.Unknown -> "Unknown"
        NexaAvailability.Offline -> "Offline"
        NexaAvailability.Unavailable -> "Unavailable"
        NexaAvailability.Error -> "Error"
    }

/**
 * The tone each state takes.
 *
 * None of them is [NexaStatus.Secure]. Not being able to see something is
 * never good news, and a green badge on a screen that has stopped knowing
 * anything is the exact failure this file exists to prevent.
 */
val NexaAvailability.status: NexaStatus
    get() = when (this) {
        NexaAvailability.Loading -> NexaStatus.Information
        // Current describes the data, not the security posture. Posture is
        // decided by the content, not by the fact that it arrived.
        NexaAvailability.Current -> NexaStatus.Information
        NexaAvailability.Stale -> NexaStatus.Warning
        NexaAvailability.Degraded -> NexaStatus.Degraded
        NexaAvailability.Empty -> NexaStatus.Information
        NexaAvailability.Unknown -> NexaStatus.Unknown
        NexaAvailability.Offline -> NexaStatus.Offline
        NexaAvailability.Unavailable -> NexaStatus.Warning
        NexaAvailability.Error -> NexaStatus.Danger
    }

val NexaAvailability.icon: ImageVector
    get() = when (this) {
        NexaAvailability.Loading -> NexaIcons.Pending
        NexaAvailability.Current -> NexaIcons.Secure
        NexaAvailability.Stale -> NexaIcons.Stale
        NexaAvailability.Degraded -> NexaIcons.Warning
        NexaAvailability.Empty -> NexaIcons.Empty
        NexaAvailability.Unknown -> NexaIcons.Unknown
        NexaAvailability.Offline -> NexaIcons.Offline
        NexaAvailability.Unavailable -> NexaIcons.Unavailable
        NexaAvailability.Error -> NexaIcons.Critical
    }

/**
 * What the state means, for a named subject.
 *
 * [subject] is the thing NEXA could or could not read — "the device
 * inventory", "alert state", "enforcement state". Every sentence describes
 * NEXA's knowledge and never the security of the network, because these
 * states say nothing whatever about the latter.
 */
fun availabilityExplanation(availability: NexaAvailability, subject: String): String {
    // The subject is written lower-case so it can sit mid-sentence, and some
    // of these sentences open with it. Capitalising here keeps the caller
    // from having to know which ones do.
    val text = when (availability) {
        NexaAvailability.Loading -> "Reading $subject."
        NexaAvailability.Current -> "$subject was confirmed just now."
        NexaAvailability.Stale ->
            "$subject is real but is no longer current. Re-check before acting on it."
        NexaAvailability.Degraded ->
            "Part of $subject could not be retrieved. What is shown may be incomplete — do not read it as the whole picture."
        NexaAvailability.Empty ->
            "$subject was retrieved and contains nothing. This reports the contents only; it is not an assessment of system posture."
        NexaAvailability.Unknown ->
            "NEXA cannot establish $subject. This is not a report that it is normal — the current state is unknown."
        NexaAvailability.Offline ->
            "No connection, so NEXA cannot refresh $subject. Anything shown was confirmed earlier."
        NexaAvailability.Unavailable ->
            "NEXA cannot reach the service that provides $subject. What is happening is unknown — this is not a report that nothing is."
        NexaAvailability.Error -> "Reading $subject failed."
    }
    return text.replaceFirstChar { it.uppercase() }
}

/**
 * Availability derived from freshness alone.
 *
 * Used where a screen already holds real data and only needs to say how old
 * it is. Note there is no path from here to [NexaAvailability.Empty] or
 * [NexaAvailability.Unavailable]: freshness describes data that exists, and
 * conflating "old" with "absent" is one of the mistakes this vocabulary is
 * meant to make impossible.
 */
fun availabilityOf(freshness: DataFreshness): NexaAvailability = when (freshness) {
    is DataFreshness.Live -> NexaAvailability.Current
    is DataFreshness.Stale -> NexaAvailability.Stale
    is DataFreshness.Unknown -> NexaAvailability.Unknown
}

/**
 * Availability for a screen that has content.
 *
 * Order matters, and it runs from least to most trustworthy claim.
 * [offline] outranks everything, because a lost connection is the most
 * fundamental fact about a picture and explains every other shortcoming in
 * it; incompleteness outranks staleness, because a partial answer is a worse
 * thing to mistake for the whole than an old one; emptiness is only claimed
 * when the data is both complete and current, since an empty *stale* list is
 * not evidence that the set is empty now.
 *
 * Note that [offline] here means "cached content, no connection" — content
 * survives, and says so. It is distinct from a screen with no cache at all,
 * which uses the full-screen offline surface instead.
 */
fun contentAvailability(
    freshness: DataFreshness,
    isEmpty: Boolean,
    degraded: Boolean = false,
    offline: Boolean = false
): NexaAvailability {
    val fromFreshness = availabilityOf(freshness)
    return when {
        offline -> NexaAvailability.Offline
        degraded -> NexaAvailability.Degraded
        fromFreshness != NexaAvailability.Current -> fromFreshness
        isEmpty -> NexaAvailability.Empty
        else -> NexaAvailability.Current
    }
}

/**
 * How complete a retrieved set is.
 *
 * Shared so no screen invents its own way of saying "some of this is
 * missing". Silently omitting records and presenting the rest as the whole
 * is how an operator concludes nothing happened during a window NEXA simply
 * could not see.
 */
sealed interface NexaCoverage {
    data object Complete : NexaCoverage
    data class Partial(val reason: String) : NexaCoverage

    val isComplete: Boolean get() = this is Complete
}
