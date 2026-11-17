package com.example.myproducts.ui.home

import com.example.myproducts.domain.AppError
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.testing.FakeProductRepository
import com.example.myproducts.testing.MainDispatcherRule
import com.example.myproducts.ui.common.UiState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeProductRepository()

    @Test
    fun `starts in Loading, then shows the 5 featured products and the categories`() = runTest(mainDispatcher.dispatcher) {
        val viewModel = HomeViewModel(repository)

        assertEquals(UiState.Loading, viewModel.state.value)

        advanceUntilIdle()

        val state = viewModel.state.value as UiState.Success
        assertEquals(listOf("12", "11", "3", "18", "5"), state.data.featured.map { it.id })
        assertEquals(listOf("electronics", "jewelery"), state.data.categories.map { it.id })
    }

    @Test
    fun `an outage shows the generic message with a retry that re-fetches and recovers`() = runTest(mainDispatcher.dispatcher) {
        repository.featured = RepoResult.Failure(AppError.Unavailable)
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        val error = viewModel.state.value as UiState.Error
        assertEquals("Something went wrong.", error.message)
        val retry = requireNotNull(error.retry) { "expected a retry action for an outage" }

        repository.featured = RepoResult.Success(FakeProductRepository.sampleProducts)
        retry()
        assertEquals(UiState.Loading, viewModel.state.value)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Success)
        assertEquals(2, repository.featuredCalls)
    }

    @Test
    fun `if categories fail, the page shows the error rather than half a home page`() = runTest(mainDispatcher.dispatcher) {
        repository.categories = RepoResult.Failure(AppError.Unavailable)
        val viewModel = HomeViewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value is UiState.Error)
    }
}
