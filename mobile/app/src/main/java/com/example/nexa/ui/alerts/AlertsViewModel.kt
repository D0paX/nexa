package com.example.nexa.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.DegradedScenario
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.RealtimeStore
import com.example.nexa.ui.realtime.withRealtime
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

    private var realtime: RealtimeState = RealtimeState()

    init {
        load()
        observeRealtime()
    }

    /**
     * Live alert lifecycle changes.
     *
     * Only lifecycle. Delivery arrives on its own events and is applied to
     * the delivery record, never here — a notification failing has never been
     * a change to an incident.
     */
    private fun observeRealtime() {
        viewModelScope.launch {
            RealtimeStore.state.collect { live ->
                realtime = live
                update { it }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AlertsUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = degradedOrDefault()
            // Re-project so events that arrived before this screen existed
            // are applied to the snapshot it just loaded.
            update { it }
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

    /** The snapshot to load, honouring a review scenario when one is active. */
    private fun degradedOrDefault(): AlertsUiState =
        when (DegradedScenario.active.value) {
            null, DegradedScenario.Scenario.Current -> AlertsPreview.scenario
            DegradedScenario.Scenario.Empty -> AlertsPreview.empty()
            DegradedScenario.Scenario.Stale -> AlertsPreview.stale()
            DegradedScenario.Scenario.Offline -> AlertsPreview.offlineWithCache()
            DegradedScenario.Scenario.Degraded -> AlertsPreview.degraded()
            DegradedScenario.Scenario.Unavailable -> AlertsPreview.unavailable()
            DegradedScenario.Scenario.Error ->
                AlertsUiState.Error("Alert state could not be read.")
        }

    private fun update(transform: (AlertsUiState.Content) -> AlertsUiState.Content) {
        val current = _state.value as? AlertsUiState.Content ?: return
        val updated = transform(current)
        val live = updated.all.withRealtime(realtime)
        _state.value = updated.copy(
            all = live,
            visible = live.resolve(updated.query, updated.filters, updated.sort, updated.view),
            summary = summarize(live)
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
            _state.value = degradedOrDefault(alertId)
        }
    }

    /**
     * The record to show, honouring a review scenario when one is active.
     *
     * The freshness carried here is what the action path consults before it
     * will prepare a quarantine against the alert's target, so a scenario
     * that ages it also closes that path.
     */
    private fun degradedOrDefault(alertId: String): AlertDetailUiState =
        when (DegradedScenario.active.value) {
            null,
            DegradedScenario.Scenario.Current,
            DegradedScenario.Scenario.Empty -> AlertsPreview.detailFor(alertId)
            DegradedScenario.Scenario.Stale, DegradedScenario.Scenario.Offline ->
                AlertsPreview.detailFor(alertId)
                    .aged(DataFreshness.Stale("Last confirmed 9 min ago"))
            DegradedScenario.Scenario.Degraded ->
                AlertsPreview.detailFor(alertId).aged(DataFreshness.Unknown)
            DegradedScenario.Scenario.Unavailable -> AlertDetailUiState.Unavailable
            DegradedScenario.Scenario.Error ->
                AlertDetailUiState.Error("This alert record could not be read.")
        }

    private fun AlertDetailUiState.aged(freshness: DataFreshness): AlertDetailUiState {
        val content = this as? AlertDetailUiState.Content ?: return this
        return AlertDetailUiState.Content(content.data.copy(freshness = freshness))
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
