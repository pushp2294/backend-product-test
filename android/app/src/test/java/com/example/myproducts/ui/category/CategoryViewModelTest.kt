package com.example.myproducts.ui.category

import androidx.lifecycle.SavedStateHandle
import com.example.myproducts.domain.AppError
import com.example.myproducts.domain.CategoryProducts
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.testing.FakeProductRepository
import com.example.myproducts.testing.FakeProductRepository.Companion.sampleCategories
import com.example.myproducts.testing.FakeProductRepository.Companion.summary
import com.example.myproducts.testing.MainDispatcherRule
import com.example.myproducts.ui.common.UiState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CategoryViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeProductRepository()

    private fun viewModelFor(categoryId: String) =
        CategoryViewModel(SavedStateHandle(mapOf(CategoryRoute.ARG_CATEGORY_ID to categoryId)), repository)

    @Test
    fun `keeps the server's topRated and others split and order exactly - no re-sorting or merging`() =
        runTest(mainDispatcher.dispatcher) {
            // Deliberately NOT in rating or id order, to prove the ViewModel passes it through as-is.
            repository.categoryProducts = RepoResult.Success(
                CategoryProducts(sampleCategories[0], topRated = listOf(summary("12"), summary("11")), others = listOf(summary("14"), summary("9"))),
            )
            val viewModel = viewModelFor("electronics")

            assertEquals(UiState.Loading, viewModel.state.value)
            advanceUntilIdle()

            val data = (viewModel.state.value as UiState.Success).data
            assertEquals(listOf("12", "11"), data.topRated.map { it.id })
            assertEquals(listOf("14", "9"), data.others.map { it.id })
        }

    @Test
    fun `an outage offers retry, and retry recovers`() = runTest(mainDispatcher.dispatcher) {
        val good = repository.categoryProducts
        repository.categoryProducts = RepoResult.Failure(AppError.Unavailable)
        val viewModel = viewModelFor("electronics")
        advanceUntilIdle()

        val retry = requireNotNull((viewModel.state.value as UiState.Error).retry) { "expected a retry action" }
        repository.categoryProducts = good
        retry()
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Success)
    }

    @Test
    fun `an unknown category says category, not product, and offers no retry`() = runTest(mainDispatcher.dispatcher) {
        repository.categoryProducts = RepoResult.Failure(AppError.NotFound)
        val viewModel = viewModelFor("hats")
        advanceUntilIdle()

        val error = viewModel.state.value as UiState.Error
        assertEquals("This category is no longer available.", error.message)
        assertEquals(null, error.retry)
    }
}
