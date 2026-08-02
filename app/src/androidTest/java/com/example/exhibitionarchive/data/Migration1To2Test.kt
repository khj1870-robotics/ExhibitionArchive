package com.example.exhibitionarchive.data

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @After
    fun cleanUp() {
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate1To2_preservesArtistAndBuildsNormalizedName() {
        helper.createDatabase(TEST_DB, 1).apply {
            insertV1Artist(this)
            close()
        }

        val db = Room.databaseBuilder(context, AppDatabase::class.java, TEST_DB)
            .addMigrations(DatabaseModule.MIGRATION_1_2)
            .build()
        val sqlite = db.openHelper.writableDatabase
        sqlite.query("SELECT name, normalizedName FROM artists WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("  Nam June PAIK  ", cursor.getString(0))
            assertEquals("nam june paik", cursor.getString(1))
        }
        db.close()
    }

    private fun insertV1Artist(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO artists (id, name, originalName, birthYear, deathYear, nationality, biography, officialUrl, createdAt, updatedAt) VALUES (1, '  Nam June PAIK  ', NULL, NULL, NULL, NULL, NULL, NULL, 1, 1)"
        )
    }

    companion object { private const val TEST_DB = "migration-test" }
}
