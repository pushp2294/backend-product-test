package com.example.myproducts.domain

// The app's own product types. The data layer maps API DTOs into these; screens and
// ViewModels only ever see these. Pure Kotlin: no Android, Retrofit or JSON imports.

/**
 * A price in integer minor units (cents, pence) plus an ISO 4217 currency code, exactly
 * as the backend sends it. Formatting for display happens in the UI (see MoneyFormat.kt).
 */
data class Money(val amountMinor: Long, val currency: String)

data class Rating(val average: Double, val count: Int)

data class Category(val id: String, val name: String)

/** What a product tile shows: home page, category page, search results. */
data class ProductSummary(
    val id: String,
    val title: String,
    val price: Money,
    val imageUrl: String,
    val rating: Rating,
    val categoryId: String,
)

/** Everything the product detail page shows. */
data class ProductDetail(
    val id: String,
    val title: String,
    val price: Money,
    val imageUrl: String,
    val rating: Rating,
    val description: String,
    val category: Category,
)

/** A category page: the server has already split and ordered the two groups. */
data class CategoryProducts(
    val category: Category,
    val topRated: List<ProductSummary>,
    val others: List<ProductSummary>,
)

/** A customer's enquiry about one product. Phone and message are optional. */
data class Enquiry(
    val productId: String,
    val name: String,
    val email: String,
    val phone: String?,
    val message: String?,
)
