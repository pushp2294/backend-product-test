package com.example.myproducts.ui.common

import com.example.myproducts.domain.AppError

/**
 * The one place an AppError becomes words a customer reads. Kept out of Composables so
 * ViewModels can be tested without an Android Context.
 *
 * Known cost: plain Kotlin strings cannot be translated. The production answer is string
 * resources, with this mapper returning resource IDs instead (see DECISIONS.md).
 */
fun AppError.toUserMessage(notFound: NotFoundSubject = NotFoundSubject.Product): String = when (this) {
    AppError.Unavailable -> "Something went wrong."
    AppError.NotFound -> when (notFound) {
        NotFoundSubject.Product -> "This product is no longer available."
        NotFoundSubject.Category -> "This category is no longer available."
    }
    // Field messages are shown inline on the form; this is the summary line above it.
    is AppError.Validation -> "Please check the highlighted fields."
    AppError.EmailFailed -> "We couldn't send your enquiry. Please try again."
}

/** What a screen was looking for, so a 404 can say "product" or "category" correctly. */
enum class NotFoundSubject { Product, Category }
