package com.example.myproducts.ui.product

import androidx.lifecycle.SavedStateHandle
import com.example.myproducts.domain.AppError
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.testing.FakeProductRepository
import com.example.myproducts.testing.MainDispatcherRule
import com.example.myproducts.ui.common.UiState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ProductDetailViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeProductRepository()

    private fun viewModelFor(productId: String) =
        ProductDetailViewModel(SavedStateHandle(mapOf(ProductDetailRoute.ARG_PRODUCT_ID to productId)), repository)

    @Test
    fun `loads the product named in the navigation route`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = viewModelFor("12")

        assertEquals(UiState.Loading, viewModel.state.value)
        advanceUntilIdle()

        assertEquals(listOf("12"), repository.requestedProductIds)
        assertEquals("WD 4TB Gaming Drive", (viewModel.state.value as UiState.Success).data.title)
    }

    @Test
    fun `a missing product says it is no longer available, with NO retry button`() = runTest(mainDispatcher.dispatcher) {
        repository.product = RepoResult.Failure(AppError.NotFound)
        val viewModel = viewModelFor("999")
        advanceUntilIdle()

        val error = viewModel.state.value as UiState.Error
        assertEquals("This product is no longer available.", error.message)
        assertNull(error.retry)
    }

    @Test
    fun `an outage offers retry`() = runTest(mainDispatcher.dispatcher) {
        repository.product = RepoResult.Failure(AppError.Unavailable)
        val viewModel = viewModelFor("12")
        advanceUntilIdle()

        val error = viewModel.state.value as UiState.Error
        assertEquals("Something went wrong.", error.message)
        assertNotNull(error.retry)
    }
}
