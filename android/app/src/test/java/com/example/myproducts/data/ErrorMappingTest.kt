package com.example.myproducts.data

import com.example.myproducts.di.NetworkModule
import com.example.myproducts.domain.AppError
import com.example.myproducts.ui.common.toUserMessage
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ErrorMappingTest {
    private val json = NetworkModule.provideJson()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("contract/$name")).readText()

    private fun httpError(status: Int, body: String): HttpException =
        HttpException(Response.error<Any>(status, body.toResponseBody("application/json".toMediaType())))

    private fun body(code: String) = """{"error":{"code":"$code","message":"irrelevant"}}"""

    // ---- Known codes, from real captured server responses where we have them ----

    @Test
    fun `NOT_FOUND (real 404 body) maps to NotFound`() {
        assertEquals(AppError.NotFound, errorFromBody(fixture("error-not-found.json"), json))
    }

    @Test
    fun `VALIDATION_ERROR (real 400 body) maps to Validation with each field and its message`() {
        val error = errorFromBody(fixture("error-enquiry-validation.json"), json)

        assertEquals(
            AppError.Validation(mapOf("name" to "Enter your name", "email" to "Enter a valid email address")),
            error,
        )
    }

    @Test
    fun `EMAIL_FAILED maps to EmailFailed`() {
        assertEquals(AppError.EmailFailed, errorFromBody(body("EMAIL_FAILED"), json))
    }

    @Test
    fun `UPSTREAM_UNAVAILABLE and INTERNAL_ERROR map to Unavailable`() {
        assertEquals(AppError.Unavailable, errorFromBody(body("UPSTREAM_UNAVAILABLE"), json))
        assertEquals(AppError.Unavailable, errorFromBody(body("INTERNAL_ERROR"), json))
    }

    // ---- (b) Unknown codes must not crash ----

    @Test
    fun `an unknown error code maps to Unavailable and the generic message, not an exception`() {
        val error = errorFromBody(body("SOMETHING_NEW_NEXT_YEAR"), json)

        assertEquals(AppError.Unavailable, error)
        assertEquals("Something went wrong.", error.toUserMessage())
    }

    @Test
    fun `PAYLOAD_TOO_LARGE, which the client has no special handling for, maps to Unavailable`() {
        assertEquals(AppError.Unavailable, errorFromBody(body("PAYLOAD_TOO_LARGE"), json))
    }

    // ---- (c) Malformed or non-JSON bodies must not crash ----

    @Test
    fun `a non-JSON error body (such as a proxy's HTML page) maps to Unavailable`() {
        assertEquals(AppError.Unavailable, errorFromBody("<html><body>502 Bad Gateway</body></html>", json))
    }

    @Test
    fun `JSON in the wrong shape maps to Unavailable`() {
        assertEquals(AppError.Unavailable, errorFromBody("""{"message":"no error envelope"}""", json))
    }

    @Test
    fun `truncated JSON maps to Unavailable`() {
        assertEquals(AppError.Unavailable, errorFromBody("""{"error":{"code":"NOT_FO""", json))
    }

    @Test
    fun `an empty or missing body maps to Unavailable`() {
        assertEquals(AppError.Unavailable, errorFromBody("", json))
        assertEquals(AppError.Unavailable, errorFromBody(null, json))
    }

    // ---- Exceptions from a Retrofit call ----

    @Test
    fun `an HttpException is mapped through its error body`() {
        assertEquals(AppError.NotFound, httpError(404, body("NOT_FOUND")).toAppError(json))
        assertEquals(AppError.Unavailable, httpError(502, body("UPSTREAM_UNAVAILABLE")).toAppError(json))
    }

    @Test
    fun `network failures and timeouts (IOException) map to Unavailable`() {
        assertEquals(AppError.Unavailable, IOException("no route to host").toAppError(json))
        assertEquals(AppError.Unavailable, SocketTimeoutException("timeout").toAppError(json))
    }

    @Test
    fun `a 2xx body that does not match our DTOs maps to Unavailable`() {
        assertEquals(AppError.Unavailable, SerializationException("bad body").toAppError(json))
    }

    @Test(expected = IllegalStateException::class)
    fun `any other exception is a bug in the app and is rethrown, not hidden`() {
        IllegalStateException("bug").toAppError(json)
    }
}
