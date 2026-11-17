package com.example.myproducts.data

import com.example.myproducts.data.remote.CategoryDto
import com.example.myproducts.data.remote.CategoryProductsResponse
import com.example.myproducts.data.remote.EnquiryRequestDto
import com.example.myproducts.data.remote.MoneyDto
import com.example.myproducts.data.remote.ProductDetailDto
import com.example.myproducts.data.remote.ProductSummaryDto
import com.example.myproducts.data.remote.RatingDto
import com.example.myproducts.domain.Category
import com.example.myproducts.domain.CategoryProducts
import com.example.myproducts.domain.Enquiry
import com.example.myproducts.domain.Money
import com.example.myproducts.domain.ProductDetail
import com.example.myproducts.domain.ProductSummary
import com.example.myproducts.domain.Rating

// The boundary: DTOs in, domain types out (and the reverse for the enquiry request).

internal fun MoneyDto.toDomain() = Money(amountMinor = amountMinor, currency = currency)

internal fun RatingDto.toDomain() = Rating(average = average, count = count)

internal fun CategoryDto.toDomain() = Category(id = id, name = name)

internal fun ProductSummaryDto.toDomain() = ProductSummary(
    id = id,
    title = title,
    price = price.toDomain(),
    imageUrl = imageUrl,
    rating = rating.toDomain(),
    categoryId = categoryId,
)

internal fun ProductDetailDto.toDomain() = ProductDetail(
    id = id,
    title = title,
    price = price.toDomain(),
    imageUrl = imageUrl,
    rating = rating.toDomain(),
    description = description,
    category = category.toDomain(),
)

/** Keeps the server's split and order exactly: no re-sorting or merging on the client. */
internal fun CategoryProductsResponse.toDomain() = CategoryProducts(
    category = category.toDomain(),
    topRated = topRated.map { it.toDomain() },
    others = others.map { it.toDomain() },
)

/** Blank optional fields are sent as absent, not as "" (the server treats both the same). */
internal fun Enquiry.toDto() = EnquiryRequestDto(
    productId = productId,
    name = name,
    email = email,
    phone = phone?.takeIf { it.isNotBlank() },
    message = message?.takeIf { it.isNotBlank() },
)
