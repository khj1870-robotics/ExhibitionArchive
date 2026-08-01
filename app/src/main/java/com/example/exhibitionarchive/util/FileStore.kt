package com.example.exhibitionarchive.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileStore @Inject constructor(@ApplicationContext private val context: Context) {
    fun copyImage(uri: Uri): String {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "이미지를 열 수 없습니다." }
            file.outputStream().use { input.copyTo(it) }
        }
        return file.absolutePath
    }

    fun newAudioFile(): File {
        val dir = File(context.filesDir, "audio").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.m4a")
    }
}
