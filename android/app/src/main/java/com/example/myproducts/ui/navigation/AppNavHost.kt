package com.example.myproducts.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myproducts.ui.category.CategoryRoute
import com.example.myproducts.ui.enquiry.EnquiryRoute
import com.example.myproducts.ui.home.HomeRoute
import com.example.myproducts.ui.product.ProductDetailRoute
import com.example.myproducts.ui.search.SearchRoute
import kotlinx.serialization.Serializable

/** Route for the home screen (no arguments). */
@Serializable
object HomeDestination

/**
 * Every screen and how to move between them, in one place. Screens never see the
 * NavController; they only get callbacks such as onProductClick.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = HomeDestination) {
        composable<HomeDestination> {
            HomeRoute(
                onProductClick = { productId -> navController.navigate(ProductDetailRoute(productId)) },
                onCategoryClick = { category -> navController.navigate(CategoryRoute(category.id)) },
                onSearchClick = { navController.navigate(SearchRoute) },
            )
        }
        composable<CategoryRoute> {
            CategoryRoute(
                onBack = { navController.popBackStack() },
                onProductClick = { productId -> navController.navigate(ProductDetailRoute(productId)) },
            )
        }
        composable<SearchRoute> {
            SearchRoute(
                onBack = { navController.popBackStack() },
                onProductClick = { productId -> navController.navigate(ProductDetailRoute(productId)) },
            )
        }
        composable<ProductDetailRoute> {
            ProductDetailRoute(
                onBack = { navController.popBackStack() },
                onEnquire = { productId, productTitle -> navController.navigate(EnquiryRoute(productId, productTitle)) },
            )
        }
        composable<EnquiryRoute> {
            // Back (top bar, "Back to product", or the system back gesture) pops this
            // screen and returns to the product it was opened from.
            EnquiryRoute(onBack = { navController.popBackStack() })
        }
    }
}
