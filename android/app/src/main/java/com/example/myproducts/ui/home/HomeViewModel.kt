package com.example.myproducts.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myproducts.domain.Category
import com.example.myproducts.domain.ProductRepository
import com.example.myproducts.domain.ProductSummary
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.common.toErrorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Everything the home page shows. */
data class HomeContent(
    val featured: List<ProductSummary>,
    val categories: List<Category>,
)

/** Home page: the 5 featured products, plus the categories as navigation entries. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProductRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<HomeContent>>(UiState.Loading)
    val state: StateFlow<UiState<HomeContent>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            // Both requests start at once; the page needs both, so it waits for both.
            val featuredRequest = async { repository.getFeatured() }
            val categoriesRequest = async { repository.getCategories() }
            val featured = featuredRequest.await()
            val categories = categoriesRequest.await()

            _state.value = when {
                featured is RepoResult.Failure -> featured.error.toErrorState(retry = ::load)
                categories is RepoResult.Failure -> categories.error.toErrorState(retry = ::load)
                featured is RepoResult.Success && categories is RepoResult.Success ->
                    UiState.Success(HomeContent(featured.value, categories.value))
                // Unreachable: each result is either Success or Failure. Needed only because
                // the compiler cannot prove that across two variables.
                else -> error("unreachable")
            }
        }
    }
}
