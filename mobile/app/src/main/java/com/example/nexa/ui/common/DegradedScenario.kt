package com.example.nexa.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PREVIEW DATA — NOT LIVE SYSTEM STATE
 *
 * Which degraded condition the app should pretend to be in.
 *
 * These states are the hardest part of the product to review, because they
 * are exactly the ones that never occur while someone is looking. A reviewer
 * cannot unplug a backend that does not exist yet, so this lets the states be
 * summoned deliberately.
 *
 * It is inert unless something sets it, and the only thing that can is the
 * debug-source-set receiver — which is not compiled into a release build.
 * [active] is null in production and every screen takes its normal path.
 */
object DegradedScenario {

    private val _active = MutableStateFlow<Scenario?>(null)
    val active: StateFlow<Scenario?> = _active.asStateFlow()

    /**
     * The conditions worth reviewing.
     *
     * Each is a genuinely reachable combination. There is no scenario for,
     * say, "unavailable but also empty", because that pair is incoherent:
     * an empty answer requires an answer.
     */
    enum class Scenario {
        /** Everything current. The baseline. */
        Current,

        /** Retrieved successfully; genuinely nothing in it. */
        Empty,

        /** No connectivity. Last confirmed state is shown, clearly aged. */
        Offline,

        /** Real data, no longer current. */
        Stale,

        /** The service could not answer at all. */
        Unavailable,

        /** Part of the answer arrived. Completeness is not assured. */
        Degraded,

        /** A specific read failed. */
        Error;

        companion object {
            fun fromName(value: String?): Scenario? =
                entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }

    /** Debug-only. The receiver that calls this exists only in debug builds. */
    fun activate(scenario: Scenario?) {
        _active.value = scenario
    }

    /**
     * What each scenario claims, in the shared vocabulary.
     *
     * This is the harness's own statement of what it is simulating, and the
     * tests hold it to producing seven distinct answers. A scenario that
     * quietly collapsed into another would make a whole review pass look
     * thorough while covering one state twice.
     */
    fun availabilityFor(scenario: Scenario): NexaAvailability = when (scenario) {
        Scenario.Current -> NexaAvailability.Current
        Scenario.Empty -> NexaAvailability.Empty
        Scenario.Offline -> NexaAvailability.Offline
        Scenario.Stale -> NexaAvailability.Stale
        Scenario.Unavailable -> NexaAvailability.Unavailable
        Scenario.Degraded -> NexaAvailability.Degraded
        Scenario.Error -> NexaAvailability.Error
    }

    /**
     * How old the content each scenario leaves on screen is allowed to claim
     * to be.
     *
     * Offline and stale both keep showing real data, so both get a stale
     * freshness with an honest age. Nothing here returns [DataFreshness.Live]
     * except the current and empty scenarios — a rule the tests enforce, so a
     * future scenario cannot be added that shows aged data under a live label.
     */
    fun freshnessFor(scenario: Scenario): DataFreshness = when (scenario) {
        Scenario.Current, Scenario.Empty -> DataFreshness.Live
        Scenario.Offline -> DataFreshness.Stale("Last confirmed 6 minutes ago, before the connection dropped")
        Scenario.Stale -> DataFreshness.Stale("Last confirmed 4 minutes ago")
        Scenario.Degraded -> DataFreshness.Stale("Last confirmed 2 minutes ago")
        Scenario.Unavailable, Scenario.Error -> DataFreshness.Unknown
    }
}
