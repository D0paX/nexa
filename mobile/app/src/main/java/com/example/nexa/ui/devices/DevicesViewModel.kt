package com.example.nexa.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = DevicesUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = DevicesPreview.scenario
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
     * Applies a change and re-resolves the visible list once, so `visible`
     * is always consistent with query + filters + sort.
     */
    private fun updateContent(transform: (DevicesUiState.Content) -> DevicesUiState.Content) {
        val current = _state.value as? DevicesUiState.Content ?: return
        val updated = transform(current)
        _state.value = updated.copy(
            visible = updated.all.resolve(updated.query, updated.filters, updated.sort)
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
            _state.value = DevicesPreview.detailFor(mac)
        }
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
