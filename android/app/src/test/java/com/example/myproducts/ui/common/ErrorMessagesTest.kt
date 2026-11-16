package com.example.myproducts.ui.common

import com.example.myproducts.domain.AppError
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the exact customer-facing wording agreed for each error. */
class ErrorMessagesTest {
    @Test
    fun `each error has its agreed message`() {
        assertEquals("Something went wrong.", AppError.Unavailable.toUserMessage())
        assertEquals("This product is no longer available.", AppError.NotFound.toUserMessage())
        assertEquals("This category is no longer available.", AppError.NotFound.toUserMessage(NotFoundSubject.Category))
        assertEquals("We couldn't send your enquiry. Please try again.", AppError.EmailFailed.toUserMessage())
        assertEquals("Please check the highlighted fields.", AppError.Validation(mapOf("email" to "x")).toUserMessage())
    }
}
