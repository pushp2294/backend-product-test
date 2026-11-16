package com.example.myproducts.data

import com.example.myproducts.data.remote.CatalogueApi
import com.example.myproducts.data.remote.CategoryListResponse
import com.example.myproducts.data.remote.CategoryProductsResponse
import com.example.myproducts.data.remote.EnquiryRequestDto
import com.example.myproducts.data.remote.ProductListResponse
import com.example.myproducts.data.remote.ProductResponse
import com.example.myproducts.data.remote.SearchResponse
import com.example.myproducts.di.NetworkModule
import com.example.myproducts.domain.AppError
import com.example.myproducts.domain.Enquiry
import com.example.myproducts.domain.RepoResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ProductRepositoryImplTest {
    private val json = NetworkModule.provideJson()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("contract/$name")).readText()

    private fun jsonBody(body: String) = body.toResponseBody("application/json".toMediaType())

    /** A CatalogueApi whose answers each test sets. Unset calls fail the test loudly. */
    private class FakeApi : CatalogueApi {
        var featured: suspend () -> ProductListResponse = { error("not set") }
        var product: suspend (String) -> ProductResponse = { error("not set") }
        var enquiry: suspend (EnquiryRequestDto) -> Response<Unit> = { error("not set") }
        val sentEnquiries = mutableListOf<EnquiryRequestDto>()

        override suspend fun getFeatured() = featured()
        override suspend fun getCategories(): CategoryListResponse = error("not used")
        override suspend fun getCategoryProducts(categoryId: String): CategoryProductsResponse = error("not used")
        override suspend fun search(query: String): SearchResponse = error("not used")
        override suspend fun getProduct(id: String) = product(id)
        override suspend fun submitEnquiry(body: EnquiryRequestDto): Response<Unit> {
            sentEnquiries += body
            return enquiry(body)
        }
    }

    private val api = FakeApi()
    private val repository = ProductRepositoryImpl(api, json)

    private val enquiry = Enquiry(productId = "12", name = "Ada", email = "ada@example.com", phone = "", message = "Hi")

    @Test
    fun `success is mapped to domain types`() = runTest {
        api.featured = { json.decodeFromString(fixture("featured.json")) }

        val result = repository.getFeatured()

        assertTrue(result is RepoResult.Success)
        assertEquals(listOf("12", "11", "3", "18", "5"), (result as RepoResult.Success).value.map { it.id })
    }

    @Test
    fun `a 404 from the server becomes Failure(NotFound)`() = runTest {
        api.product = { throw HttpException(Response.error<Any>(404, jsonBody(fixture("error-not-found.json")))) }

        assertEquals(RepoResult.Failure(AppError.NotFound), repository.getProduct("999"))
    }

    @Test
    fun `no network becomes Failure(Unavailable)`() = runTest {
        api.featured = { throw IOException("Failed to connect to /10.0.2.2:3000") }

        assertEquals(RepoResult.Failure(AppError.Unavailable), repository.getFeatured())
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown, never turned into a Failure`() = runTest {
        api.featured = { throw CancellationException("user left the screen") }

        repository.getFeatured()
    }

    @Test
    fun `enquiry 200 is success`() = runTest {
        api.enquiry = { Response.success(Unit) }

        assertEquals(RepoResult.Success(Unit), repository.submitEnquiry(enquiry))
    }

    @Test
    fun `enquiry ANY 2xx is success (such as 202 if enquiries move to a queue)`() = runTest {
        api.enquiry = { Response.success(202, Unit) }

        assertEquals(RepoResult.Success(Unit), repository.submitEnquiry(enquiry))
    }

    @Test
    fun `enquiry 502 EMAIL_FAILED is Failure(EmailFailed)`() = runTest {
        api.enquiry = {
            Response.error(502, jsonBody("""{"error":{"code":"EMAIL_FAILED","message":"Your enquiry could not be sent."}}"""))
        }

        assertEquals(RepoResult.Failure(AppError.EmailFailed), repository.submitEnquiry(enquiry))
    }

    @Test
    fun `enquiry 400 is Failure(Validation) with the server's field messages`() = runTest {
        api.enquiry = { Response.error(400, jsonBody(fixture("error-enquiry-validation.json"))) }

        val result = repository.submitEnquiry(enquiry)

        assertEquals(
            RepoResult.Failure(AppError.Validation(mapOf("name" to "Enter your name", "email" to "Enter a valid email address"))),
            result,
        )
    }

    @Test
    fun `enquiry sends blank optional fields as absent, not as empty strings`() = runTest {
        api.enquiry = { Response.success(Unit) }

        repository.submitEnquiry(enquiry.copy(phone = "  ", message = ""))

        assertEquals(EnquiryRequestDto(productId = "12", name = "Ada", email = "ada@example.com"), api.sentEnquiries.single())
    }
}
