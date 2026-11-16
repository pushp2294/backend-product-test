package com.example.myproducts.data

import com.example.myproducts.data.remote.CategoryListResponse
import com.example.myproducts.data.remote.CategoryProductsResponse
import com.example.myproducts.data.remote.ProductListResponse
import com.example.myproducts.data.remote.ProductResponse
import com.example.myproducts.data.remote.SearchResponse
import com.example.myproducts.di.NetworkModule
import com.example.myproducts.domain.Category
import com.example.myproducts.domain.Money
import com.example.myproducts.domain.Rating
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Decodes REAL responses captured from our running backend (test/resources/contract/)
 * with the app's real DTOs and Json settings. If a DTO field name drifts from the
 * server's contract, these fail here instead of in the app.
 */
class ContractDecodingTest {
    private val json = NetworkModule.provideJson()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("contract/$name")) { "missing fixture $name" }.readText()

    @Test
    fun `featured decodes to 5 summaries in server order, mapped to domain types`() {
        val products = json.decodeFromString<ProductListResponse>(fixture("featured.json")).products.map { it.toDomain() }

        assertEquals(listOf("12", "11", "3", "18", "5"), products.map { it.id })
        assertEquals(Money(amountMinor = 11400, currency = "USD"), products[0].price)
        assertEquals(Rating(average = 4.8, count = 400), products[0].rating)
        assertEquals("electronics", products[0].categoryId)
    }

    @Test
    fun `categories decode with our slug ids and display names`() {
        val categories = json.decodeFromString<CategoryListResponse>(fixture("categories.json")).categories.map { it.toDomain() }

        assertEquals(Category("mens-clothing", "Men's clothing"), categories[2])
        assertEquals(4, categories.size)
    }

    @Test
    fun `category page keeps the server's topRated and others split and order exactly`() {
        val page = json.decodeFromString<CategoryProductsResponse>(fixture("category-electronics.json")).toDomain()

        assertEquals(Category("electronics", "Electronics"), page.category)
        assertEquals(listOf("12", "11"), page.topRated.map { it.id })
        assertEquals(listOf("9", "10", "13", "14"), page.others.map { it.id })
    }

    @Test
    fun `search results decode`() {
        val results = json.decodeFromString<SearchResponse>(fixture("search-men.json"))

        assertEquals("men", results.query)
        assertEquals(listOf("3", "2", "4"), results.products.map { it.id })
    }

    @Test
    fun `product detail decodes description and category`() {
        val product = json.decodeFromString<ProductResponse>(fixture("product-12.json")).product.toDomain()

        assertEquals("12", product.id)
        assertEquals(Category("electronics", "Electronics"), product.category)
        assertEquals(true, product.description.isNotBlank())
    }

    @Test
    fun `a field the server adds later is ignored, not a crash`() {
        val withExtra = fixture("product-12.json").replaceFirst("\"id\":\"12\",", "\"id\":\"12\",\"stock\":42,")

        val product = json.decodeFromString<ProductResponse>(withExtra).product

        assertEquals("12", product.id)
    }
}
