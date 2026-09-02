package com.example.nexa.ui.identity

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
 * Holds the identity inventory.
 *
 * Query and filtering resolve here, so the list never recomputes during
 * composition and no security judgement is made mid-layout.
 */
class IdentitiesViewModel : ViewModel() {

    private val _state = MutableStateFlow<IdentitiesUiState>(IdentitiesUiState.Loading)
    val state: StateFlow<IdentitiesUiState> = _state.asStateFlow()

    private var realtime: RealtimeState = RealtimeState()

    init {
        load()
        observeRealtime()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = IdentitiesUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = degradedOrDefault()
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
            RealtimeStore.state.collect { live ->
                realtime = live
                updateContent { it }
            }
        }
    }

    fun refresh() = load()

    fun onQueryChange(query: String) = updateContent { it.copy(query = query) }

    fun onFiltersChange(filters: IdentityFilters) = updateContent { it.copy(filters = filters) }

    fun clearFilters() = updateContent { it.copy(filters = IdentityFilters()) }

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
        val live = updated.all.withRealtime(realtime)
        _state.value = updated.copy(
            all = live,
            visible = live.resolve(updated.query, updated.filters)
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
