package com.example.nexa.ui.devices

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
 * Holds the device inventory state.
 *
 * Query, filter and sort are applied here and the resolved list is stored,
 * so the list never recomputes during composition however fast an operator
 * types. A future realtime source replaces [DevicesPreview] and pushes new
 * inventories through [onInventory]; nothing in the screen changes.
 */
class DevicesViewModel : ViewModel() {

    private val _state = MutableStateFlow<DevicesUiState>(DevicesUiState.Loading)
    val state: StateFlow<DevicesUiState> = _state.asStateFlow()

    /** The overlay last applied, so a re-projection keeps live values. */
    private var realtime: RealtimeState = RealtimeState()

    init {
        load()
        observeRealtime()
    }

    /**
     * Live device changes.
     *
     * The screen reads the shared store rather than a stream of its own, so
     * the inventory and every other surface agree about a device.
     */
    private fun observeRealtime() {
        viewModelScope.launch {
            RealtimeStore.state.collect { live ->
                realtime = live
                updateContent { it }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DevicesUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = degradedOrDefault()
            // Re-project so anything the stream already reported is applied
            // to the newly loaded snapshot. Without this a screen opened
            // after an event would show the snapshot as though nothing had
            // happened since.
            updateContent { it }
        }
    }

    fun refresh() = load()

    fun onQueryChange(query: String) = updateContent { it.copy(query = query) }

    fun onSortChange(sort: DeviceSort) = updateContent { it.copy(sort = sort) }

    fun onFiltersChange(filters: DeviceFilters) = updateContent { it.copy(filters = filters) }

    fun clearFilters() = updateContent { it.copy(filters = DeviceFilters()) }

    fun clearQuery() = updateContent { it.copy(query = "") }

    /** Entry point for a future live inventory push. */
    fun onInventory(devices: List<DeviceListItem>) = updateContent { it.copy(all = devices) }

    /**
     * The snapshot to load, honouring a review scenario when one is active.
     *
     * Offline deliberately keeps the last confirmed inventory and marks it
     * stale rather than blanking the screen. Erasing what NEXA legitimately
     * knew a minute ago helps nobody, and an empty list is the one thing an
     * offline inventory must never look like.
     */
    private fun degradedOrDefault(): DevicesUiState =
        when (DegradedScenario.active.value) {
            null, DegradedScenario.Scenario.Current -> DevicesPreview.scenario
            DegradedScenario.Scenario.Empty -> DevicesPreview.empty()
            DegradedScenario.Scenario.Stale -> DevicesPreview.stale()
            DegradedScenario.Scenario.Offline -> DevicesPreview.offlineWithCache()
            DegradedScenario.Scenario.Degraded -> DevicesPreview.degraded()
            DegradedScenario.Scenario.Unavailable -> DevicesPreview.unavailable()
            DegradedScenario.Scenario.Error ->
                DevicesUiState.Error("The device inventory could not be read.")
        }

    /**
     * Applies a change and re-resolves the visible list once, so `visible`
     * is always consistent with query + filters + sort.
     */
    private fun updateContent(transform: (DevicesUiState.Content) -> DevicesUiState.Content) {
        val current = _state.value as? DevicesUiState.Content ?: return
        val updated = transform(current)
        // The snapshot with the live overlay applied on top. Applied at
        // projection time rather than written into `all`, so a later snapshot
        // replaces the base cleanly instead of inheriting old changes.
        val live = updated.all.withRealtime(realtime)
        _state.value = updated.copy(
            all = live,
            visible = live.resolve(updated.query, updated.filters, updated.sort)
        )
    }

    private companion object {
        const val LOAD_DELAY_MS = 450L
    }
}

/**
 * Holds one device's full context.
 *
 * Loads only what Device Detail shows; the inventory list never pulls detail
 * objects for rows it is merely displaying.
 */
class DeviceDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<DeviceDetailUiState>(DeviceDetailUiState.Loading)
    val state: StateFlow<DeviceDetailUiState> = _state.asStateFlow()

    private var loadedMac: String? = null

    fun load(mac: String) {
        if (loadedMac == mac && _state.value is DeviceDetailUiState.Content) return
        loadedMac = mac
        viewModelScope.launch {
            _state.value = DeviceDetailUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = degradedOrDefault(mac)
        }
    }

    /**
     * The record to show, honouring a review scenario when one is active.
     *
     * This is where the degraded condition reaches the action path. Ageing
     * the observation is not decoration: [ActionPreparation] derives how much
     * NEXA knows from exactly this field, so a detail screen opened while the
     * inventory could not be confirmed produces a context that refuses
     * enforcement changes rather than one that looks confident.
     */
    private fun degradedOrDefault(mac: String): DeviceDetailUiState =
        when (DegradedScenario.active.value) {
            null,
            DegradedScenario.Scenario.Current,
            DegradedScenario.Scenario.Empty -> DevicesPreview.detailFor(mac)
            // Old and disconnected differ on the list, where the whole
            // picture is the subject. For one record they amount to the same
            // fact: this observation has not been confirmed recently.
            DegradedScenario.Scenario.Stale, DegradedScenario.Scenario.Offline ->
                DevicesPreview.detailFor(mac).aged(
                    DataFreshness.Stale("Last confirmed 6 min ago"),
                    "6m ago"
                )
            // Part of the record could not be retrieved, and the observation
            // is part of it. NEXA says it does not know rather than showing a
            // time it cannot stand behind.
            DegradedScenario.Scenario.Degraded ->
                DevicesPreview.detailFor(mac).aged(DataFreshness.Unknown, "unknown")
            DegradedScenario.Scenario.Unavailable -> DeviceDetailUiState.Unavailable
            DegradedScenario.Scenario.Error ->
                DeviceDetailUiState.Error("This device record could not be read.")
        }

    /**
     * Re-dates the observation this record rests on.
     *
     * Both copies are updated together — the one the screen renders and the
     * one the action path reads — because a screen that says "6m ago" while
     * preparing an action that believes the target was seen just now is
     * exactly the inconsistency this checkpoint exists to remove.
     */
    private fun DeviceDetailUiState.aged(
        freshness: DataFreshness,
        label: String
    ): DeviceDetailUiState {
        val content = this as? DeviceDetailUiState.Content ?: return this
        val data = content.data
        return DeviceDetailUiState.Content(
            data.copy(
                device = data.device.copy(freshness = freshness, lastSeenLabel = label),
                record = data.record.copy(freshness = freshness, lastObservedLabel = label)
            )
        )
    }

    fun refresh() {
        loadedMac?.let { mac ->
            loadedMac = null
            load(mac)
        }
    }

    private companion object {
        const val LOAD_DELAY_MS = 350L
    }
}
