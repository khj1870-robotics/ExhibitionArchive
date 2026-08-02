package com.example.exhibitionarchive.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE artists ADD COLUMN normalizedName TEXT NOT NULL DEFAULT ''")
            db.execSQL("UPDATE artists SET normalizedName = lower(trim(name))")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_artists_normalizedName ON artists(normalizedName)")
        }
    }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "exhibition_archive.db")
            .addMigrations(MIGRATION_1_2)
            .build()
}
