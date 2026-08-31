package com.example.nexa.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nexa.push.PushInbox
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Holds notification delivery state.
 *
 * Search, filtering, ordering and paging all resolve here, so the list never
 * recomputes during composition and no delivery reasoning happens inside a
 * layout pass.
 *
 * [onDeliveryRecords] and [onDeliveryUpdate] are the seams the transport
 * feeds. Push arrives through the first of them; a realtime source will use
 * the same two. Nothing in this class knows what a push message is — no
 * Firebase type appears anywhere in this package, and the transport reaches
 * it only as already-validated delivery records.
 */
class NotificationCenterViewModel : ViewModel() {

    private val _state = MutableStateFlow<NotificationCenterUiState>(
        NotificationCenterUiState.Loading
    )
    val state: StateFlow<NotificationCenterUiState> = _state.asStateFlow()

    private var pageLimit = NOTIFICATION_PAGE_SIZE

    init {
        load()
        observeIncomingPush()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = NotificationCenterUiState.Loading
            delay(LOAD_DELAY_MS)
            pageLimit = NOTIFICATION_PAGE_SIZE
            val scenario = NotificationPreview.scenario as NotificationCenterUiState.Content
            // Messages that arrived before this screen existed are part of the
            // record too, so the load starts from what the inbox already holds.
            _state.value = project(
                scenario.copy(all = PushInbox.records.value + scenario.all)
            )
        }
    }

    /**
     * The delivery intelligence surface is one surface.
     *
     * Push arrivals feed the same read model the rest of the Notification
     * Center uses rather than a parallel "push" list, so an operator has one
     * place to look and one set of rules about what delivery state means.
     */
    private fun observeIncomingPush() {
        viewModelScope.launch {
            PushInbox.records.collect { onDeliveryRecords(it) }
        }
    }

    fun refresh() = load()

    fun onQueryChange(query: String) = update(resetPage = true) { it.copy(query = query) }

    fun onFiltersChange(filters: NotificationFilters) =
        update(resetPage = true) { it.copy(filters = filters) }

    fun onSortChange(sort: NotificationSort) = update(resetPage = true) { it.copy(sort = sort) }

    fun onQuickFilter(quick: NotificationQuickFilter) = update(resetPage = true) {
        it.copy(filters = it.filters.withQuickFilter(quick))
    }

    fun clearFilters() = update(resetPage = true) { it.copy(filters = NotificationFilters()) }

    fun loadMore() {
        val current = _state.value as? NotificationCenterUiState.Content ?: return
        if (!current.hasMore) return
        pageLimit += NOTIFICATION_PAGE_SIZE
        _state.value = project(current)
    }

    /**
     * Entry point for newly arrived delivery records.
     *
     * Merges by delivery id and re-orders, so a record arriving late lands
     * where it belongs rather than at the top of the list.
     */
    fun onDeliveryRecords(records: List<NotificationRecord>) = update {
        val merged = (records + it.all).distinctBy { record -> record.id }
        it.copy(all = merged)
    }

    /**
     * Entry point for a delivery state change on a record already held.
     *
     * Replaces the delivery half only. The source snapshot is untouched: a
     * retry, a failure or an exhaustion changes what happened to the message
     * and must never edit the incident it was about.
     */
    fun onDeliveryUpdate(deliveryId: String, delivery: NotificationDeliverySummary) = update {
        it.copy(
            all = it.all.map { record ->
                if (record.id == deliveryId) record.copy(delivery = delivery) else record
            }
        )
    }

    private fun update(
        resetPage: Boolean = false,
        transform: (NotificationCenterUiState.Content) -> NotificationCenterUiState.Content
    ) {
        val current = _state.value as? NotificationCenterUiState.Content ?: return
        if (resetPage) pageLimit = NOTIFICATION_PAGE_SIZE
        _state.value = project(transform(current))
    }

    /** Re-derives everything downstream of [NotificationCenterUiState.Content.all]. */
    private fun project(
        content: NotificationCenterUiState.Content
    ): NotificationCenterUiState.Content {
        val visible = content.all.resolve(content.query, content.filters, content.sort)
        return content.copy(
            visible = visible,
            page = visible.take(pageLimit),
            summary = summarize(visible),
            hasMore = visible.size > pageLimit
        )
    }

    private companion object {
        const val LOAD_DELAY_MS = 380L
    }
}

/** Holds one delivery record and the sequence of attempts behind it. */
class NotificationDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<NotificationDetailUiState>(
        NotificationDetailUiState.Loading
    )
    val state: StateFlow<NotificationDetailUiState> = _state.asStateFlow()

    private var loadedId: String? = null

    fun load(deliveryId: String) {
        if (loadedId == deliveryId && _state.value is NotificationDetailUiState.Content) return
        loadedId = deliveryId
        viewModelScope.launch {
            _state.value = NotificationDetailUiState.Loading
            delay(LOAD_DELAY_MS)
            // Preview records first, then messages that arrived on this
            // device. A record for neither is reported unavailable rather
            // than rebuilt from whatever the notification happened to say.
            _state.value = NotificationPreview.detailFor(deliveryId)
                .orElse { detailFromInbox(deliveryId) }
        }
    }

    fun refresh() {
        loadedId?.let {
            loadedId = null
            load(it)
        }
    }

    private fun detailFromInbox(deliveryId: String): NotificationDetailUiState {
        val record = PushInbox.records.value.firstOrNull { it.id == deliveryId }
            ?: return NotificationDetailUiState.Unavailable
        return NotificationDetailUiState.Content(
            NotificationDetailData(
                record = record,
                deliveryFields = notificationDeliveryFields(record),
                sourceFields = notificationSourceFields(record),
                attempts = record.delivery.attempts,
                links = notificationLinks(record),
                // A record built from an arriving message has read nothing
                // from the system, so its context is unknown rather than live.
                freshness = com.example.nexa.ui.common.DataFreshness.Unknown
            )
        )
    }

    private inline fun NotificationDetailUiState.orElse(
        fallback: () -> NotificationDetailUiState
    ): NotificationDetailUiState =
        if (this is NotificationDetailUiState.Unavailable) fallback() else this

    private companion object {
        const val LOAD_DELAY_MS = 300L
    }
}
