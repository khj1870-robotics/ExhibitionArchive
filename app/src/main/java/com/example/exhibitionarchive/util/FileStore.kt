package com.example.exhibitionarchive.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStore @Inject constructor(@ApplicationContext private val context: Context) {
    fun copyImage(uri: Uri): String {
        val file = newImageFile()
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "이미지를 열 수 없습니다." }
            file.outputStream().use { input.copyTo(it) }
        }
        return file.absolutePath
    }

    suspend fun downloadImage(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = Jsoup.connect(url).ignoreContentType(true).timeout(15_000).execute().bodyAsBytes()
            val file = newImageFile()
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    fun newAudioFile(): File {
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.m4a")
    }

    private fun newImageFile(): File {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.jpg")
    }
}
