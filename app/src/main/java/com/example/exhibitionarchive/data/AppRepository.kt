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
    fun audioForArtwork(id: Long) = db.mediaDao().observeAudioForArtwork(id)
    fun tagsForArtwork(id: Long) = db.tagDao().observeForArtwork(id)
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

    suspend fun updateExhibition(
        exhibitionId: Long,
        title: String,
        visitDate: String,
        posterPath: String?,
        venue: String?,
        oneLine: String?,
        detail: String?,
        tagNames: List<String>
    ): String? = db.withTransaction {
        val current = requireNotNull(db.exhibitionDao().get(exhibitionId)) { "전시를 찾을 수 없습니다." }
        val oldPoster = current.posterPath?.takeIf { it != posterPath }
        db.exhibitionDao().update(current.copy(
            title = title.trim(), posterPath = posterPath,
            venueName = venue?.trim()?.ifBlank { null },
            updatedAt = System.currentTimeMillis()
        ))
        val visit = db.visitDao().latestForExhibition(exhibitionId)
        if (visit == null) {
            db.visitDao().insert(VisitEntity(exhibitionId = exhibitionId, visitedAt = visitDate, oneLineReview = oneLine.clean(), detailedReview = detail.clean()))
        } else {
            db.visitDao().update(visit.copy(visitedAt = visitDate, oneLineReview = oneLine.clean(), detailedReview = detail.clean(), updatedAt = System.currentTimeMillis()))
        }
        db.tagDao().setExhibitionTags(exhibitionId, tagNames)
        oldPoster
    }

    suspend fun deleteExhibition(id: Long): DeletedMedia = db.withTransaction {
        val exhibition = requireNotNull(db.exhibitionDao().get(id)) { "전시를 찾을 수 없습니다." }
        val files = DeletedMedia(
            images = listOfNotNull(exhibition.posterPath) + db.mediaDao().imagePathsForExhibition(id),
            audio = db.mediaDao().audioPathsForExhibition(id)
        )
        db.tagDao().clearExhibitionLinks(id)
        db.artworkDao().idsForExhibition(id).forEach { db.tagDao().clearArtworkLinks(it) }
        db.exhibitionDao().delete(exhibition)
        files
    }

    suspend fun addArtwork(exhibitionId: Long, title: String, artistName: String?, review: String?, imagePath: String?): Long = db.withTransaction {
        requireNotNull(db.exhibitionDao().get(exhibitionId)) { "전시를 찾을 수 없습니다." }
        val artistId = findOrCreateArtist(artistName)
        val id = db.artworkDao().insert(ArtworkEntity(exhibitionId = exhibitionId, artistId = artistId, title = title.trim(), personalReview = review?.trim()?.ifBlank { null }))
        imagePath?.let { db.mediaDao().insertImage(ArtworkImageEntity(artworkId = id, localPath = it, sourceType = "GALLERY", isRepresentative = true)) }
        id
    }

    suspend fun updateArtwork(
        artworkId: Long,
        title: String,
        artistName: String?,
        productionYear: String?,
        medium: String?,
        dimensions: String?,
        sectionName: String?,
        description: String?,
        review: String?
    ) = db.withTransaction {
        val current = requireNotNull(db.artworkDao().getCard(artworkId)?.artwork) { "작품을 찾을 수 없습니다." }
        db.artworkDao().update(current.copy(
            artistId = findOrCreateArtist(artistName), title = title.trim(),
            productionYear = productionYear.clean(), medium = medium.clean(), dimensions = dimensions.clean(),
            sectionName = sectionName.clean(), description = description.clean(), personalReview = review.clean(),
            updatedAt = System.currentTimeMillis()
        ))
    }

    suspend fun addArtworkImage(artworkId: Long, path: String) {
        requireNotNull(db.artworkDao().getCard(artworkId)) { "작품을 찾을 수 없습니다." }
        val representative = db.mediaDao().imagesForArtworkNow(artworkId).isEmpty()
        db.mediaDao().insertImage(ArtworkImageEntity(artworkId = artworkId, localPath = path, isRepresentative = representative))
    }

    suspend fun deleteArtworkImage(imageId: Long): String? = db.withTransaction {
        val image = requireNotNull(db.mediaDao().image(imageId)) { "사진을 찾을 수 없습니다." }
        db.mediaDao().deleteImage(imageId)
        image.localPath
    }

    suspend fun deleteArtwork(id: Long): DeletedMedia = db.withTransaction {
        requireNotNull(db.artworkDao().getCard(id)) { "작품을 찾을 수 없습니다." }
        val files = DeletedMedia(db.mediaDao().imagePathsForArtwork(id), db.mediaDao().audioPathsForArtwork(id))
        db.tagDao().clearArtworkLinks(id)
        db.artworkDao().deleteById(id)
        files
    }

    suspend fun addAudio(item: AudioRecordEntity) = db.mediaDao().insertAudio(item)
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

    private suspend fun findOrCreateArtist(rawName: String?): Long? {
        val name = rawName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = name.lowercase()
        return db.artistDao().findByNormalizedName(normalized)?.id
            ?: db.artistDao().insert(ArtistEntity(name = name, normalizedName = normalized))
    }

    private fun String?.clean() = this?.trim()?.ifBlank { null }
}

data class DeletedMedia(val images: List<String> = emptyList(), val audio: List<String> = emptyList()) {
    val all: List<String> get() = images + audio
}
