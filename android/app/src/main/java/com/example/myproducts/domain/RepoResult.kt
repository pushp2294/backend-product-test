package com.example.myproducts.domain

/**
 * The outcome of a repository call: a value, or one of the app's known errors.
 *
 * Used instead of Kotlin's built-in Result because Result carries only a Throwable, and
 * the UI needs to tell a validation failure from an outage from a missing product.
 */
sealed interface RepoResult<out T> {
    data class Success<T>(val value: T) : RepoResult<T>
    data class Failure(val error: AppError) : RepoResult<Nothing>
}

/** Every failure the UI has to handle, and nothing more. */
sealed interface AppError {
    /**
     * Something went wrong that retrying might fix: no network, a timeout, the product
     * source is down (502), a server bug (500), or any error code this app does not know.
     */
    data object Unavailable : AppError

    /** The product or category does not exist (404). */
    data object NotFound : AppError

    /** The server rejected the input (400). Maps field name -> message, e.g. "email" -> "Enter a valid email address". */
    data class Validation(val fieldErrors: Map<String, String>) : AppError

    /** The enquiry could not be emailed (502 EMAIL_FAILED). The form must stay on screen. */
    data object EmailFailed : AppError
}
