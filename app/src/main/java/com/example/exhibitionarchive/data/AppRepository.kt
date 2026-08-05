package com.example.exhibitionarchive.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepository @Inject constructor(private val db: AppDatabase) {
    val exhibitions = db.exhibitionDao().observeAll()
    val visitedExhibitions = db.exhibitionDao().observeVisited()
    val wishlist = db.exhibitionDao().observeWishlist()
    val visits = db.visitDao().observeAll()
    val artists = db.artistDao().observeAll()
    val tags = db.tagDao().observeAll()
    val artistUsage = db.artistDao().observeUsage()
    val tagUsage = db.tagDao().observeUsage()

    fun exhibition(id: Long) = db.exhibitionDao().observe(id)
    fun visitsForExhibition(id: Long) = db.visitDao().observeForExhibition(id)
    fun artworksForExhibition(id: Long) = db.artworkDao().observeForExhibition(id)
    fun artworksForArtist(id: Long) = db.artworkDao().observeForArtist(id)
    fun artworksForTag(id: Long) = db.artworkDao().observeForTag(id)
    fun artwork(id: Long) = db.artworkDao().observeCard(id)
    fun audioForExhibition(id: Long) = db.mediaDao().observeAudioForExhibition(id)
    fun visitNotesForExhibition(id: Long) = db.visitNoteDao().observeForExhibition(id)
    fun tagsForExhibition(id: Long) = db.tagDao().observeForExhibition(id)
    fun tagsForArtwork(id: Long) = db.tagDao().observeForArtwork(id)

    suspend fun createExhibition(
        title: String,
        visitDate: String?,
        posterPath: String?,
        venue: String?,
        oneLine: String?,
        detail: String?,
        tagNames: List<String>,
        description: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        officialUrl: String? = null,
        rating: Float? = null
    ): Long = db.withTransaction {
        val exhibitionId = db.exhibitionDao().insert(
            ExhibitionEntity(
                title = title.trim(),
                posterPath = posterPath,
                venueName = venue?.trim()?.ifBlank { null },
                description = description?.trim()?.ifBlank { null },
                startDate = startDate?.ifBlank { null },
                endDate = endDate?.ifBlank { null },
                officialUrl = officialUrl?.trim()?.ifBlank { null }
            )
        )
        if (visitDate != null) {
            db.visitDao().insert(VisitEntity(exhibitionId = exhibitionId, visitedAt = visitDate, oneLineReview = oneLine?.trim()?.ifBlank { null }, detailedReview = detail?.trim()?.ifBlank { null }, rating = rating))
        }
        db.tagDao().setExhibitionTags(exhibitionId, tagNames)
        exhibitionId
    }

    suspend fun markVisited(exhibitionId: Long, date: String, oneLine: String?, detail: String?, rating: Float?) {
        db.visitDao().insert(VisitEntity(exhibitionId = exhibitionId, visitedAt = date, oneLineReview = oneLine?.trim()?.ifBlank { null }, detailedReview = detail?.trim()?.ifBlank { null }, rating = rating))
    }

    suspend fun updateExhibition(exhibition: ExhibitionEntity, tagNames: List<String>, visit: VisitEntity?) = db.withTransaction {
        db.exhibitionDao().update(exhibition.copy(updatedAt = System.currentTimeMillis()))
        db.tagDao().setExhibitionTags(exhibition.id, tagNames)
        visit?.let { db.visitDao().update(it) }
    }

    suspend fun addArtwork(
        exhibitionId: Long,
        title: String,
        artistName: String?,
        review: String?,
        imagePaths: List<String> = emptyList(),
        sourceUrl: String? = null,
        medium: String? = null,
        description: String? = null,
        audioClips: List<Pair<String, Long?>> = emptyList(),
        tagNames: List<String> = emptyList()
    ): Long = db.withTransaction {
        val artistId = artistName?.trim()?.takeIf { it.isNotEmpty() }?.let { name ->
            db.artistDao().insert(ArtistEntity(name = name))
        }
        val id = db.artworkDao().insert(
            ArtworkEntity(
                exhibitionId = exhibitionId,
                artistId = artistId,
                title = title.trim(),
                medium = medium?.trim()?.ifBlank { null },
                description = description?.trim()?.ifBlank { null },
                personalReview = review?.trim()?.ifBlank { null },
                sourceUrl = sourceUrl?.trim()?.ifBlank { null }
            )
        )
        imagePaths.forEachIndexed { index, path ->
            db.mediaDao().insertImage(ArtworkImageEntity(artworkId = id, localPath = path, sourceType = "GALLERY", isRepresentative = index == 0))
        }
        audioClips.forEach { (path, duration) ->
            db.mediaDao().insertAudio(AudioRecordEntity(artworkId = id, filePath = path, durationMillis = duration))
        }
        if (tagNames.isNotEmpty()) db.tagDao().setArtworkTags(id, tagNames)
        id
    }

    suspend fun updateArtwork(artwork: ArtworkEntity, artistName: String?, newImagePaths: List<String>, newAudioClips: List<Pair<String, Long?>>, tagNames: List<String>) = db.withTransaction {
        val artistId = artistName?.trim()?.takeIf { it.isNotEmpty() }?.let { name -> db.artistDao().insert(ArtistEntity(name = name)) }
        db.artworkDao().update(artwork.copy(artistId = artistId, updatedAt = System.currentTimeMillis()))
        newImagePaths.forEach { path -> db.mediaDao().insertImage(ArtworkImageEntity(artworkId = artwork.id, localPath = path, sourceType = "GALLERY")) }
        newAudioClips.forEach { (path, duration) -> db.mediaDao().insertAudio(AudioRecordEntity(artworkId = artwork.id, filePath = path, durationMillis = duration)) }
        db.tagDao().setArtworkTags(artwork.id, tagNames)
    }

    suspend fun deleteArtworkImage(id: Long) = db.mediaDao().deleteImage(id)
    suspend fun deleteAudio(id: Long) = db.mediaDao().deleteAudio(id)

    suspend fun assignVisitItemsToArtwork(artworkId: Long, noteIds: List<Long>, audioIds: List<Long>) = db.withTransaction {
        if (noteIds.isNotEmpty()) {
            val notes = db.visitNoteDao().getByIds(noteIds)
            notes.forEach { note ->
                note.photoPath?.let { path ->
                    db.mediaDao().insertImage(ArtworkImageEntity(artworkId = artworkId, localPath = path, sourceType = "VISIT_MODE"))
                }
            }
            val texts = notes.mapNotNull { it.text }
            if (texts.isNotEmpty()) {
                db.artworkDao().get(artworkId)?.let { artwork ->
                    val merged = (listOfNotNull(artwork.description) + texts).joinToString("\n\n")
                    db.artworkDao().update(artwork.copy(description = merged))
                }
            }
            db.visitNoteDao().deleteByIds(noteIds)
        }
        if (audioIds.isNotEmpty()) {
            db.mediaDao().reassignToArtwork(audioIds, artworkId)
        }
    }

    suspend fun addAudio(item: AudioRecordEntity) = db.mediaDao().insertAudio(item)
    suspend fun addVisitNote(exhibitionId: Long, photoPath: String?, text: String?) =
        db.visitNoteDao().insert(VisitNoteEntity(exhibitionId = exhibitionId, photoPath = photoPath, text = text?.trim()?.ifBlank { null }))
    suspend fun updateVisitNoteText(note: VisitNoteEntity, text: String) = db.visitNoteDao().update(note.copy(text = text.trim().ifBlank { null }))
    suspend fun deleteVisitItems(noteIds: List<Long>, audioIds: List<Long>) = db.withTransaction {
        if (noteIds.isNotEmpty()) db.visitNoteDao().deleteByIds(noteIds)
        if (audioIds.isNotEmpty()) db.mediaDao().deleteAudioByIds(audioIds)
    }
    suspend fun deleteExhibition(item: ExhibitionEntity) = db.exhibitionDao().delete(item)
    fun searchExhibitions(q: String): Flow<List<ExhibitionEntity>> = db.exhibitionDao().search(q)
    fun searchArtworks(q: String): Flow<List<ArtworkEntity>> = db.artworkDao().search(q)
    fun searchArtists(q: String): Flow<List<ArtistEntity>> = db.artistDao().search(q)

    suspend fun backupPayload() = BackupPayload(
        exhibitions = db.exhibitionDao().allNow(), visits = db.visitDao().allNow(), artists = db.artistDao().allNow(),
        artworks = db.artworkDao().allNow(), images = db.mediaDao().allImages(), audio = db.mediaDao().allAudio(),
        tags = db.tagDao().allNow(), exhibitionTags = db.tagDao().allExhibitionRefs(), artworkTags = db.tagDao().allArtworkRefs(),
        visitNotes = db.visitNoteDao().allNow()
    )

    suspend fun replaceFromBackup(payload: BackupPayload) = db.withTransaction {
        db.clearAllTables()
        db.exhibitionDao().insertAll(payload.exhibitions)
        db.artistDao().insertAll(payload.artists)
        db.visitDao().insertAll(payload.visits)
        db.artworkDao().insertAll(payload.artworks)
        db.mediaDao().insertImages(payload.images)
        db.mediaDao().insertAudio(payload.audio)
        db.visitNoteDao().insertAll(payload.visitNotes)
        db.tagDao().insertAll(payload.tags)
        db.tagDao().insertExhibitionRefs(payload.exhibitionTags)
        db.tagDao().insertArtworkRefs(payload.artworkTags)
    }
}
