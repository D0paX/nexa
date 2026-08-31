package com.example.nexa.ui.identity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = IdentitiesUiState.Loading
            delay(LOAD_DELAY_MS)
            _state.value = IdentityPreview.scenario
        }
    }

    fun refresh() = load()

    fun onQueryChange(query: String) = updateContent { it.copy(query = query) }

    fun onFiltersChange(filters: IdentityFilters) = updateContent { it.copy(filters = filters) }

    fun clearFilters() = updateContent { it.copy(filters = IdentityFilters()) }

    private fun updateContent(transform: (IdentitiesUiState.Content) -> IdentitiesUiState.Content) {
        val current = _state.value as? IdentitiesUiState.Content ?: return
        val updated = transform(current)
        _state.value = updated.copy(visible = updated.all.resolve(updated.query, updated.filters))
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
            _state.value = IdentityPreview.detailFor(identityId)
        }
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
