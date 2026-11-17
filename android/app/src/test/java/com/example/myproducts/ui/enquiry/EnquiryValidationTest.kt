package com.example.myproducts.ui.enquiry

import org.junit.Assert.assertEquals
import org.junit.Test

/** The client mirrors the backend's rules so obvious mistakes never cost a request. */
class EnquiryValidationTest {
    private val valid = EnquiryForm(name = "Ada", email = "ada@example.com")

    @Test
    fun `name and email are all that is required`() {
        assertEquals(emptyMap<EnquiryField, String>(), validateEnquiry(valid))
    }

    @Test
    fun `phone allows digits, spaces, plus and hyphen only`() {
        assertEquals(emptyMap<EnquiryField, String>(), validateEnquiry(valid.copy(phone = "+44 7700-900123")))
        assertEquals(setOf(EnquiryField.Phone), validateEnquiry(valid.copy(phone = "call me")).keys)
    }

    @Test
    fun `length limits match the server`() {
        assertEquals(setOf(EnquiryField.Name), validateEnquiry(valid.copy(name = "a".repeat(101))).keys)
        assertEquals(setOf(EnquiryField.Message), validateEnquiry(valid.copy(message = "a".repeat(1001))).keys)
        assertEquals(emptyMap<EnquiryField, String>(), validateEnquiry(valid.copy(message = "a".repeat(1000))))
    }

    @Test
    fun `whitespace-only name is empty`() {
        assertEquals("Enter your name", validateEnquiry(valid.copy(name = "   "))[EnquiryField.Name])
    }

    @Test
    fun `field names match the server's details field names`() {
        assertEquals(EnquiryField.Email, EnquiryField.fromServerName("email"))
        assertEquals(null, EnquiryField.fromServerName("productId"))
    }
}
