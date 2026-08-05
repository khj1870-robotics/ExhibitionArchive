package com.example.exhibitionarchive.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val FONT_SCALE_KEY = floatPreferencesKey("font_scale")
private val GRID_COLUMNS_KEY = intPreferencesKey("grid_columns")

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val context: Context) {
    val fontScale: Flow<Float> = context.settingsDataStore.data.map { it[FONT_SCALE_KEY] ?: 1f }
    val gridColumns: Flow<Int> = context.settingsDataStore.data.map { it[GRID_COLUMNS_KEY] ?: 2 }

    suspend fun setFontScale(scale: Float) {
        context.settingsDataStore.edit { it[FONT_SCALE_KEY] = scale }
    }

    suspend fun setGridColumns(columns: Int) {
        context.settingsDataStore.edit { it[GRID_COLUMNS_KEY] = columns }
    }
}
