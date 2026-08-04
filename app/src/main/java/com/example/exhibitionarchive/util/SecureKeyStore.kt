package com.example.exhibitionarchive.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureKeyStore @Inject constructor(@ApplicationContext context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getNaverKeys(): Pair<String, String>? {
        val clientId = prefs.getString(KEY_CLIENT_ID, null)
        val clientSecret = prefs.getString(KEY_CLIENT_SECRET, null)
        return if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) null else clientId to clientSecret
    }

    fun saveNaverKeys(clientId: String, clientSecret: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId).putString(KEY_CLIENT_SECRET, clientSecret).apply()
    }

    companion object {
        private const val PREFS_NAME = "secure_api_keys"
        private const val KEY_CLIENT_ID = "naver_client_id"
        private const val KEY_CLIENT_SECRET = "naver_client_secret"
    }
}
