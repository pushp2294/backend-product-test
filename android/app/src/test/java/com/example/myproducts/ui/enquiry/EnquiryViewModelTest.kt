package com.example.myproducts.ui.enquiry

import androidx.lifecycle.SavedStateHandle
import com.example.myproducts.domain.AppError
import com.example.myproducts.domain.Enquiry
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.testing.FakeProductRepository
import com.example.myproducts.testing.MainDispatcherRule
import com.example.myproducts.ui.common.UiState
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnquiryViewModelTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeProductRepository()
    private val viewModel by lazy {
        EnquiryViewModel(
            SavedStateHandle(mapOf(EnquiryRoute.ARG_PRODUCT_ID to "12", EnquiryRoute.ARG_PRODUCT_TITLE to "WD 4TB Gaming Drive")),
            repository,
        )
    }

    private fun fillValidForm() {
        viewModel.onFieldChange(EnquiryField.Name, "  Ada Lovelace ")
        viewModel.onFieldChange(EnquiryField.Email, " ada@example.com ")
        viewModel.onFieldChange(EnquiryField.Phone, "")
        viewModel.onFieldChange(EnquiryField.Message, "Is this in stock?")
    }

    // ---- Client-side validation ----

    @Test
    fun `an invalid email is rejected before any repository call`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()
        viewModel.onFieldChange(EnquiryField.Email, "not-an-email")

        viewModel.submit()
        advanceUntilIdle()

        assertEquals(emptyList<Enquiry>(), repository.submittedEnquiries)
        assertEquals("Enter a valid email address", viewModel.state.value.fieldErrors[EnquiryField.Email])
        assertNull(viewModel.state.value.submission)
    }

    @Test
    fun `an empty form shows errors on name and email at once, and sends nothing`() = runTest(mainDispatcher.dispatcher) {
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(setOf(EnquiryField.Name, EnquiryField.Email), viewModel.state.value.fieldErrors.keys)
        assertTrue(repository.submittedEnquiries.isEmpty())
    }

    @Test
    fun `editing a field clears that field's error`() = runTest(mainDispatcher.dispatcher) {
        viewModel.submit()
        viewModel.onFieldChange(EnquiryField.Name, "Ada")

        assertFalse(EnquiryField.Name in viewModel.state.value.fieldErrors)
        assertTrue(EnquiryField.Email in viewModel.state.value.fieldErrors)
    }

    // ---- Success ----

    @Test
    fun `a successful submit reaches the thank-you state`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()

        viewModel.submit()
        assertEquals(UiState.Loading, viewModel.state.value.submission)
        advanceUntilIdle()

        assertEquals(UiState.Success(Unit), viewModel.state.value.submission)
        assertFalse("nothing left to send after success", viewModel.state.value.canSubmit)
    }

    @Test
    fun `productId comes from the route, values are trimmed, blank optionals are sent as absent`() =
        runTest(mainDispatcher.dispatcher) {
            fillValidForm()

            viewModel.submit()
            advanceUntilIdle()

            assertEquals(
                Enquiry(productId = "12", name = "Ada Lovelace", email = "ada@example.com", phone = null, message = "Is this in stock?"),
                repository.submittedEnquiries.single(),
            )
        }

    @Test
    fun `submit is disabled while in flight, so a second tap sends nothing`() = runTest(mainDispatcher.dispatcher) {
        fillValidForm()

        viewModel.submit()
        assertFalse(viewModel.state.value.canSubmit)
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(1, repository.submittedEnquiries.size)
    }

    // ---- Failures: the customer stays on the form ----

    @Test
    fun `EMAIL_FAILED shows the error, stays on the form with input intact, and NEVER reaches thank-you`() =
        runTest(mainDispatcher.dispatcher) {
            repository.enquiryResult = RepoResult.Failure(AppError.EmailFailed)
            fillValidForm()

            viewModel.submit()
            advanceUntilIdle()

            val state = viewModel.state.value
            assertFalse("thank-you must not appear when the email was not sent", state.submission is UiState.Success)
            assertEquals("We couldn't send your enquiry. Please try again.", (state.submission as UiState.Error).message)
            assertEquals("  Ada Lovelace ", state.form.name)
            assertEquals("Is this in stock?", state.form.message)
            assertTrue("the customer can try again", state.canSubmit)
        }

    @Test
    fun `after EMAIL_FAILED, trying again can succeed`() = runTest(mainDispatcher.dispatcher) {
        repository.enquiryResult = RepoResult.Failure(AppError.EmailFailed)
        fillValidForm()
        viewModel.submit()
        advanceUntilIdle()

        repository.enquiryResult = RepoResult.Success(Unit)
        viewModel.submit()
        advanceUntilIdle()

        assertEquals(UiState.Success(Unit), viewModel.state.value.submission)
        assertEquals(2, repository.submittedEnquiries.size)
    }

    @Test
    fun `server VALIDATION_ERROR details appear against the right fields`() = runTest(mainDispatcher.dispatcher) {
        repository.enquiryResult = RepoResult.Failure(
            AppError.Validation(mapOf("email" to "Enter a valid email address", "productId" to "Required")),
        )
        fillValidForm()

        viewModel.submit()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(mapOf(EnquiryField.Email to "Enter a valid email address"), state.fieldErrors)
        assertEquals("Please check the highlighted fields.", (state.submission as UiState.Error).message)
    }

    @Test
    fun `an outage keeps the form, with the generic message`() = runTest(mainDispatcher.dispatcher) {
        repository.enquiryResult = RepoResult.Failure(AppError.Unavailable)
        fillValidForm()

        viewModel.submit()
        advanceUntilIdle()

        assertEquals("Something went wrong.", (viewModel.state.value.submission as UiState.Error).message)
        assertTrue(viewModel.state.value.canSubmit)
    }
}
