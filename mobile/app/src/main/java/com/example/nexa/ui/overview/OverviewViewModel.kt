package com.example.nexa.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = OverviewUiState.Loading
            // Stands in for the round trip to the read model.
            delay(LOAD_DELAY_MS)
            _state.value = OverviewPreview.scenario
        }
    }

    /** Re-reads system state; used by the retry affordance on failure states. */
    fun refresh() = load()

    private companion object {
        const val LOAD_DELAY_MS = 550L
    }
}
