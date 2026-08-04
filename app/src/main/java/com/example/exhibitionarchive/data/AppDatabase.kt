package com.example.exhibitionarchive.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `artworks` ADD COLUMN `sourceUrl` TEXT")
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `visit_notes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `exhibitionId` INTEGER NOT NULL,
                `photoPath` TEXT,
                `text` TEXT,
                `createdAt` INTEGER NOT NULL,
                FOREIGN KEY(`exhibitionId`) REFERENCES `exhibitions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_notes_exhibitionId` ON `visit_notes` (`exhibitionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_visit_notes_createdAt` ON `visit_notes` (`createdAt`)")
    }
}

@Database(
    entities = [ExhibitionEntity::class, VisitEntity::class, ArtistEntity::class, ArtworkEntity::class,
        ArtworkImageEntity::class, AudioRecordEntity::class, TagEntity::class,
        ExhibitionTagCrossRef::class, ArtworkTagCrossRef::class, VisitNoteEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exhibitionDao(): ExhibitionDao
    abstract fun visitDao(): VisitDao
    abstract fun artistDao(): ArtistDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun mediaDao(): MediaDao
    abstract fun visitNoteDao(): VisitNoteDao
    abstract fun tagDao(): TagDao
}
