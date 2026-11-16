package com.example.myproducts.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Our backend's HTTP API, described for Retrofit. Paths are RELATIVE (no leading "/") so
 * they resolve under the base URL's "/api/v1/" instead of replacing it.
 *
 * The GET functions throw HttpException on a non-2xx status; ProductRepositoryImpl
 * catches it. @Path values are URL-encoded by Retrofit, so an id can't change the path.
 */
internal interface CatalogueApi {
    @GET("products/featured")
    suspend fun getFeatured(): ProductListResponse

    @GET("categories")
    suspend fun getCategories(): CategoryListResponse

    @GET("categories/{categoryId}/products")
    suspend fun getCategoryProducts(@Path("categoryId") categoryId: String): CategoryProductsResponse

    @GET("search")
    suspend fun search(@Query("q") query: String): SearchResponse

    @GET("products/{id}")
    suspend fun getProduct(@Path("id") id: String): ProductResponse

    /**
     * Returns the raw Response so ANY 2xx counts as success. The backend sends 200
     * {"status":"sent"} today; a future move to a queue would make it 202. Unit = the
     * body is not read.
     */
    @POST("enquiries")
    suspend fun submitEnquiry(@Body body: EnquiryRequestDto): Response<Unit>
}
