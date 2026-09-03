package com.example.nexa.ui.devices

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

    /** What the operator had set up, kept across a reload. */
    private var presentation: NexaPresentation<DeviceFilters, DeviceSort>? = null

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
            // Only the slices this screen actually renders. Every applied
            // event moves the store's sequence and applied count, so
            // collecting the state itself re-ran this whole projection —
            // overlay, search, filter, sort — for a delivery record in a
            // domain this screen does not show.
            RealtimeStore.state
                .distinctUntilChangedBy { it.devices to it.identities }
                .collect { live ->
                    realtime = live
                    updateContent { it }
                }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DevicesUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = restorePresentation(degradedOrDefault())
            // Re-project so anything the stream already reported is applied
            // to the newly loaded snapshot. Without this a screen opened
            // after an event would show the snapshot as though nothing had
            // happened since.
            updateContent { it }
        }
    }

    /**
     * Revalidates without taking the screen away.
     *
     * When there is content to keep, it stays visible and is marked as being
     * checked. Only a screen with nothing on it falls back to a full load —
     * there, a spinner is the honest treatment because there is genuinely
     * nothing else to show.
     */
    fun refresh() {
        val current = _state.value as? DevicesUiState.Content ?: run {
            load()
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(refreshing = true)
            delay(LOAD_DELAY_MS)
            _state.value = restorePresentation(degradedOrDefault())
            updateContent { it }
        }
    }

    /**
     * Puts the operator's search, filters and sort back onto a freshly loaded
     * snapshot.
     *
     * A retry after a failure is the moment this matters most: someone was
     * looking for a particular device when the inventory stopped answering,
     * and having the retry succeed by handing them an unfiltered list is
     * quietly undoing their work.
     *
     * Presentation only. Nothing security-relevant survives a reload — not a
     * target, not an authorization, not an action's eligibility. Restoring a
     * query restores a question; restoring anything else would restore an
     * answer nobody re-checked.
     */
    private fun restorePresentation(loaded: DevicesUiState): DevicesUiState {
        val content = loaded as? DevicesUiState.Content ?: return loaded
        val kept = presentation ?: return content
        return content.copy(
            query = kept.query,
            filters = kept.filters,
            sort = kept.sort,
            refreshing = false
        )
    }

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
        presentation = NexaPresentation(updated.query, updated.filters, updated.sort)
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
                val loaded = loadedMac ?: return@collect
                if (loadedUnder == scenario) return@collect
                loadedMac = null
                load(loaded)
            }
        }
    }

    fun load(mac: String) {
        val scenario = DegradedScenario.active.value
        if (loadedMac == mac && loadedUnder == scenario &&
            _state.value is DeviceDetailUiState.Content
        ) {
            return
        }
        loadedMac = mac
        loadedUnder = scenario
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
