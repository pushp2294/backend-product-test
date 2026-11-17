package com.example.myproducts.ui.search

import com.example.myproducts.domain.AppError
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.testing.FakeProductRepository
import com.example.myproducts.testing.MainDispatcherRule
import com.example.myproducts.ui.common.UiState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeProductRepository()
    private val viewModel by lazy { SearchViewModel(repository) }

    @Test
    fun `before any search there are no results, and the button is disabled`() {
        assertNull(viewModel.state.value.results)
        assertFalse(viewModel.state.value.canSearch)
    }

    @Test
    fun `empty or whitespace input keeps the button disabled and never calls the server`() = runTest(mainDispatcher.dispatcher) {
        for (input in listOf("", "   ", "\t\n")) {
            viewModel.onQueryChange(input)
            assertFalse("canSearch for '$input'", viewModel.state.value.canSearch)
            viewModel.search()
        }
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.searchedQueries)
        assertNull(viewModel.state.value.results)
    }

    @Test
    fun `searching sends the trimmed term and shows the results`() = runTest(mainDispatcher.dispatcher) {
        viewModel.onQueryChange("  men ")
        assertTrue(viewModel.state.value.canSearch)

        viewModel.search()
        assertEquals(UiState.Loading, viewModel.state.value.results)
        advanceUntilIdle()

        assertEquals(listOf("men"), repository.searchedQueries)
        val results = (viewModel.state.value.results as UiState.Success).data
        assertEquals("men", results.query)
        assertEquals(5, results.products.size)
    }

    @Test
    fun `the button is disabled while a search is running, so it cannot be sent twice`() = runTest(mainDispatcher.dispatcher) {
        viewModel.onQueryChange("men")
        viewModel.search()

        assertFalse(viewModel.state.value.canSearch)
        viewModel.search() // a second tap while loading is ignored
        advanceUntilIdle()

        assertEquals(listOf("men"), repository.searchedQueries)
        assertTrue(viewModel.state.value.canSearch)
    }

    @Test
    fun `no matches is a Success with an empty list, not an error`() = runTest(mainDispatcher.dispatcher) {
        repository.searchResults = RepoResult.Success(emptyList())
        viewModel.onQueryChange("submarine")
        viewModel.search()
        advanceUntilIdle()

        val results = (viewModel.state.value.results as UiState.Success).data
        assertEquals("submarine", results.query)
        assertTrue(results.products.isEmpty())
    }

    @Test
    fun `an outage offers retry, which re-runs the SAME query even if the field was edited`() = runTest(mainDispatcher.dispatcher) {
        repository.searchResults = RepoResult.Failure(AppError.Unavailable)
        viewModel.onQueryChange("men")
        viewModel.search()
        advanceUntilIdle()

        val error = viewModel.state.value.results as UiState.Error
        assertEquals("Something went wrong.", error.message)

        viewModel.onQueryChange("jacket")
        repository.searchResults = RepoResult.Success(FakeProductRepository.sampleProducts)
        requireNotNull(error.retry) { "expected a retry action" }()
        advanceUntilIdle()

        assertEquals(listOf("men", "men"), repository.searchedQueries)
        assertTrue(viewModel.state.value.results is UiState.Success)
    }
}
