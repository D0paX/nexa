package com.example.nexa.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.RealtimeStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the command center's state.
 *
 * The screen renders whatever it is handed; posture, attention and freshness
 * are decided here (and in the pure functions in OverviewState.kt), never in
 * a composable. That boundary is what will let the preview source be swapped
 * for the real read model without the UI changing.
 */
class OverviewViewModel : ViewModel() {

    private val _state = MutableStateFlow<OverviewUiState>(OverviewUiState.Loading)
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    private var realtime: RealtimeState = RealtimeState()
    private var snapshot: OverviewUiState.Content? = null

    init {
        load()
        observeRealtime()
    }

    /**
     * Live posture inputs.
     *
     * The command center reads the same store every other screen reads, so a
     * breaker that opens is reflected here and in action availability from one
     * fact rather than two.
     */
    private fun observeRealtime() {
        viewModelScope.launch {
            RealtimeStore.state.collect { live ->
                realtime = live
                project()
            }
        }
    }

    /**
     * Applies the overlay to the snapshot.
     *
     * Only the breaker, because only the breaker is a *complete* statement.
     * It describes the whole subsystem, so a report of it replaces what the
     * snapshot said outright.
     *
     * Counts deliberately do not come from the overlay. The stream reports
     * the devices that changed, not the devices that exist, and counting the
     * ones it happened to mention would produce a total that is confidently
     * wrong — and visibly disagreeing with the device summary beside it. The
     * inventory owns that number, and applies its overlays where it has the
     * whole list to apply them to.
     */
    private fun project() {
        val base = snapshot ?: return
        val breaker = realtime.circuitBreaker ?: base.data.enforcement.circuitBreaker
        val enforcement = base.data.enforcement.copy(circuitBreaker = breaker)
        val posture = derivePosture(enforcement, base.data.alerts, base.data.freshness)
        _state.value = base.copy(
            data = base.data.copy(
                enforcement = enforcement,
                posture = posture,
                postureDetail = postureDetail(posture, enforcement)
            )
        )
    }

    fun load() {
        viewModelScope.launch {
            _state.value = OverviewUiState.Loading
            // Stands in for the round trip to the read model.
            delay(LOAD_DELAY_MS)
            val loaded = OverviewPreview.scenario
            snapshot = loaded as? OverviewUiState.Content
            _state.value = loaded
            project()
        }
    }

    /** Re-reads system state; used by the retry affordance on failure states. */
    fun refresh() = load()

    private companion object {
        const val LOAD_DELAY_MS = 550L
    }
}
