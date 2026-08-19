package com.localaisearch.data.image

import android.content.Context
import java.io.File

/** Owns only LocalAISearch-generated processed image files. */
class ImageCacheManager(private val context: Context) {
    private val cacheDir: File get() = context.cacheDir

    fun listFiles(): List<File> = cacheDir.listFiles { file ->
        file.isFile && file.name.startsWith("processed_image_") && file.extension.equals("jpg", true)
    }?.toList().orEmpty()

    fun sizeBytes(): Long = listFiles().sumOf { it.length() }

    fun clear(): Long {
        var deleted = 0L
        listFiles().forEach { file ->
            val size = file.length()
            if (file.delete()) deleted += size
        }
        return deleted
    }
}
