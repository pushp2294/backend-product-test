package com.example.myproducts.ui.category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myproducts.R
import com.example.myproducts.domain.CategoryProducts
import com.example.myproducts.domain.ProductSummary
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.components.ErrorState
import com.example.myproducts.ui.components.LoadingState
import com.example.myproducts.ui.components.ProductTile
import com.example.myproducts.ui.theme.AppTheme
import kotlinx.serialization.Serializable

/** Navigation route for a category page. */
@Serializable
data class CategoryRoute(val categoryId: String) {
    companion object {
        /** Must equal the property name above: Navigation stores the argument under it. */
        const val ARG_CATEGORY_ID = "categoryId"
    }
}

@Composable
fun CategoryRoute(
    onBack: () -> Unit,
    onProductClick: (productId: String) -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CategoryScreen(state = state, onBack = onBack, onProductClick = onProductClick)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    state: UiState<CategoryProducts>,
    onBack: () -> Unit,
    onProductClick: (productId: String) -> Unit,
) {
    val title = (state as? UiState.Success)?.data?.category?.name ?: stringResource(R.string.category_title_loading)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            UiState.Loading -> LoadingState(modifier)
            is UiState.Error -> ErrorState(state, modifier)
            is UiState.Success -> CategoryContent(state.data, onProductClick, modifier)
        }
    }
}

/**
 * Two visually distinct groups, in the order the server sent them:
 * "Top rated" on a tinted band, then "More in <category>" on the plain background.
 */
@Composable
private fun CategoryContent(
    content: CategoryProducts,
    onProductClick: (productId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    LazyVerticalGrid(
        // As many columns as fit at the token tile width: 2 on a phone, more on a tablet.
        columns = GridCells.Adaptive(minSize = AppTheme.sizes.tileWidth),
        modifier = modifier,
        contentPadding = PaddingValues(bottom = spacing.xl),
    ) {
        productSection(
            title = { stringResource(R.string.category_top_rated, content.category.name) },
            products = content.topRated,
            onProductClick = onProductClick,
            highlighted = true,
        )
        if (content.others.isNotEmpty()) {
            productSection(
                title = { stringResource(R.string.category_others, content.category.name) },
                products = content.others,
                onProductClick = onProductClick,
                highlighted = false,
            )
        }
    }
}

/** A full-width heading followed by its tiles. `highlighted` puts the group on a tinted band. */
private fun LazyGridScope.productSection(
    title: @Composable () -> String,
    products: List<ProductSummary>,
    onProductClick: (productId: String) -> Unit,
    highlighted: Boolean,
) {
    // maxLineSpan makes the heading span every column of the grid.
    item(span = { GridItemSpan(maxLineSpan) }) {
        Text(
            text = title(),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .sectionBackground(highlighted)
                .padding(horizontal = AppTheme.spacing.l, vertical = AppTheme.spacing.m),
        )
    }
    items(products, key = { it.id }) { product ->
        ProductTile(
            product = product,
            onClick = { onProductClick(product.id) },
            modifier = Modifier
                .sectionBackground(highlighted)
                .padding(AppTheme.spacing.s),
        )
    }
}

@Composable
private fun Modifier.sectionBackground(highlighted: Boolean): Modifier =
    background(if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
