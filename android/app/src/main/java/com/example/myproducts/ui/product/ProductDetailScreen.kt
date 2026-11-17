package com.example.myproducts.ui.product

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.example.myproducts.domain.ProductDetail
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.common.formatMoney
import com.example.myproducts.ui.components.ErrorState
import com.example.myproducts.ui.components.LoadingState
import com.example.myproducts.ui.components.ProductImage
import com.example.myproducts.ui.components.RatingText
import com.example.myproducts.ui.theme.AppTheme
import kotlinx.serialization.Serializable

/** Navigation route for this screen. Type-safe: the product id is a real property, not a string path. */
@Serializable
data class ProductDetailRoute(val productId: String) {
    companion object {
        /** Must equal the property name above: Navigation stores the argument under it. */
        const val ARG_PRODUCT_ID = "productId"
    }
}

@Composable
fun ProductDetailRoute(
    onBack: () -> Unit,
    onEnquire: (productId: String, productTitle: String) -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProductDetailScreen(state = state, onBack = onBack, onEnquire = onEnquire)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    state: UiState<ProductDetail>,
    onBack: () -> Unit,
    onEnquire: (productId: String, productTitle: String) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.product_title)) },
                // A text "Back" button rather than an icon: the icon set is a separate
                // dependency we have not added.
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (state) {
            UiState.Loading -> LoadingState(modifier)
            is UiState.Error -> ErrorState(state, modifier)
            is UiState.Success -> ProductDetailContent(state.data, onEnquire, modifier)
        }
    }
}

@Composable
private fun ProductDetailContent(
    product: ProductDetail,
    onEnquire: (productId: String, productTitle: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(AppTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m),
    ) {
        ProductImage(
            imageUrl = product.imageUrl,
            contentDescription = product.title,
            height = AppTheme.sizes.detailImageHeight,
        )
        Text(text = product.title, style = MaterialTheme.typography.headlineMedium)
        Text(text = formatMoney(product.price), style = AppTheme.priceStyle)
        Text(
            text = product.category.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        RatingText(product.rating)
        Text(text = product.description, style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = { onEnquire(product.id, product.title) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppTheme.sizes.minTouchTarget),
        ) {
            Text(stringResource(R.string.product_enquire))
        }
    }
}
