package com.example.myproducts.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myproducts.domain.CategoryProducts
import com.example.myproducts.domain.ProductRepository
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.ui.common.NotFoundSubject
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.common.toErrorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Category page: the category's top-rated products and the rest of it, exactly as the
 * server split and ordered them. Nothing is re-sorted or merged here.
 */
@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
) : ViewModel() {

    // Read by key (not toRoute()) so the ViewModel stays testable on the plain JVM.
    private val categoryId: String = checkNotNull(savedStateHandle[CategoryRoute.ARG_CATEGORY_ID]) {
        "CategoryViewModel opened without a categoryId"
    }

    private val _state = MutableStateFlow<UiState<CategoryProducts>>(UiState.Loading)
    val state: StateFlow<UiState<CategoryProducts>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.getCategoryProducts(categoryId)) {
                is RepoResult.Success -> UiState.Success(result.value)
                is RepoResult.Failure -> result.error.toErrorState(retry = ::load, notFound = NotFoundSubject.Category)
            }
        }
    }
}
