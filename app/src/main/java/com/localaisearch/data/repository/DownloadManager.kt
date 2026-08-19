package com.localaisearch.data.repository

import android.content.Context
import android.util.Log
import com.localaisearch.data.model.GGUFModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.appendingSink
import okio.buffer
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.Semaphore

import com.localaisearch.data.repository.NetworkClientFactory
/**
 * Download state for a single model file.
 */
sealed class DownloadState {
    data class Idle(val modelId: String) : DownloadState()
    data class Queued(val modelId: String) : DownloadState()
    data class Downloading(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val bytesPerSecond: Long = 0,
        val progress: Float = 0f
    ) : DownloadState()
    data class Paused(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()
    data class Completed(val modelId: String, val filePath: String) : DownloadState()
    data class Error(val modelId: String, val message: String) : DownloadState()
    data class Cancelled(val modelId: String) : DownloadState()
}

/**
 * Download manager with resume support.
 *
 * Features:
 * - Pause / Resume / Cancel
 * - Background download (coroutine-based, non-blocking UI)
 * - Progress tracking (speed, remaining time, bytes)
 * - Concurrent download limiting
 * - File integrity check after completion
 */
class DownloadManager(
    private val context: Context,
    private val maxConcurrent: Int = 3,
    private val onComplete: (GGUFModel) -> Unit = {}
) {
    companion object {
        private const val TAG = "DownloadManager"
        private const val CHUNK_SIZE = 32768
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = NetworkClientFactory.builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _downloadStates = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, DownloadState>> = _downloadStates.asStateFlow()

    // Active download coroutines
    private val activeDownloads = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val downloadGuard = Any()
    private val downloadQueue = ArrayDeque<String>()
    private val pausedDownloads = mutableSetOf<String>()
    private val cancelledDownloads = mutableSetOf<String>()

    // Concurrency control. Queued downloads wait for a permit instead of being
    // silently dropped when all slots are occupied.
    private val concurrency = Semaphore(maxConcurrent, true)

    /**
     * Start downloading a GGUF model file.
     *
     * @param modelId Unique identifier for tracking
     * @param downloadUrl Direct URL to the .gguf file
     * @param fileName Target file name (must end with .gguf)
     * @param totalSize Expected total size in bytes (optional, for progress calculation)
     * @param downloadSource Source name for display (e.g. "Hugging Face", "Tsinghua Mirror")
     */
    fun startDownload(
        modelId: String,
        downloadUrl: String,
        fileName: String,
        totalSize: Long = 0,
        downloadSource: String = ""
    ) {
        if (!fileName.endsWith(".gguf", ignoreCase = true)) {
            updateState(modelId, DownloadState.Error(modelId, "Only .gguf files are supported"))
            return
        }

        synchronized(downloadGuard) {
            // Reserve the model ID before launching the coroutine so two rapid taps
            // cannot create duplicate download jobs.
            if (activeDownloads.containsKey(modelId)) return
            cancelledDownloads.remove(modelId)
            pausedDownloads.remove(modelId)
            updateState(modelId, DownloadState.Queued(modelId))
            processQueue(modelId, downloadUrl, fileName, totalSize, downloadSource)
        }
    }

    /**
     * Pause a downloading file.
     */
    fun pauseDownload(modelId: String) {
        activeDownloads[modelId]?.cancel()
        pausedDownloads.add(modelId)
        val current = _downloadStates.value[modelId]
        if (current is DownloadState.Downloading) {
            updateState(modelId, DownloadState.Paused(modelId, current.bytesDownloaded, current.totalBytes))
        }
    }

    /**
     * Resume a paused download.
     */
    fun resumeDownload(
        modelId: String,
        downloadUrl: String,
        fileName: String,
        totalSize: Long = 0,
        downloadSource: String = ""
    ) {
        pausedDownloads.remove(modelId)
        cancelledDownloads.remove(modelId)
        val current = _downloadStates.value[modelId]
        val existingFile = File(context.filesDir, "models/$fileName")
        val resumeFrom = if (current is DownloadState.Paused) current.bytesDownloaded else existingFile.length()

        updateState(modelId, DownloadState.Queued(modelId))
        processQueue(modelId, downloadUrl, fileName, totalSize, downloadSource, resumeFrom)
    }

    /**
     * Cancel and delete a download.
     */
    fun cancelDownload(modelId: String, fileName: String) {
        activeDownloads[modelId]?.cancel()
        cancelledDownloads.add(modelId)
        pausedDownloads.remove(modelId)
        activeDownloads.remove(modelId)
        updateState(modelId, DownloadState.Cancelled(modelId))

        // Delete partial file
        val file = File(context.filesDir, "models/$fileName")
        if (file.exists()) {
            file.delete()
        }
    }

    /**
     * Check if a download is active.
     */
    fun isDownloading(modelId: String): Boolean {
        return activeDownloads.containsKey(modelId)
    }

    fun isPaused(modelId: String): Boolean {
        return pausedDownloads.contains(modelId)
    }

    private fun processQueue(
        modelId: String,
        downloadUrl: String,
        fileName: String,
        totalSize: Long,
        downloadSource: String,
        resumeFrom: Long = 0
    ) {
        val job = scope.launch {
            var permitAcquired = false
            try {
                concurrency.acquire()
                permitAcquired = true
                val modelsDir = File(context.filesDir, "models").apply { mkdirs() }
                val targetFile = File(modelsDir, fileName)

                // Never assume an existing .gguf is complete. Only skip the
                // network request when its size matches the expected size and the
                // GGUF magic header is valid.
                if (targetFile.exists() && targetFile.length() > 0 && resumeFrom == 0L) {
                    val expected = totalSize
                    val looksComplete = expected > 0L &&
                        targetFile.length() == expected &&
                        isValidGguf(targetFile)
                    if (looksComplete) {
                        updateState(modelId, DownloadState.Completed(modelId, targetFile.absolutePath))
                        onComplete(
                            GGUFModel(
                                id = targetFile.absolutePath,
                                name = targetFile.nameWithoutExtension,
                                filePath = targetFile.absolutePath,
                                fileSizeBytes = targetFile.length()
                            )
                        )
                        return@launch
                    }
                    if (expected > 0L && targetFile.length() > expected) {
                        targetFile.delete()
                    }
                }

                val requestBuilder = Request.Builder().url(downloadUrl)
                    .header("User-Agent", "Localis/1.0")

                // Resume support: request partial content
                val resumePosition = if (resumeFrom > 0 && targetFile.exists()) {
                    resumeFrom
                } else {
                    0L
                }

                if (resumePosition > 0) {
                    requestBuilder.header("Range", "bytes=$resumePosition-")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                // A mirror/CDN is allowed to ignore Range and return 200. In that
                // case appending to the partial file would corrupt the GGUF.
                val effectiveResume = if (resumePosition > 0 && response.code != 206) {
                    if (response.code != 200) {
                        response.close()
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    targetFile.delete()
                    0L
                } else {
                    if (!response.isSuccessful && response.code != 206) {
                        response.close()
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    resumePosition
                }

                val contentType = response.header("Content-Type")?.lowercase().orEmpty()
                if (contentType.contains("text/html") || contentType.contains("text/plain")) {
                    response.close()
                    throw IllegalStateException("Mirror returned a text/HTML response instead of a GGUF file")
                }

                val contentLength = response.header("Content-Length")?.toLongOrNull()
                    ?: totalSize
                val totalBytes = if (response.code == 206 && contentLength > 0) {
                    effectiveResume + contentLength
                } else {
                    contentLength
                }

                val body = response.body ?: run {
                    response.close()
                    throw IllegalStateException("Empty response body")
                }
                val inputStream = body.byteStream()

                val startTime = System.currentTimeMillis()
                var bytesRead = effectiveResume
                var lastUpdate = startTime
                var lastBytes = effectiveResume
                val buffer = ByteArray(CHUNK_SIZE)

                // Append mode for resume support
                val raf = RandomAccessFile(targetFile, "rws")
                if (effectiveResume > 0) {
                    raf.seek(effectiveResume)
                } else {
                    raf.setLength(0L)
                }

                try {
                    while (true) {
                        if (cancelledDownloads.contains(modelId)) {
                            throw kotlinx.coroutines.CancellationException("Cancelled")
                        }
                        if (pausedDownloads.contains(modelId)) {
                            break
                        }

                        val read = inputStream.read(buffer)
                        if (read == -1) break

                        raf.write(buffer, 0, read)
                        bytesRead += read

                        // Update progress every 500ms
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 500) {
                            val elapsed = (now - lastUpdate) / 1000f
                            val bytesPerSecond = ((bytesRead - lastBytes) / elapsed).toLong()
                            val progress = if (totalBytes > 0) bytesRead.toFloat() / totalBytes else 0f
                            updateState(
                                modelId,
                                DownloadState.Downloading(
                                    modelId = modelId,
                                    bytesDownloaded = bytesRead,
                                    totalBytes = totalBytes,
                                    bytesPerSecond = bytesPerSecond,
                                    progress = progress.coerceIn(0f, 1f)
                                )
                            )
                            lastUpdate = now
                            lastBytes = bytesRead
                        }
                    }
                } finally {
                    raf.close()
                    inputStream.close()
                }

                // Check if paused
                if (pausedDownloads.contains(modelId)) {
                    updateState(modelId, DownloadState.Paused(modelId, bytesRead, totalBytes))
                    return@launch
                }

                // Check if cancelled
                if (cancelledDownloads.contains(modelId)) {
                    updateState(modelId, DownloadState.Cancelled(modelId))
                    return@launch
                }

                // Verify exact size when known and validate the GGUF magic header.
                val finalSize = targetFile.length()
                if (totalBytes > 0 && finalSize != totalBytes) {
                    throw IllegalStateException(
                        "File incomplete: $finalSize / $totalBytes bytes"
                    )
                }
                if (!isValidGguf(targetFile)) {
                    throw IllegalStateException(
                        "Downloaded file is not a valid GGUF file. The mirror may have returned an HTML/error page."
                    )
                }

                updateState(modelId, DownloadState.Completed(modelId, targetFile.absolutePath))

                // Notify completion
                onComplete(
                    GGUFModel(
                        id = targetFile.absolutePath,
                        name = targetFile.nameWithoutExtension,
                        filePath = targetFile.absolutePath,
                        fileSizeBytes = finalSize
                    )
                )

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    if (pausedDownloads.contains(modelId)) {
                        val current = _downloadStates.value[modelId]
                        if (current is DownloadState.Downloading) {
                            updateState(modelId, DownloadState.Paused(modelId, current.bytesDownloaded, current.totalBytes))
                        }
                    } else {
                        updateState(modelId, DownloadState.Cancelled(modelId))
                    }
                } else {
                    Log.e(TAG, "Download failed: $modelId", e)
                    updateState(modelId, DownloadState.Error(modelId, e.message ?: "Download failed"))
                }
            } finally {
                activeDownloads.remove(modelId)
                if (permitAcquired) {
                    concurrency.release()
                }
                // Waiting queued coroutines automatically acquire the next slot.
            }
        }

        activeDownloads[modelId] = job
    }

    private fun isValidGguf(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return try {
            file.inputStream().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 &&
                    header[0] == 0x47.toByte() &&
                    header[1] == 0x47.toByte() &&
                    header[2] == 0x55.toByte() &&
                    header[3] == 0x46.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun updateState(modelId: String, state: DownloadState) {
        _downloadStates.value = _downloadStates.value.toMutableMap().apply {
            put(modelId, state)
        }
    }

    fun release() {
        activeDownloads.values.forEach { it.cancel() }
        scope.cancel()
    }
}
