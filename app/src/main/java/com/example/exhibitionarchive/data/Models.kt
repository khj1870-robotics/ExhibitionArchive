package com.example.exhibitionarchive.data

import androidx.room.*
import kotlinx.serialization.Serializable

@Entity(tableName = "exhibitions")
@Serializable
data class ExhibitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String? = null,
    val posterPath: String? = null,
    val venueName: String? = null,
    val venueAddress: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val officialUrl: String? = null,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "visits",
    foreignKeys = [ForeignKey(
        entity = ExhibitionEntity::class,
        parentColumns = ["id"],
        childColumns = ["exhibitionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("exhibitionId"), Index("visitedAt")]
)
@Serializable
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exhibitionId: Long,
    val visitedAt: String,
    val oneLineReview: String? = null,
    val detailedReview: String? = null,
    val rating: Float? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "artists")
@Serializable
data class ArtistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val originalName: String? = null,
    val birthYear: Int? = null,
    val deathYear: Int? = null,
    val nationality: String? = null,
    val biography: String? = null,
    val officialUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "artworks",
    foreignKeys = [
        ForeignKey(entity = ExhibitionEntity::class, parentColumns = ["id"], childColumns = ["exhibitionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ArtistEntity::class, parentColumns = ["id"], childColumns = ["artistId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("exhibitionId"), Index("artistId")]
)
@Serializable
data class ArtworkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exhibitionId: Long,
    val artistId: Long? = null,
    val title: String,
    val productionYear: String? = null,
    val medium: String? = null,
    val dimensions: String? = null,
    val sectionName: String? = null,
    val description: String? = null,
    val personalReview: String? = null,
    val sourceUrl: String? = null,
    val displayOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "artwork_images",
    foreignKeys = [ForeignKey(entity = ArtworkEntity::class, parentColumns = ["id"], childColumns = ["artworkId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("artworkId")]
)
@Serializable
data class ArtworkImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val artworkId: Long,
    val localPath: String? = null,
    val externalUrl: String? = null,
    val sourceType: String = "GALLERY",
    val sourceName: String? = null,
    val copyrightText: String? = null,
    val isRepresentative: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audio_records",
    foreignKeys = [
        ForeignKey(entity = ExhibitionEntity::class, parentColumns = ["id"], childColumns = ["exhibitionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ArtworkEntity::class, parentColumns = ["id"], childColumns = ["artworkId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("exhibitionId"), Index("artworkId")]
)
@Serializable
data class AudioRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exhibitionId: Long? = null,
    val artworkId: Long? = null,
    val type: String = "PERSONAL_REVIEW",
    val title: String? = null,
    val filePath: String? = null,
    val externalAudioUrl: String? = null,
    val durationMillis: Long? = null,
    val memo: String? = null,
    val recordedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "visit_notes",
    foreignKeys = [ForeignKey(
        entity = ExhibitionEntity::class,
        parentColumns = ["id"],
        childColumns = ["exhibitionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("exhibitionId"), Index("createdAt")]
)
@Serializable
data class VisitNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exhibitionId: Long,
    val photoPath: String? = null,
    val text: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
@Serializable
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val normalizedName: String = name.trim().lowercase(),
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(primaryKeys = ["exhibitionId", "tagId"], tableName = "exhibition_tags")
@Serializable
data class ExhibitionTagCrossRef(val exhibitionId: Long, val tagId: Long)

@Entity(primaryKeys = ["artworkId", "tagId"], tableName = "artwork_tags")
@Serializable
data class ArtworkTagCrossRef(val artworkId: Long, val tagId: Long)

@Serializable
data class ExhibitionWithVisit(
    @Embedded val exhibition: ExhibitionEntity,
    @Relation(parentColumn = "id", entityColumn = "exhibitionId") val visits: List<VisitEntity>
)

@Serializable
data class ArtworkCard(
    @Embedded val artwork: ArtworkEntity,
    @Relation(parentColumn = "artistId", entityColumn = "id") val artist: ArtistEntity?,
    @Relation(parentColumn = "id", entityColumn = "artworkId") val images: List<ArtworkImageEntity>,
    @Relation(parentColumn = "id", entityColumn = "artworkId") val audio: List<AudioRecordEntity>
)

@Serializable
data class BackupPayload(
    val schemaVersion: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val exhibitions: List<ExhibitionEntity>,
    val visits: List<VisitEntity>,
    val artists: List<ArtistEntity>,
    val artworks: List<ArtworkEntity>,
    val images: List<ArtworkImageEntity>,
    val audio: List<AudioRecordEntity>,
    val tags: List<TagEntity>,
    val exhibitionTags: List<ExhibitionTagCrossRef>,
    val artworkTags: List<ArtworkTagCrossRef>,
    val visitNotes: List<VisitNoteEntity> = emptyList()
)
