package com.localaisearch.data.error

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object GlobalErrorHandler {
    private val _errors = MutableSharedFlow<AppError>(extraBufferCapacity = 10)
    val errors: SharedFlow<AppError> = _errors.asSharedFlow()

    fun emit(error: AppError) {
        _errors.tryEmit(error)
    }

    fun emitNetwork(detail: String) = emit(AppError.NetworkError(detail))
    fun emitModel(detail: String) = emit(AppError.ModelError(detail))
    fun emitJni(detail: String) = emit(AppError.JniError(detail))
    fun emitStorage(detail: String) = emit(AppError.StorageError(detail))
}
