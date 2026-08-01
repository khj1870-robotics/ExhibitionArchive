package com.example.exhibitionarchive.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(private val db: AppDatabase) {
    val exhibitions = db.exhibitionDao().observeAll()
    val visits = db.visitDao().observeAll()
    val artists = db.artistDao().observeAll()
    val tags = db.tagDao().observeAll()

    fun exhibition(id: Long) = db.exhibitionDao().observe(id)
    fun visitsForExhibition(id: Long) = db.visitDao().observeForExhibition(id)
    fun artworksForExhibition(id: Long) = db.artworkDao().observeForExhibition(id)
    fun artwork(id: Long) = db.artworkDao().observeCard(id)
    fun audioForExhibition(id: Long) = db.mediaDao().observeAudioForExhibition(id)
    fun tagsForExhibition(id: Long) = db.tagDao().observeForExhibition(id)

    suspend fun createExhibition(
        title: String,
        visitDate: String,
        posterPath: String?,
        venue: String?,
        oneLine: String?,
        detail: String?,
        tagNames: List<String>
    ): Long = db.withTransaction {
        val exhibitionId = db.exhibitionDao().insert(ExhibitionEntity(title = title.trim(), posterPath = posterPath, venueName = venue?.trim()?.ifBlank { null }))
        db.visitDao().insert(VisitEntity(exhibitionId = exhibitionId, visitedAt = visitDate, oneLineReview = oneLine?.trim()?.ifBlank { null }, detailedReview = detail?.trim()?.ifBlank { null }))
        db.tagDao().setExhibitionTags(exhibitionId, tagNames)
        exhibitionId
    }

    suspend fun addArtwork(exhibitionId: Long, title: String, artistName: String?, review: String?, imagePath: String?): Long = db.withTransaction {
        val artistId = artistName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            db.artistDao().insert(ArtistEntity(name = name))
        }
        val id = db.artworkDao().insert(ArtworkEntity(exhibitionId = exhibitionId, artistId = artistId, title = title.trim(), personalReview = review?.trim()?.ifBlank { null }))
        imagePath?.let { db.mediaDao().insertImage(ArtworkImageEntity(artworkId = id, localPath = it, sourceType = "GALLERY", isRepresentative = true)) }
        id
    }

    suspend fun addAudio(item: AudioRecordEntity) = db.mediaDao().insertAudio(item)
    suspend fun deleteExhibition(item: ExhibitionEntity) = db.exhibitionDao().delete(item)
    fun searchExhibitions(q: String): Flow<List<ExhibitionEntity>> = db.exhibitionDao().search(q)
    fun searchArtworks(q: String): Flow<List<ArtworkEntity>> = db.artworkDao().search(q)
    fun searchArtists(q: String): Flow<List<ArtistEntity>> = db.artistDao().search(q)

    suspend fun backupPayload() = BackupPayload(
        exhibitions = db.exhibitionDao().allNow(), visits = db.visitDao().allNow(), artists = db.artistDao().allNow(),
        artworks = db.artworkDao().allNow(), images = db.mediaDao().allImages(), audio = db.mediaDao().allAudio(),
        tags = db.tagDao().allNow(), exhibitionTags = db.tagDao().allExhibitionRefs(), artworkTags = db.tagDao().allArtworkRefs()
    )

    suspend fun replaceFromBackup(payload: BackupPayload) = db.withTransaction {
        db.clearAllTables()
        db.exhibitionDao().insertAll(payload.exhibitions)
        db.artistDao().insertAll(payload.artists)
        db.visitDao().insertAll(payload.visits)
        db.artworkDao().insertAll(payload.artworks)
        db.mediaDao().insertImages(payload.images)
        db.mediaDao().insertAudio(payload.audio)
        db.tagDao().insertAll(payload.tags)
        db.tagDao().insertExhibitionRefs(payload.exhibitionTags)
        db.tagDao().insertArtworkRefs(payload.artworkTags)
    }
}
