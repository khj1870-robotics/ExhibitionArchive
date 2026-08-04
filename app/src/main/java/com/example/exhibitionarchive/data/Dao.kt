package com.example.exhibitionarchive.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExhibitionDao {
    @Query("SELECT * FROM exhibitions ORDER BY updatedAt DESC") fun observeAll(): Flow<List<ExhibitionEntity>>
    @Query("SELECT * FROM exhibitions WHERE id=:id") fun observe(id: Long): Flow<ExhibitionEntity?>
    @Query("SELECT * FROM exhibitions WHERE id=:id") suspend fun get(id: Long): ExhibitionEntity?
    @Insert suspend fun insert(item: ExhibitionEntity): Long
    @Update suspend fun update(item: ExhibitionEntity)
    @Delete suspend fun delete(item: ExhibitionEntity)
    @Query("SELECT * FROM exhibitions WHERE title LIKE '%' || :query || '%' OR venueName LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun search(query: String): Flow<List<ExhibitionEntity>>
    @Query("SELECT * FROM exhibitions") suspend fun allNow(): List<ExhibitionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ExhibitionEntity>)
    @Query("SELECT * FROM exhibitions WHERE id IN (SELECT DISTINCT exhibitionId FROM visits) ORDER BY updatedAt DESC") fun observeVisited(): Flow<List<ExhibitionEntity>>
    @Query("SELECT * FROM exhibitions WHERE id NOT IN (SELECT DISTINCT exhibitionId FROM visits) ORDER BY startDate IS NULL, startDate") fun observeWishlist(): Flow<List<ExhibitionEntity>>
}

@Dao
interface VisitDao {
    @Query("SELECT * FROM visits ORDER BY visitedAt DESC") fun observeAll(): Flow<List<VisitEntity>>
    @Query("SELECT * FROM visits WHERE exhibitionId=:exhibitionId ORDER BY visitedAt DESC") fun observeForExhibition(exhibitionId: Long): Flow<List<VisitEntity>>
    @Query("SELECT * FROM visits WHERE visitedAt BETWEEN :from AND :to ORDER BY visitedAt") fun observeBetween(from: String, to: String): Flow<List<VisitEntity>>
    @Insert suspend fun insert(item: VisitEntity): Long
    @Update suspend fun update(item: VisitEntity)
    @Query("SELECT * FROM visits") suspend fun allNow(): List<VisitEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<VisitEntity>)
}

@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY name") fun observeAll(): Flow<List<ArtistEntity>>
    @Query("SELECT * FROM artists WHERE id=:id") fun observe(id: Long): Flow<ArtistEntity?>
    @Query("SELECT * FROM artists WHERE name LIKE '%' || :query || '%' ORDER BY name") fun search(query: String): Flow<List<ArtistEntity>>
    @Insert suspend fun insert(item: ArtistEntity): Long
    @Update suspend fun update(item: ArtistEntity)
    @Query("SELECT * FROM artists") suspend fun allNow(): List<ArtistEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ArtistEntity>)
}

@Dao
interface ArtworkDao {
    @Transaction @Query("SELECT * FROM artworks WHERE exhibitionId=:exhibitionId ORDER BY displayOrder, createdAt") fun observeForExhibition(exhibitionId: Long): Flow<List<ArtworkCard>>
    @Transaction @Query("SELECT * FROM artworks WHERE artistId=:artistId ORDER BY createdAt DESC") fun observeForArtist(artistId: Long): Flow<List<ArtworkCard>>
    @Transaction @Query("SELECT a.* FROM artworks a INNER JOIN artwork_tags x ON a.id=x.artworkId WHERE x.tagId=:tagId ORDER BY a.createdAt DESC") fun observeForTag(tagId: Long): Flow<List<ArtworkCard>>
    @Transaction @Query("SELECT * FROM artworks WHERE id=:id") fun observeCard(id: Long): Flow<ArtworkCard?>
    @Query("SELECT * FROM artworks WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR personalReview LIKE '%' || :query || '%' ORDER BY updatedAt DESC") fun search(query: String): Flow<List<ArtworkEntity>>
    @Insert suspend fun insert(item: ArtworkEntity): Long
    @Update suspend fun update(item: ArtworkEntity)
    @Query("SELECT * FROM artworks WHERE id=:id") suspend fun get(id: Long): ArtworkEntity?
    @Query("DELETE FROM artworks WHERE id=:id") suspend fun deleteById(id: Long)
    @Query("SELECT * FROM artworks") suspend fun allNow(): List<ArtworkEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ArtworkEntity>)
}

@Dao
interface MediaDao {
    @Insert suspend fun insertImage(item: ArtworkImageEntity): Long
    @Insert suspend fun insertAudio(item: AudioRecordEntity): Long
    @Query("DELETE FROM artwork_images WHERE id=:id") suspend fun deleteImage(id: Long)
    @Query("SELECT * FROM audio_records WHERE exhibitionId=:exhibitionId ORDER BY recordedAt DESC") fun observeAudioForExhibition(exhibitionId: Long): Flow<List<AudioRecordEntity>>
    @Query("SELECT * FROM audio_records WHERE artworkId=:artworkId ORDER BY recordedAt DESC") fun observeAudioForArtwork(artworkId: Long): Flow<List<AudioRecordEntity>>
    @Query("SELECT * FROM artwork_images") suspend fun allImages(): List<ArtworkImageEntity>
    @Query("SELECT * FROM audio_records") suspend fun allAudio(): List<AudioRecordEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertImages(items: List<ArtworkImageEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAudio(items: List<AudioRecordEntity>)
    @Query("DELETE FROM audio_records WHERE id=:id") suspend fun deleteAudio(id: Long)
    @Query("UPDATE audio_records SET artworkId=:artworkId, exhibitionId=NULL WHERE id IN (:ids)") suspend fun reassignToArtwork(ids: List<Long>, artworkId: Long)
}

@Dao
interface VisitNoteDao {
    @Query("SELECT * FROM visit_notes WHERE exhibitionId=:exhibitionId ORDER BY createdAt DESC") fun observeForExhibition(exhibitionId: Long): Flow<List<VisitNoteEntity>>
    @Insert suspend fun insert(item: VisitNoteEntity): Long
    @Delete suspend fun delete(item: VisitNoteEntity)
    @Query("SELECT * FROM visit_notes") suspend fun allNow(): List<VisitNoteEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<VisitNoteEntity>)
    @Query("SELECT * FROM visit_notes WHERE id IN (:ids)") suspend fun getByIds(ids: List<Long>): List<VisitNoteEntity>
    @Query("DELETE FROM visit_notes WHERE id IN (:ids)") suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name") fun observeAll(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags WHERE normalizedName=:normalized LIMIT 1") suspend fun find(normalized: String): TagEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(item: TagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun linkExhibition(ref: ExhibitionTagCrossRef)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun linkArtwork(ref: ArtworkTagCrossRef)
    @Query("DELETE FROM exhibition_tags WHERE exhibitionId=:id") suspend fun clearExhibitionLinks(id: Long)
    @Query("DELETE FROM artwork_tags WHERE artworkId=:id") suspend fun clearArtworkLinks(id: Long)
    @Transaction suspend fun setExhibitionTags(exhibitionId: Long, names: List<String>) {
        clearExhibitionLinks(exhibitionId)
        names.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach { name ->
            val normalized = name.lowercase()
            val existing = find(normalized)
            val tagId = existing?.id ?: insert(TagEntity(name = name, normalizedName = normalized)).takeIf { it > 0 } ?: find(normalized)!!.id
            linkExhibition(ExhibitionTagCrossRef(exhibitionId, tagId))
        }
    }
    @Query("SELECT t.* FROM tags t INNER JOIN exhibition_tags x ON t.id=x.tagId WHERE x.exhibitionId=:id ORDER BY t.name") fun observeForExhibition(id: Long): Flow<List<TagEntity>>
    @Query("SELECT t.* FROM tags t INNER JOIN artwork_tags x ON t.id=x.tagId WHERE x.artworkId=:id ORDER BY t.name") fun observeForArtwork(id: Long): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags") suspend fun allNow(): List<TagEntity>
    @Query("SELECT * FROM exhibition_tags") suspend fun allExhibitionRefs(): List<ExhibitionTagCrossRef>
    @Query("SELECT * FROM artwork_tags") suspend fun allArtworkRefs(): List<ArtworkTagCrossRef>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExhibitionRefs(items: List<ExhibitionTagCrossRef>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertArtworkRefs(items: List<ArtworkTagCrossRef>)
}
