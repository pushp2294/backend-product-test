package com.example.myproducts.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import com.example.myproducts.R
import com.example.myproducts.domain.ProductSummary
import com.example.myproducts.domain.Rating
import com.example.myproducts.ui.common.formatMoney
import com.example.myproducts.ui.theme.AppTheme

/**
 * A product image loaded by Coil from the URL our API returned, with a placeholder while
 * loading and an error image if it fails (for example when the image host is down).
 */
@Composable
fun ProductImage(imageUrl: String, contentDescription: String, height: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        placeholder = painterResource(R.drawable.image_placeholder),
        error = painterResource(R.drawable.image_error),
        // Fit, not Crop: product photos have mixed aspect ratios and must not be cut off.
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    )
}

/** "★ 4.8 (400)", read out by screen readers as a full sentence. */
@Composable
fun RatingText(rating: Rating, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.rating_content_description, rating.average, rating.count)
    Text(
        text = stringResource(R.string.rating_format, rating.average, rating.count),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/**
 * A tappable product tile: image, title, price, rating. Used on home, category and search.
 * The caller sets the width: a fixed token width in a horizontal row, the cell width in a grid.
 */
@Composable
fun ProductTile(product: ProductSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
    ) {
        ProductImage(
            imageUrl = product.imageUrl,
            contentDescription = product.title,
            height = AppTheme.sizes.tileImageHeight,
            modifier = Modifier.padding(AppTheme.spacing.s),
        )
        Column(modifier = Modifier.padding(AppTheme.spacing.m)) {
            Text(
                text = product.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatMoney(product.price),
                style = AppTheme.priceStyle,
                modifier = Modifier.padding(top = AppTheme.spacing.xs),
            )
            RatingText(product.rating)
        }
    }
}
