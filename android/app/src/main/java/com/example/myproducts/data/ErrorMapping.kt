package com.example.myproducts.data

import com.example.myproducts.data.remote.ErrorResponse
import com.example.myproducts.domain.AppError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

/**
 * Turns an exception from a Retrofit call into an AppError.
 *
 * Only the failures we expect are mapped. Anything else is a bug in this app and is
 * rethrown, so it fails loudly instead of hiding behind "Something went wrong".
 */
internal fun Throwable.toAppError(json: Json): AppError = when (this) {
    // Non-2xx from our backend: read our { error: { code } } body.
    is HttpException -> errorFromBody(response()?.errorBody()?.string(), json)
    // No connection, DNS failure, timeout, connection reset.
    is IOException -> AppError.Unavailable
    // A 2xx whose body doesn't match our DTOs: treat as a server problem, not a crash.
    is SerializationException -> AppError.Unavailable
    else -> throw this
}

/**
 * Maps our backend's error body to an AppError by its `code`.
 *
 * Deliberately forgiving: a missing, empty, non-JSON or unexpectedly shaped body, and any
 * code this app does not recognise (PAYLOAD_TOO_LARGE today, new codes tomorrow), all
 * become Unavailable. An error response must never crash the app.
 */
internal fun errorFromBody(body: String?, json: Json): AppError {
    if (body == null) return AppError.Unavailable

    val error = try {
        json.decodeFromString<ErrorResponse>(body).error
    } catch (e: IllegalArgumentException) {
        // SerializationException extends IllegalArgumentException: covers an empty body,
        // non-JSON (e.g. a proxy's HTML page), truncated JSON and the wrong shape.
        return AppError.Unavailable
    }

    return when (error.code) {
        "NOT_FOUND" -> AppError.NotFound
        // If the server lists the same field twice, keep the first message.
        "VALIDATION_ERROR" -> AppError.Validation(
            error.details.distinctBy { it.field }.associate { it.field to it.message },
        )
        "EMAIL_FAILED" -> AppError.EmailFailed
        // UPSTREAM_UNAVAILABLE, INTERNAL_ERROR, PAYLOAD_TOO_LARGE and anything unknown.
        else -> AppError.Unavailable
    }
}
