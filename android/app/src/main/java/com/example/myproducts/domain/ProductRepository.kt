package com.example.myproducts.domain

/**
 * Where the app gets product data and sends enquiries. ViewModels depend on this
 * interface only, never on Retrofit, so they can be tested with a fake.
 *
 * No function throws for an expected failure: every outcome comes back as a RepoResult.
 */
interface ProductRepository {
    suspend fun getFeatured(): RepoResult<List<ProductSummary>>

    suspend fun getCategories(): RepoResult<List<Category>>

    suspend fun getCategoryProducts(categoryId: String): RepoResult<CategoryProducts>

    /** `query` must not be blank; the screen disables search until it isn't. */
    suspend fun search(query: String): RepoResult<List<ProductSummary>>

    suspend fun getProduct(id: String): RepoResult<ProductDetail>

    /** Success means the backend accepted the enquiry and the sales inbox email was sent. */
    suspend fun submitEnquiry(enquiry: Enquiry): RepoResult<Unit>
}
