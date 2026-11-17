package com.example.myproducts.testing

import com.example.myproducts.domain.Category
import com.example.myproducts.domain.CategoryProducts
import com.example.myproducts.domain.Enquiry
import com.example.myproducts.domain.Money
import com.example.myproducts.domain.ProductDetail
import com.example.myproducts.domain.ProductRepository
import com.example.myproducts.domain.ProductSummary
import com.example.myproducts.domain.Rating
import com.example.myproducts.domain.RepoResult

/**
 * ProductRepository for ViewModel tests. Each result is a plain var the test sets, and
 * every call is counted so tests can assert, for example, that retry really re-fetched.
 */
class FakeProductRepository : ProductRepository {
    var featured: RepoResult<List<ProductSummary>> = RepoResult.Success(sampleProducts)
    var categories: RepoResult<List<Category>> = RepoResult.Success(sampleCategories)
    var categoryProducts: RepoResult<CategoryProducts> = RepoResult.Success(
        CategoryProducts(sampleCategories[0], topRated = sampleProducts.take(2), others = sampleProducts.drop(2)),
    )
    var searchResults: RepoResult<List<ProductSummary>> = RepoResult.Success(sampleProducts)
    var product: RepoResult<ProductDetail> = RepoResult.Success(sampleDetail)
    var enquiryResult: RepoResult<Unit> = RepoResult.Success(Unit)

    var featuredCalls = 0
    val requestedProductIds = mutableListOf<String>()
    val searchedQueries = mutableListOf<String>()
    val submittedEnquiries = mutableListOf<Enquiry>()

    override suspend fun getFeatured(): RepoResult<List<ProductSummary>> {
        featuredCalls++
        return featured
    }

    override suspend fun getCategories() = categories

    override suspend fun getCategoryProducts(categoryId: String) = categoryProducts

    override suspend fun search(query: String): RepoResult<List<ProductSummary>> {
        searchedQueries += query
        return searchResults
    }

    override suspend fun getProduct(id: String): RepoResult<ProductDetail> {
        requestedProductIds += id
        return product
    }

    override suspend fun submitEnquiry(enquiry: Enquiry): RepoResult<Unit> {
        submittedEnquiries += enquiry
        return enquiryResult
    }

    companion object {
        fun summary(id: String) = ProductSummary(
            id = id,
            title = "Product $id",
            price = Money(1000, "USD"),
            imageUrl = "https://img.test/$id.png",
            rating = Rating(4.0, 10),
            categoryId = "electronics",
        )

        val sampleProducts = listOf("12", "11", "3", "18", "5").map(::summary)
        val sampleCategories = listOf(Category("electronics", "Electronics"), Category("jewelery", "Jewelery"))
        val sampleDetail = ProductDetail(
            id = "12",
            title = "WD 4TB Gaming Drive",
            price = Money(11400, "USD"),
            imageUrl = "https://img.test/12.png",
            rating = Rating(4.8, 400),
            description = "A drive.",
            category = sampleCategories[0],
        )
    }
}
