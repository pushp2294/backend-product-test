package com.example.myproducts.ui.product

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myproducts.domain.ProductDetail
import com.example.myproducts.domain.ProductRepository
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.common.toErrorState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Product detail page for the product id in the navigation route. */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
) : ViewModel() {

    // Navigation stores ProductDetailRoute.productId under its property name. Reading it by
    // key (rather than savedStateHandle.toRoute()) keeps this ViewModel testable on the
    // plain JVM, without Android's Bundle.
    private val productId: String = checkNotNull(savedStateHandle[ProductDetailRoute.ARG_PRODUCT_ID]) {
        "ProductDetailViewModel opened without a productId"
    }

    private val _state = MutableStateFlow<UiState<ProductDetail>>(UiState.Loading)
    val state: StateFlow<UiState<ProductDetail>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = repository.getProduct(productId)) {
                is RepoResult.Success -> UiState.Success(result.value)
                // NotFound -> "This product is no longer available." with no retry button.
                is RepoResult.Failure -> result.error.toErrorState(retry = ::load)
            }
        }
    }
}
