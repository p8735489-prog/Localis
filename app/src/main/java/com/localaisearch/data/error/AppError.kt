package com.localaisearch.data.error

sealed class AppError(val code: String, val message: String) {
    data class NetworkError(val detail: String) : AppError("NET_001", "Network error: $detail")
    data class ModelError(val detail: String) : AppError("MODEL_001", "Model error: $detail")
    data class JniError(val detail: String) : AppError("JNI_001", "Native engine error: $detail")
    data class StorageError(val detail: String) : AppError("STORAGE_001", "Storage error: $detail")
    data class PermissionError(val detail: String) : AppError("PERM_001", "Permission denied: $detail")
    data class MemoryError(val detail: String) : AppError("MEM_001", "Memory system error: $detail")
    data class UnknownError(val throwable: Throwable) : AppError("UNKNOWN", "Unexpected error: ${throwable.message}")
}
