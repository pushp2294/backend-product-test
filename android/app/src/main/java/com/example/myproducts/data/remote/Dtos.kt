package com.example.myproducts.data.remote

import kotlinx.serialization.Serializable

// DTOs: exact mirrors of OUR backend's JSON contract (docs/PLAN.md section 3).
// `internal` plus the architecture test keep them inside the data package: they are
// mapped to domain types in Mappers.kt and never reach a ViewModel or screen.
// Unknown JSON fields are ignored (see NetworkModule's Json), so the server can add
// fields without breaking the app.

@Serializable
internal data class MoneyDto(val amountMinor: Long, val currency: String)

@Serializable
internal data class RatingDto(val average: Double, val count: Int)

@Serializable
internal data class CategoryDto(val id: String, val name: String)

@Serializable
internal data class ProductSummaryDto(
    val id: String,
    val title: String,
    val price: MoneyDto,
    val imageUrl: String,
    val rating: RatingDto,
    val categoryId: String,
)

@Serializable
internal data class ProductDetailDto(
    val id: String,
    val title: String,
    val price: MoneyDto,
    val imageUrl: String,
    val rating: RatingDto,
    val categoryId: String,
    val description: String,
    val category: CategoryDto,
)

/** GET /products/featured */
@Serializable
internal data class ProductListResponse(val products: List<ProductSummaryDto>)

/** GET /categories */
@Serializable
internal data class CategoryListResponse(val categories: List<CategoryDto>)

/** GET /categories/{categoryId}/products */
@Serializable
internal data class CategoryProductsResponse(
    val category: CategoryDto,
    val topRated: List<ProductSummaryDto>,
    val others: List<ProductSummaryDto>,
)

/** GET /search?q= */
@Serializable
internal data class SearchResponse(val query: String, val products: List<ProductSummaryDto>)

/** GET /products/{id} */
@Serializable
internal data class ProductResponse(val product: ProductDetailDto)

/** POST /enquiries body. Optional fields are left out of the JSON when null. */
@Serializable
internal data class EnquiryRequestDto(
    val productId: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val message: String? = null,
)

/** The one error shape every endpoint uses: { "error": { code, message, details? } }. */
@Serializable
internal data class ErrorResponse(val error: ErrorDto)

@Serializable
internal data class ErrorDto(
    val code: String,
    val message: String,
    val details: List<FieldErrorDto> = emptyList(),
)

@Serializable
internal data class FieldErrorDto(val field: String, val message: String)
