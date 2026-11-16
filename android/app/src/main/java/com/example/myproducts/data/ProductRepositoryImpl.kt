package com.example.myproducts.data

import com.example.myproducts.data.remote.CatalogueApi
import com.example.myproducts.domain.Category
import com.example.myproducts.domain.CategoryProducts
import com.example.myproducts.domain.Enquiry
import com.example.myproducts.domain.ProductDetail
import com.example.myproducts.domain.ProductRepository
import com.example.myproducts.domain.ProductSummary
import com.example.myproducts.domain.RepoResult
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import javax.inject.Inject

/** ProductRepository backed by our backend's HTTP API. */
internal class ProductRepositoryImpl @Inject constructor(
    private val api: CatalogueApi,
    private val json: Json,
) : ProductRepository {

    override suspend fun getFeatured(): RepoResult<List<ProductSummary>> =
        call { api.getFeatured().products.map { it.toDomain() } }

    override suspend fun getCategories(): RepoResult<List<Category>> =
        call { api.getCategories().categories.map { it.toDomain() } }

    override suspend fun getCategoryProducts(categoryId: String): RepoResult<CategoryProducts> =
        call { api.getCategoryProducts(categoryId).toDomain() }

    override suspend fun search(query: String): RepoResult<List<ProductSummary>> =
        call { api.search(query).products.map { it.toDomain() } }

    override suspend fun getProduct(id: String): RepoResult<ProductDetail> =
        call { api.getProduct(id).product.toDomain() }

    override suspend fun submitEnquiry(enquiry: Enquiry): RepoResult<Unit> {
        // This endpoint returns a raw Response, so a non-2xx does NOT throw; check it here.
        return when (val result = call { api.submitEnquiry(enquiry.toDto()) }) {
            is RepoResult.Failure -> result
            is RepoResult.Success -> {
                val response = result.value
                // isSuccessful = any 2xx (200 today, 202 if enquiries ever move to a queue).
                if (response.isSuccessful) {
                    RepoResult.Success(Unit)
                } else {
                    RepoResult.Failure(errorFromBody(response.errorBody()?.string(), json))
                }
            }
        }
    }

    /** Runs one API call, turning expected failures into RepoResult.Failure. */
    private inline fun <T> call(block: () -> T): RepoResult<T> = try {
        RepoResult.Success(block())
    } catch (e: CancellationException) {
        // The coroutine was cancelled (e.g. the user left the screen). This must propagate,
        // or the coroutine would carry on as if nothing happened.
        throw e
    } catch (e: Exception) {
        RepoResult.Failure(e.toAppError(json))
    }
}
