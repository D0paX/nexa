package com.example.nexa.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexa.ui.common.DataFreshness
import com.example.nexa.ui.common.DegradedScenario
import com.example.nexa.ui.common.NexaPresentation
import com.example.nexa.ui.realtime.RealtimeState
import com.example.nexa.ui.realtime.RealtimeStore
import com.example.nexa.ui.realtime.withRealtime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
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

    /** What the operator had set up, kept across a reload. */
    private var presentation: NexaPresentation<AlertFilters, AlertSort>? = null

    /** Which slice they were reading. Kept for the same reason. */
    private var view: AlertScopeView = AlertScopeView.Open

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
            // Only the slices this screen actually renders. Every applied
            // event moves the store's sequence and applied count, so
            // collecting the state itself re-ran this whole projection —
            // overlay, search, filter, sort — for a delivery record in a
            // domain this screen does not show.
            RealtimeStore.state
                .distinctUntilChangedBy { it.alerts to it.deliveries }
                .collect { live ->
                    realtime = live
                    update { it }
                }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AlertsUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = restorePresentation(degradedOrDefault())
            // Re-project so events that arrived before this screen existed
            // are applied to the snapshot it just loaded.
            update { it }
        }
    }

    /**
     * Revalidates without taking the screen away.
     *
     * When there is content to keep, it stays visible and is marked as being
     * checked. Only a screen with nothing on it falls back to a full load.
     */
    fun refresh() {
        val current = _state.value as? AlertsUiState.Content ?: run {
            load()
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(refreshing = true)
            delay(LOAD_DELAY_MS)
            _state.value = restorePresentation(degradedOrDefault())
            update { it }
        }
    }

    /**
     * Puts the operator's search, filters and sort back onto a freshly loaded
     * snapshot.
     *
     * A retry after a failure is the moment this matters most: someone was
     * narrowing a list when the source stopped answering, and having the
     * retry succeed by handing them an unfiltered one undoes their work.
     *
     * Presentation only. Nothing security-relevant survives a reload.
     */
    private fun restorePresentation(loaded: AlertsUiState): AlertsUiState {
        val content = loaded as? AlertsUiState.Content ?: return loaded
        val kept = presentation ?: return content
        return content.copy(
            query = kept.query,
            filters = kept.filters,
            sort = kept.sort,
            view = view,
            refreshing = false
        )
    }

    fun onQueryChange(query: String) = update { it.copy(query = query) }

    fun onFiltersChange(filters: AlertFilters) = update { it.copy(filters = filters) }

    fun onSortChange(sort: AlertSort) = update { it.copy(sort = sort) }

    fun onViewChange(view: AlertScopeView) = update { it.copy(view = view) }

    /**
     * Clears the filter set and nothing else.
     *
     * The search query survives, because clearing filters is not a request to
     * undo a search — the two controls are separate and each clears only its
     * own concern.
     */
    fun clearFilters() = update { it.copy(filters = AlertFilters()) }

    fun clearQuery() = update { it.copy(query = "") }

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
        presentation = NexaPresentation(updated.query, updated.filters, updated.sort)
        view = updated.view
        val live = updated.all.withRealtime(realtime)
        val visible = live.resolve(updated.query, updated.filters, updated.sort, updated.view)
        _state.value = updated.copy(
            all = live,
            visible = visible,
            // Counted over what is on screen, never over the whole set. A
            // breakdown drawn from a different collection than the list it
            // sits above is a count that describes something the operator
            // cannot see.
            summary = summarize(visible)
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

    /**
     * The condition the loaded record was read under.
     *
     * A record is cached so that returning to it does not re-run the load,
     * but the availability it was read under is part of what it says. When
     * that changes, the cached copy is describing a moment that has passed —
     * on a real device this showed a detail claiming an observation from two
     * minutes ago while the list it was opened from already said the state
     * could not be confirmed. Keying the cache on the condition as well as
     * the identifier makes the record reload instead.
     */
    private var loadedUnder: DegradedScenario.Scenario? = null

    /**
     * A change of condition re-reads the record.
     *
     * Waiting for the screen to be entered again is not enough. The detail is
     * still open when connectivity goes, and a record left as it was would go
     * on claiming an observation NEXA can no longer confirm — to the reader,
     * and to the action path, which builds its target snapshot from this same
     * record.
     */
    init {
        viewModelScope.launch {
            DegradedScenario.active.collect { scenario ->
                val loaded = loadedId ?: return@collect
                if (loadedUnder == scenario) return@collect
                loadedId = null
                load(loaded)
            }
        }
    }

    fun load(alertId: String) {
        val scenario = DegradedScenario.active.value
        if (loadedId == alertId && loadedUnder == scenario &&
            _state.value is AlertDetailUiState.Content
        ) {
            return
        }
        loadedId = alertId
        loadedUnder = scenario
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
