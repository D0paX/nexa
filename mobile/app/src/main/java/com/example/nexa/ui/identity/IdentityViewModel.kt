package com.example.nexa.ui.identity

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
 * Holds the identity inventory.
 *
 * Query and filtering resolve here, so the list never recomputes during
 * composition and no security judgement is made mid-layout.
 */
class IdentitiesViewModel : ViewModel() {

    private val _state = MutableStateFlow<IdentitiesUiState>(IdentitiesUiState.Loading)
    val state: StateFlow<IdentitiesUiState> = _state.asStateFlow()

    private var realtime: RealtimeState = RealtimeState()

    /** What the operator had set up, kept across a reload. */
    private var presentation: NexaPresentation<IdentityFilters, IdentitySort>? = null

    init {
        load()
        observeRealtime()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = IdentitiesUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = restorePresentation(degradedOrDefault())
            // Re-project so trust changes already reported are applied.
            updateContent { it }
        }
    }

    /**
     * Live trust changes.
     *
     * Trust standing only. What an operator may do with an identity is
     * decided by the authorization engine at request time, and no amount of
     * trust arriving on a stream changes it.
     */
    private fun observeRealtime() {
        viewModelScope.launch {
            // Only the slices this screen actually renders. Every applied
            // event moves the store's sequence and applied count, so
            // collecting the state itself re-ran this whole projection —
            // overlay, search, filter, sort — for a delivery record in a
            // domain this screen does not show.
            RealtimeStore.state
                .distinctUntilChangedBy { it.identities }
                .collect { live ->
                    realtime = live
                    updateContent { it }
                }
        }
    }

    /**
     * Revalidates without taking the screen away.
     *
     * When there is content to keep, it stays visible and is marked as being
     * checked. Only a screen with nothing on it falls back to a full load.
     */
    fun refresh() {
        val current = _state.value as? IdentitiesUiState.Content ?: run {
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
     * narrowing a list when the source stopped answering, and having the
     * retry succeed by handing them an unfiltered one undoes their work.
     *
     * Presentation only. Nothing security-relevant survives a reload.
     */
    private fun restorePresentation(loaded: IdentitiesUiState): IdentitiesUiState {
        val content = loaded as? IdentitiesUiState.Content ?: return loaded
        val kept = presentation ?: return content
        return content.copy(
            query = kept.query,
            filters = kept.filters,
            sort = kept.sort,
            refreshing = false
        )
    }

    fun onQueryChange(query: String) = updateContent { it.copy(query = query) }

    fun onFiltersChange(filters: IdentityFilters) = updateContent { it.copy(filters = filters) }

    fun onSortChange(sort: IdentitySort) = updateContent { it.copy(sort = sort) }

    /**
     * Clears the filter set and nothing else.
     *
     * The search query survives, because clearing filters is not a request to
     * undo a search — the two controls are separate and each clears only its
     * own concern.
     */
    fun clearFilters() = updateContent { it.copy(filters = IdentityFilters()) }

    fun clearQuery() = updateContent { it.copy(query = "") }

    /** The snapshot to load, honouring a review scenario when one is active. */
    private fun degradedOrDefault(): IdentitiesUiState =
        when (DegradedScenario.active.value) {
            null, DegradedScenario.Scenario.Current -> IdentityPreview.scenario
            DegradedScenario.Scenario.Empty -> IdentityPreview.empty()
            DegradedScenario.Scenario.Stale -> IdentityPreview.stale()
            DegradedScenario.Scenario.Offline -> IdentityPreview.offlineWithCache()
            DegradedScenario.Scenario.Degraded -> IdentityPreview.degraded()
            DegradedScenario.Scenario.Unavailable -> IdentityPreview.unavailable()
            DegradedScenario.Scenario.Error ->
                IdentitiesUiState.Error("Identity data could not be read.")
        }

    private fun updateContent(transform: (IdentitiesUiState.Content) -> IdentitiesUiState.Content) {
        val current = _state.value as? IdentitiesUiState.Content ?: return
        val updated = transform(current)
        presentation = NexaPresentation(updated.query, updated.filters, updated.sort)
        val live = updated.all.withRealtime(realtime)
        _state.value = updated.copy(
            all = live,
            visible = live.resolve(updated.query, updated.filters, updated.sort)
        )
    }

    private companion object {
        const val LOAD_DELAY_MS = 400L
    }
}

/** Holds one identity's full trust context. */
class IdentityDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<IdentityDetailUiState>(IdentityDetailUiState.Loading)
    val state: StateFlow<IdentityDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun load(identityId: String) {
        if (loadedId == identityId && _state.value is IdentityDetailUiState.Content) return
        loadedId = identityId
        viewModelScope.launch {
            _state.value = IdentityDetailUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = degradedOrDefault(identityId)
        }
    }

    /**
     * The record to show, honouring a review scenario when one is active.
     *
     * Reverification is prepared against the identity's associated device, so
     * it is that observation which has to be honest about its age.
     */
    private fun degradedOrDefault(identityId: String): IdentityDetailUiState =
        when (DegradedScenario.active.value) {
            null,
            DegradedScenario.Scenario.Current,
            DegradedScenario.Scenario.Empty -> IdentityPreview.detailFor(identityId)
            DegradedScenario.Scenario.Stale, DegradedScenario.Scenario.Offline ->
                IdentityPreview.detailFor(identityId).aged(
                    DataFreshness.Stale("Last confirmed 12 min ago"),
                    "12m ago"
                )
            DegradedScenario.Scenario.Degraded ->
                IdentityPreview.detailFor(identityId).aged(DataFreshness.Unknown, "unknown")
            DegradedScenario.Scenario.Unavailable -> IdentityDetailUiState.Unavailable
            DegradedScenario.Scenario.Error ->
                IdentityDetailUiState.Error("This identity record could not be read.")
        }

    private fun IdentityDetailUiState.aged(
        freshness: DataFreshness,
        label: String
    ): IdentityDetailUiState {
        val content = this as? IdentityDetailUiState.Content ?: return this
        val identity = content.data.identity
        val device = identity.device ?: return this
        return IdentityDetailUiState.Content(
            content.data.copy(
                identity = identity.copy(
                    device = device.copy(
                        recordFreshness = freshness,
                        lastObservedLabel = label
                    )
                )
            )
        )
    }

    fun refresh() {
        loadedId?.let {
            loadedId = null
            load(it)
        }
    }


    private companion object {
        const val LOAD_DELAY_MS = 320L
    }
}
