package com.example.nexa.ui.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexa.ui.common.DegradedScenario
import com.example.nexa.ui.common.NexaPresentation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds the security history.
 *
 * Search, filtering, ordering and paging all resolve here, so the timeline
 * never recomputes during composition however far back the record goes, and
 * no security reasoning happens inside a layout pass.
 *
 * Only a bounded page is ever handed to the list. History grows without limit;
 * the screen must not.
 */
class AuditViewModel : ViewModel() {

    private val _state = MutableStateFlow<AuditUiState>(AuditUiState.Loading)
    val state: StateFlow<AuditUiState> = _state.asStateFlow()

    private var pageLimit = AUDIT_PAGE_SIZE

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AuditUiState.Loading
            delay(LOAD_DELAY_MS)
            pageLimit = AUDIT_PAGE_SIZE
            _state.value = restorePresentation(degradedOrDefault())
        }
    }

    /**
     * Revalidates without taking the screen away.
     *
     * When there is content to keep, it stays visible and is marked as being
     * checked. Only a screen with nothing on it falls back to a full load.
     */
    fun refresh() {
        val current = _state.value as? AuditUiState.Content ?: run {
            load()
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(refreshing = true)
            delay(LOAD_DELAY_MS)
            _state.value = restorePresentation(degradedOrDefault())
            update(resetPage = true) { it }
        }
    }

    /**
     * Puts the operator's search, filters and sort back onto a freshly loaded
     * snapshot. Presentation only — nothing security-relevant survives a
     * reload.
     */
    private fun restorePresentation(loaded: AuditUiState): AuditUiState {
        val content = loaded as? AuditUiState.Content ?: return loaded
        val kept = presentation ?: return content
        return content.copy(
            query = kept.query,
            filters = kept.filters,
            sort = kept.sort,
            refreshing = false
        )
    }

    /** What the operator had set up, kept across a reload. */
    private var presentation: NexaPresentation<AuditFilters, AuditSort>? = null

    fun onQueryChange(query: String) = update(resetPage = true) { it.copy(query = query) }

    fun onFiltersChange(filters: AuditFilters) = update(resetPage = true) { it.copy(filters = filters) }

    fun onSortChange(sort: AuditSort) = update(resetPage = true) { it.copy(sort = sort) }

    fun onQuickFilter(quick: AuditQuickFilter) = update(resetPage = true) {
        it.copy(filters = it.filters.withQuickFilter(quick))
    }

    /**
     * Clears the filter set and nothing else.
     *
     * The search query survives, because clearing filters is not a request to
     * undo a search — the two controls are separate and each clears only its
     * own concern.
     */
    fun clearFilters() = update(resetPage = true) { it.copy(filters = AuditFilters()) }

    fun clearQuery() = update(resetPage = true) { it.copy(query = "") }

    /**
     * Extends the rendered window.
     *
     * The seam a paginated source plugs into: today it takes more of an
     * already-loaded list, and later it will ask for the next range.
     */
    fun loadMore() {
        val current = _state.value as? AuditUiState.Content ?: return
        if (!current.hasMore) return
        pageLimit += AUDIT_PAGE_SIZE
        _state.value = project(current)
    }

    /**
     * Entry point for a future incremental event stream.
     *
     * Merges by event id and re-orders by the authoritative sequence, so an
     * event arriving late still lands where it actually belongs in time
     * rather than at the top of the list.
     */
    fun onEvents(events: List<AuditEntry>) = update {
        val merged = (events + it.all).distinctBy { entry -> entry.id }
        it.copy(all = merged)
    }

    /** The snapshot to load, honouring a review scenario when one is active. */
    private fun degradedOrDefault(): AuditUiState =
        when (DegradedScenario.active.value) {
            null, DegradedScenario.Scenario.Current -> AuditPreview.scenario
            DegradedScenario.Scenario.Empty -> AuditPreview.empty
            DegradedScenario.Scenario.Stale -> AuditPreview.stale
            DegradedScenario.Scenario.Offline -> AuditPreview.offline
            // Partial history is stated as partial. Presenting an incomplete
            // record as the whole is how an operator concludes nothing
            // happened during a window NEXA could not see.
            DegradedScenario.Scenario.Degraded -> AuditPreview.degraded
            DegradedScenario.Scenario.Unavailable -> AuditPreview.unavailable
            DegradedScenario.Scenario.Error ->
                AuditUiState.Error("Security history could not be read.")
        }

    private fun update(
        resetPage: Boolean = false,
        transform: (AuditUiState.Content) -> AuditUiState.Content
    ) {
        val current = _state.value as? AuditUiState.Content ?: return
        if (resetPage) pageLimit = AUDIT_PAGE_SIZE
        val updated = transform(current)
        presentation = NexaPresentation(updated.query, updated.filters, updated.sort)
        _state.value = project(updated)
    }

    /** Re-derives everything downstream of [AuditUiState.Content.all]. */
    private fun project(content: AuditUiState.Content): AuditUiState.Content {
        val visible = content.all.resolve(content.query, content.filters, content.sort)
        return content.copy(
            visible = visible,
            page = visible.take(pageLimit),
            // Over the visible set, so the header's count and its breakdown
            // always describe the same records.
            summary = summarize(visible),
            hasMore = visible.size > pageLimit
        )
    }

    private companion object {
        const val LOAD_DELAY_MS = 400L
    }
}

/** Holds one historical record and the sequence it belongs to. */
class AuditDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<AuditDetailUiState>(AuditDetailUiState.Loading)
    val state: StateFlow<AuditDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun load(eventId: String) {
        if (loadedId == eventId && _state.value is AuditDetailUiState.Content) return
        loadedId = eventId
        viewModelScope.launch {
            _state.value = AuditDetailUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = AuditPreview.detailFor(eventId)
        }
    }

    fun refresh() {
        loadedId?.let {
            loadedId = null
            load(it)
        }
    }

    private companion object {
        const val LOAD_DELAY_MS = 300L
    }
}
