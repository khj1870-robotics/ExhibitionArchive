package com.example.exhibitionarchive.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ExhibitionEntity::class, VisitEntity::class, ArtistEntity::class, ArtworkEntity::class,
        ArtworkImageEntity::class, AudioRecordEntity::class, TagEntity::class,
        ExhibitionTagCrossRef::class, ArtworkTagCrossRef::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exhibitionDao(): ExhibitionDao
    abstract fun visitDao(): VisitDao
    abstract fun artistDao(): ArtistDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun mediaDao(): MediaDao
    abstract fun tagDao(): TagDao
}
