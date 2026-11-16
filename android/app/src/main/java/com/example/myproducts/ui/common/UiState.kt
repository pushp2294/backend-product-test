package com.example.myproducts.ui.common

import com.example.myproducts.domain.AppError

/**
 * What every screen renders. Each ViewModel exposes exactly one StateFlow<UiState<T>>,
 * and each screen draws all three cases: loading and error are part of the product,
 * not polish.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    /**
     * @param retry null when retrying cannot help (e.g. the product no longer exists),
     *   so the screen shows no "Try again" button.
     */
    data class Error(val message: String, val retry: (() -> Unit)?) : UiState<Nothing>
}

/** Turns a repository error into the screen's error state, offering retry only when it can help. */
fun AppError.toErrorState(
    retry: () -> Unit,
    notFound: NotFoundSubject = NotFoundSubject.Product,
): UiState.Error =
    UiState.Error(
        message = toUserMessage(notFound),
        retry = if (this == AppError.Unavailable) retry else null,
    )
