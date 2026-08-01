package com.example.exhibitionarchive.util

import android.content.Context
import android.net.Uri
import com.example.exhibitionarchive.data.AppRepository
import com.example.exhibitionarchive.data.BackupPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: AppRepository
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(uri: Uri) {
        val payload = repository.backupPayload()
        context.contentResolver.openOutputStream(uri).use { raw ->
            requireNotNull(raw)
            ZipOutputStream(raw).use { zip ->
                zip.putNextEntry(ZipEntry("data.json"))
                zip.write(json.encodeToString(payload).encodeToByteArray())
                zip.closeEntry()
            }
        }
    }

    suspend fun importReplace(uri: Uri) {
        var data: String? = null
        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw)
            ZipInputStream(raw).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "data.json") data = zip.readBytes().decodeToString()
                    entry = zip.nextEntry
                }
            }
        }
        val payload = json.decodeFromString<BackupPayload>(requireNotNull(data) { "data.json이 없습니다." })
        repository.replaceFromBackup(payload)
    }
}
