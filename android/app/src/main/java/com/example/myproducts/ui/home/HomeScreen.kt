package com.example.myproducts.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myproducts.R
import com.example.myproducts.domain.Category
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.components.ErrorState
import com.example.myproducts.ui.components.LoadingState
import com.example.myproducts.ui.components.ProductTile
import com.example.myproducts.ui.theme.AppTheme

/** Connects the home screen to its ViewModel. Navigation is passed in as callbacks. */
@Composable
fun HomeRoute(
    onProductClick: (productId: String) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onSearchClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // collectAsStateWithLifecycle stops collecting while the app is in the background.
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onProductClick = onProductClick,
        onCategoryClick = onCategoryClick,
        onSearchClick = onSearchClick,
    )
}

/** Stateless: draws whatever state it is given, so it can be previewed and reasoned about alone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState<HomeContent>,
    onProductClick: (productId: String) -> Unit,
    onCategoryClick: (Category) -> Unit,
    onSearchClick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = { TextButton(onClick = onSearchClick) { Text(stringResource(R.string.search_title)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            UiState.Loading -> LoadingState(modifier)
            is UiState.Error -> ErrorState(state, modifier)
            is UiState.Success -> HomeContentList(state.data, onProductClick, onCategoryClick, modifier)
        }
    }
}

@Composable
private fun HomeContentList(
    content: HomeContent,
    onProductClick: (productId: String) -> Unit,
    onCategoryClick: (Category) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = AppTheme.spacing
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = spacing.l),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        item {
            SectionHeader(stringResource(R.string.home_featured))
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.l),
                horizontalArrangement = Arrangement.spacedBy(spacing.m),
            ) {
                items(content.featured, key = { it.id }) { product ->
                    ProductTile(
                        product = product,
                        onClick = { onProductClick(product.id) },
                        modifier = Modifier.width(AppTheme.sizes.tileWidth),
                    )
                }
            }
        }
        item {
            SectionHeader(stringResource(R.string.home_categories), Modifier.padding(top = spacing.l))
        }
        items(content.categories, key = { it.id }) { category ->
            CategoryRow(category = category, onClick = { onCategoryClick(category) })
            HorizontalDivider(modifier = Modifier.padding(horizontal = spacing.l))
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = AppTheme.spacing.l, vertical = AppTheme.spacing.s),
    )
}

@Composable
private fun CategoryRow(category: Category, onClick: () -> Unit) {
    Text(
        text = category.name,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = AppTheme.sizes.minTouchTarget)
            .clickable(onClick = onClick)
            .padding(horizontal = AppTheme.spacing.l, vertical = AppTheme.spacing.m),
    )
}
