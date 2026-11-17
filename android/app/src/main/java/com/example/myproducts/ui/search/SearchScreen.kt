package com.example.myproducts.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myproducts.R
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.components.ErrorState
import com.example.myproducts.ui.components.LoadingState
import com.example.myproducts.ui.components.ProductTile
import com.example.myproducts.ui.theme.AppTheme
import kotlinx.serialization.Serializable

/** Navigation route for the search screen (no arguments). */
@Serializable
object SearchRoute

@Composable
fun SearchRoute(
    onBack: () -> Unit,
    onProductClick: (productId: String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::search,
        onBack = onBack,
        onProductClick = onProductClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onProductClick: (productId: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SearchBar(state, onQueryChange, onSearch)
            when (val results = state.results) {
                null -> Message(stringResource(R.string.search_prompt))
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(results)
                is UiState.Success ->
                    if (results.data.products.isEmpty()) {
                        Message(stringResource(R.string.search_no_results, results.data.query))
                    } else {
                        ResultsGrid(results.data, onProductClick)
                    }
            }
        }
    }
}

/** The text field and the explicit Search button the acceptance criteria call for. */
@Composable
private fun SearchBar(state: SearchUiState, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Row(
        modifier = Modifier.padding(AppTheme.spacing.l),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.search_field_label)) },
            singleLine = true,
            // The keyboard's search key does the same as the button, under the same rule.
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (state.canSearch) onSearch() }),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSearch,
            enabled = state.canSearch,
            modifier = Modifier.heightIn(min = AppTheme.sizes.minTouchTarget),
        ) {
            Text(stringResource(R.string.search_button))
        }
    }
}

@Composable
private fun ResultsGrid(results: SearchResults, onProductClick: (productId: String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = AppTheme.sizes.tileWidth),
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.s),
    ) {
        items(results.products, key = { it.id }) { product ->
            ProductTile(
                product = product,
                onClick = { onProductClick(product.id) },
                modifier = Modifier.padding(AppTheme.spacing.s),
            )
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = AppTheme.spacing.l, vertical = AppTheme.spacing.xl),
    )
}
