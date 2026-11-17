package com.example.myproducts.ui.enquiry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myproducts.R
import com.example.myproducts.ui.common.UiState
import com.example.myproducts.ui.theme.AppTheme
import kotlinx.serialization.Serializable

/**
 * Navigation route for the enquiry form. `productTitle` is for display only ("Enquiry
 * about ..."); the server looks the product up by id and never trusts client product fields.
 */
@Serializable
data class EnquiryRoute(val productId: String, val productTitle: String) {
    companion object {
        /** Must equal the property names above: Navigation stores the arguments under them. */
        const val ARG_PRODUCT_ID = "productId"
        const val ARG_PRODUCT_TITLE = "productTitle"
    }
}

@Composable
fun EnquiryRoute(onBack: () -> Unit, viewModel: EnquiryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    EnquiryScreen(
        state = state,
        onFieldChange = viewModel::onFieldChange,
        onSubmit = viewModel::submit,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnquiryScreen(
    state: EnquiryUiState,
    onFieldChange: (EnquiryField, String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.enquiry_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) } },
            )
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        // On success the thank-you state REPLACES the form; there is no way back into it.
        if (state.submission is UiState.Success) {
            ThankYou(email = state.form.email.trim(), onBack = onBack, modifier = modifier)
        } else {
            EnquiryFormContent(state, onFieldChange, onSubmit, modifier)
        }
    }
}

@Composable
private fun EnquiryFormContent(
    state: EnquiryUiState,
    onFieldChange: (EnquiryField, String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sending = state.submission is UiState.Loading
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(AppTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m),
    ) {
        if (state.productTitle.isNotBlank()) {
            Text(
                text = stringResource(R.string.enquiry_about, state.productTitle),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        // Submission errors (e.g. EMAIL_FAILED) appear here, above the form, which stays put.
        (state.submission as? UiState.Error)?.let { error ->
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }

        FormField(state, EnquiryField.Name, R.string.enquiry_name, KeyboardType.Text, KeyboardCapitalization.Words, onFieldChange, !sending)
        FormField(state, EnquiryField.Email, R.string.enquiry_email, KeyboardType.Email, KeyboardCapitalization.None, onFieldChange, !sending)
        FormField(state, EnquiryField.Phone, R.string.enquiry_phone, KeyboardType.Phone, KeyboardCapitalization.None, onFieldChange, !sending)
        FormField(
            state, EnquiryField.Message, R.string.enquiry_message, KeyboardType.Text,
            KeyboardCapitalization.Sentences, onFieldChange, !sending, singleLine = false,
        )

        Button(
            onClick = onSubmit,
            // Disabled while sending, so a second tap cannot send a second email.
            enabled = state.canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppTheme.sizes.minTouchTarget),
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AppTheme.sizes.buttonProgress),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.enquiry_submit))
            }
        }
    }
}

/** One text field, with its error (from client or server validation) shown underneath it. */
@Composable
private fun FormField(
    state: EnquiryUiState,
    field: EnquiryField,
    labelRes: Int,
    keyboardType: KeyboardType,
    capitalization: KeyboardCapitalization,
    onFieldChange: (EnquiryField, String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = true,
) {
    val value = when (field) {
        EnquiryField.Name -> state.form.name
        EnquiryField.Email -> state.form.email
        EnquiryField.Phone -> state.form.phone
        EnquiryField.Message -> state.form.message
    }
    val error = state.fieldErrors[field]
    OutlinedTextField(
        value = value,
        onValueChange = { onFieldChange(field, it) },
        label = { Text(stringResource(labelRes)) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        enabled = enabled,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 4,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = capitalization,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ThankYou(email: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.l, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(R.string.enquiry_thanks_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            text = stringResource(R.string.enquiry_thanks_body, email),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onBack) { Text(stringResource(R.string.enquiry_back_to_product)) }
    }
}
