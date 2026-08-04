package com.example.exhibitionarchive.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val FONT_SCALE_KEY = floatPreferencesKey("font_scale")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    val fontScale: Flow<Float> = context.settingsDataStore.data.map { it[FONT_SCALE_KEY] ?: 1f }

    suspend fun setFontScale(scale: Float) {
        context.settingsDataStore.edit { it[FONT_SCALE_KEY] = scale }
    }
}
