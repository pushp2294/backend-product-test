package com.example.myproducts.ui.enquiry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myproducts.domain.AppError
import com.example.myproducts.domain.Enquiry
import com.example.myproducts.domain.ProductRepository
import com.example.myproducts.domain.RepoResult
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.common.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The enquiry screen's single state. Like search, it holds two things with different
 * lifecycles: the form being edited, and the result of the last submission.
 *
 * @property submission null = not sent yet; Loading = in flight; Success = the email was
 *   sent (the thank-you state REPLACES the form); Error = a message shown above the form,
 *   which stays on screen with everything the customer typed.
 */
data class EnquiryUiState(
    val productTitle: String,
    val form: EnquiryForm = EnquiryForm(),
    val fieldErrors: Map<EnquiryField, String> = emptyMap(),
    val submission: UiState<Unit>? = null,
) {
    /** False while sending (no double submission) and after success (nothing left to send). */
    val canSubmit: Boolean
        get() = submission !is UiState.Loading && submission !is UiState.Success
}

@HiltViewModel
class EnquiryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProductRepository,
) : ViewModel() {

    // Attached automatically from the navigation route; the customer never types it.
    private val productId: String = checkNotNull(savedStateHandle[EnquiryRoute.ARG_PRODUCT_ID]) {
        "EnquiryViewModel opened without a productId"
    }

    private val _state = MutableStateFlow(
        EnquiryUiState(productTitle = savedStateHandle[EnquiryRoute.ARG_PRODUCT_TITLE] ?: ""),
    )
    val state: StateFlow<EnquiryUiState> = _state.asStateFlow()

    /** Updates one field, and clears that field's error so it disappears as the customer fixes it. */
    fun onFieldChange(field: EnquiryField, value: String) {
        _state.update { state ->
            val form = when (field) {
                EnquiryField.Name -> state.form.copy(name = value)
                EnquiryField.Email -> state.form.copy(email = value)
                EnquiryField.Phone -> state.form.copy(phone = value)
                EnquiryField.Message -> state.form.copy(message = value)
            }
            state.copy(form = form, fieldErrors = state.fieldErrors - field)
        }
    }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        // Invalid input is caught here, before any request is made.
        val errors = validateEnquiry(current.form)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(fieldErrors = errors, submission = null) }
            return
        }

        _state.update { it.copy(fieldErrors = emptyMap(), submission = UiState.Loading) }
        viewModelScope.launch {
            val form = current.form
            val result = repository.submitEnquiry(
                Enquiry(
                    productId = productId,
                    name = form.name.trim(),
                    email = form.email.trim(),
                    phone = form.phone.trim().ifEmpty { null },
                    message = form.message.trim().ifEmpty { null },
                ),
            )
            _state.update { state ->
                when (result) {
                    is RepoResult.Success -> state.copy(submission = UiState.Success(Unit))
                    is RepoResult.Failure -> state.withFailure(result.error)
                }
            }
        }
    }

    /**
     * Every failure keeps the customer on the form with their input intact. The Submit
     * button is the retry, so no separate retry action is offered.
     * EMAIL_FAILED in particular must never show "Thank you": that would tell the customer
     * their enquiry was sent when it was not.
     */
    private fun EnquiryUiState.withFailure(error: AppError): EnquiryUiState {
        val serverFieldErrors = if (error is AppError.Validation) {
            error.fieldErrors.mapNotNull { (name, message) -> EnquiryField.fromServerName(name)?.let { it to message } }.toMap()
        } else {
            emptyMap()
        }
        return copy(
            fieldErrors = serverFieldErrors,
            submission = UiState.Error(message = error.toUserMessage(), retry = null),
        )
    }
}
