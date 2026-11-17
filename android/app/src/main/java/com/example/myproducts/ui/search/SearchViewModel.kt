package com.example.myproducts.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myproducts.domain.ProductRepository
import com.example.myproducts.domain.ProductSummary
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.common.toErrorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The results of one search: what was searched for, and what matched (possibly nothing). */
data class SearchResults(val query: String, val products: List<ProductSummary>)

/**
 * The search screen's single state. Unlike the other screens it also owns form input,
 * so the usual UiState sits inside it as `results`.
 *
 * @property results null before the first search; otherwise Loading / Success / Error.
 * @property canSearch whether the Search button is enabled. False for empty or whitespace
 *   input (the server would answer 400, which the user should never see) and while a
 *   search is already running.
 */
data class SearchUiState(
    val query: String = "",
    val results: UiState<SearchResults>? = null,
) {
    val canSearch: Boolean
        get() = query.isNotBlank() && results !is UiState.Loading
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Runs a search for the current text. Does nothing if the button would be disabled. */
    fun search() {
        val current = _state.value
        if (!current.canSearch) return

        // Trimmed, so "  men " searches for "men" (the server trims too).
        val query = current.query.trim()
        searchJob?.cancel()
        _state.update { it.copy(results = UiState.Loading) }
        searchJob = viewModelScope.launch {
            val results = when (val result = repository.search(query)) {
                is RepoResult.Success -> UiState.Success(SearchResults(query, result.value))
                // Retry re-runs this same query, even if the field has been edited since.
                is RepoResult.Failure -> result.error.toErrorState(retry = { retry(query) })
            }
            _state.update { it.copy(results = results) }
        }
    }

    private fun retry(query: String) {
        _state.update { it.copy(query = query) }
        search()
    }
}
