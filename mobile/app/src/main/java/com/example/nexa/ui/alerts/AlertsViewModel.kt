package com.example.nexa.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the incident load.
 *
 * Query, filter, view and sort resolve here, so the list never recomputes
 * during composition however large the inventory grows. A future realtime
 * source replaces the preview and pushes through [onAlerts]; the screen is
 * unaffected.
 */
class AlertsViewModel : ViewModel() {

    private val _state = MutableStateFlow<AlertsUiState>(AlertsUiState.Loading)
    val state: StateFlow<AlertsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AlertsUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = AlertsPreview.scenario
        }
    }

    fun refresh() = load()

    fun onQueryChange(query: String) = update { it.copy(query = query) }

    fun onFiltersChange(filters: AlertFilters) = update { it.copy(filters = filters) }

    fun onSortChange(sort: AlertSort) = update { it.copy(sort = sort) }

    fun onViewChange(view: AlertScopeView) = update { it.copy(view = view) }

    fun clearFilters() = update { it.copy(filters = AlertFilters()) }

    /** Entry point for a future live alert stream. */
    fun onAlerts(alerts: List<AlertListItem>) = update { it.copy(all = alerts) }

    /**
     * Requests a lifecycle transition.
     *
     * Phase 3 owns the alert lifecycle; this is the client asking for a
     * transition, and the preview source applies it locally. Acknowledgement
     * moves an alert to Acknowledged and nothing else — it never resolves.
     */
    fun onLifecycleChange(alertId: String, lifecycle: AlertLifecycle) = update { content ->
        content.copy(
            all = content.all.map { alert ->
                if (alert.id == alertId) alert.copy(lifecycle = lifecycle) else alert
            }
        )
    }

    private fun update(transform: (AlertsUiState.Content) -> AlertsUiState.Content) {
        val current = _state.value as? AlertsUiState.Content ?: return
        val updated = transform(current)
        _state.value = updated.copy(
            visible = updated.all.resolve(updated.query, updated.filters, updated.sort, updated.view),
            summary = summarize(updated.all)
        )
    }

    private companion object {
        const val LOAD_DELAY_MS = 420L
    }
}

/** Holds one alert's full incident context. */
class AlertDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<AlertDetailUiState>(AlertDetailUiState.Loading)
    val state: StateFlow<AlertDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun load(alertId: String) {
        if (loadedId == alertId && _state.value is AlertDetailUiState.Content) return
        loadedId = alertId
        viewModelScope.launch {
            _state.value = AlertDetailUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = AlertsPreview.detailFor(alertId)
        }
    }

    fun refresh() {
        loadedId?.let {
            loadedId = null
            load(it)
        }
    }

    /**
     * Applies a requested lifecycle transition to the displayed alert.
     *
     * The available action set is recomputed from the new state, so an
     * acknowledged alert immediately offers Resolve rather than
     * Acknowledge — and never claims to have been resolved.
     */
    fun onLifecycleChange(lifecycle: AlertLifecycle) {
        val current = _state.value as? AlertDetailUiState.Content ?: return
        val updated = current.data.alert.copy(lifecycle = lifecycle)
        _state.value = AlertDetailUiState.Content(
            current.data.copy(
                alert = updated,
                actions = availableAlertActions(updated)
            )
        )
    }

    private companion object {
        const val LOAD_DELAY_MS = 330L
    }
}
