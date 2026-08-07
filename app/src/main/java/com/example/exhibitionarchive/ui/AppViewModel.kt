package com.example.exhibitionarchive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exhibitionarchive.data.*
import com.example.exhibitionarchive.util.BackupManager
import com.example.exhibitionarchive.util.ExhibitionImportInfo
import com.example.exhibitionarchive.util.ExhibitionPageFetcher
import com.example.exhibitionarchive.util.FileStore
import com.example.exhibitionarchive.util.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: AppRepository,
    val fileStore: FileStore,
    private val backupManager: BackupManager,
    private val pageFetcher: ExhibitionPageFetcher,
    private val settingsStore: SettingsStore
) : ViewModel() {
    val exhibitions = repository.exhibitions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val visitedExhibitions = repository.visitedExhibitions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wishlist = repository.wishlist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fontScale = settingsStore.fontScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)
    val gridColumns = settingsStore.gridColumns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 2)

    fun setFontScale(scale: Float) { viewModelScope.launch { settingsStore.setFontScale(scale) } }
    fun setGridColumns(columns: Int) { viewModelScope.launch { settingsStore.setGridColumns(columns) } }
    val visits = repository.visits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = repository.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artistUsage = repository.artistUsage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tagUsage = repository.tagUsage.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun clearMessage() { _message.value = null }

    fun createExhibition(
        title: String,
        date: LocalDate,
        posterPath: String?,
        venue: String?,
        oneLine: String?,
        detail: String?,
        tags: String,
        description: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        officialUrl: String? = null,
        rating: Float? = null,
        onDone: (Long) -> Unit
    ) {
        if (title.isBlank()) { _message.value = "전시명을 입력하세요."; return }
        viewModelScope.launch {
            runCatching {
                repository.createExhibition(title, date.toString(), posterPath, venue, oneLine, detail, tags.split(','), description, startDate, endDate, officialUrl, rating)
            }
                .onSuccess(onDone)
                .onFailure { _message.value = it.message ?: "저장에 실패했습니다." }
        }
    }

    fun createWishlist(
        title: String,
        posterPath: String?,
        venue: String?,
        tags: String,
        description: String? = null,
        startDate: String? = null,
        endDate: String? = null,
        officialUrl: String? = null,
        onDone: (Long) -> Unit
    ) {
        if (title.isBlank()) { _message.value = "전시명을 입력하세요."; return }
        viewModelScope.launch {
            runCatching {
                repository.createExhibition(title, null, posterPath, venue, null, null, tags.split(','), description, startDate, endDate, officialUrl, null)
            }
                .onSuccess(onDone)
                .onFailure { _message.value = it.message ?: "저장에 실패했습니다." }
        }
    }

    fun markVisited(exhibitionId: Long, date: LocalDate, oneLine: String?, detail: String?, rating: Float?, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.markVisited(exhibitionId, date.toString(), oneLine, detail, rating) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "저장에 실패했습니다." }
        }
    }

    fun updateExhibition(exhibition: ExhibitionEntity, tags: String, visit: VisitEntity?, onDone: () -> Unit) {
        if (exhibition.title.isBlank()) { _message.value = "전시명을 입력하세요."; return }
        viewModelScope.launch {
            runCatching { repository.updateExhibition(exhibition, tags.split(','), visit) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "저장에 실패했습니다." }
        }
    }

    fun importExhibitionInfo(url: String, onResult: (ExhibitionImportInfo) -> Unit) {
        if (url.isBlank()) { _message.value = "링크를 입력하세요."; return }
        viewModelScope.launch {
            runCatching { pageFetcher.fetch(url.trim()) }
                .onSuccess(onResult)
                .onFailure { _message.value = it.message ?: "전시 정보를 가져오지 못했습니다." }
        }
    }

    fun addArtwork(
        exhibitionId: Long,
        title: String,
        artist: String?,
        review: String?,
        imagePaths: List<String> = emptyList(),
        sourceUrl: String? = null,
        medium: String? = null,
        description: String? = null,
        audioClips: List<Pair<String, Long?>> = emptyList(),
        tagNames: List<String> = emptyList(),
        onDone: (Long) -> Unit
    ) {
        if (title.isBlank()) { _message.value = "작품명을 입력하세요."; return }
        viewModelScope.launch {
            runCatching { repository.addArtwork(exhibitionId, title, artist, review, imagePaths, sourceUrl, medium, description, audioClips, tagNames) }
                .onSuccess { id -> onDone(id) }
                .onFailure { _message.value = it.message ?: "작품 저장에 실패했습니다." }
        }
    }

    fun assignVisitItemsToArtwork(artworkId: Long, noteIds: List<Long>, audioIds: List<Long>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.assignVisitItemsToArtwork(artworkId, noteIds, audioIds) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "작품에 배정하지 못했습니다." }
        }
    }

    fun updateArtwork(artwork: ArtworkEntity, artistName: String?, newImagePaths: List<String>, newAudioClips: List<Pair<String, Long?>>, tagNames: List<String>, onDone: () -> Unit) {
        if (artwork.title.isBlank()) { _message.value = "작품명을 입력하세요."; return }
        viewModelScope.launch {
            runCatching { repository.updateArtwork(artwork, artistName, newImagePaths, newAudioClips, tagNames) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "저장에 실패했습니다." }
        }
    }

    fun deleteArtworkImage(id: Long) { viewModelScope.launch { runCatching { repository.deleteArtworkImage(id) }.onFailure { _message.value = it.message ?: "삭제에 실패했습니다." } } }
    fun deleteAudio(id: Long) { viewModelScope.launch { runCatching { repository.deleteAudio(id) }.onFailure { _message.value = it.message ?: "삭제에 실패했습니다." } } }

    fun exhibition(id: Long) = repository.exhibition(id)
    fun visitsFor(id: Long) = repository.visitsForExhibition(id)
    fun artworksFor(id: Long) = repository.artworksForExhibition(id)
    fun artworksForArtist(id: Long) = repository.artworksForArtist(id)
    fun artworksForTag(id: Long) = repository.artworksForTag(id)
    fun artwork(id: Long) = repository.artwork(id)
    fun audioFor(id: Long) = repository.audioForExhibition(id)
    fun visitNotesFor(id: Long) = repository.visitNotesForExhibition(id)
    fun tagsFor(id: Long) = repository.tagsForExhibition(id)
    fun tagsForArtwork(id: Long) = repository.tagsForArtwork(id)
    fun searchExhibitions(q: String) = repository.searchExhibitions(q)
    fun searchArtworks(q: String) = repository.searchArtworks(q)
    fun searchArtists(q: String) = repository.searchArtists(q)


    fun addVisitNote(exhibitionId: Long, photoPath: String?, text: String?, onDone: () -> Unit = {}) {
        if (photoPath == null && text.isNullOrBlank()) { _message.value = "메모를 입력하세요."; return }
        viewModelScope.launch {
            runCatching { repository.addVisitNote(exhibitionId, photoPath, text) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "관람 메모 저장에 실패했습니다." }
        }
    }

    fun updateVisitNote(note: VisitNoteEntity, text: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.updateVisitNoteText(note, text) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "메모 수정에 실패했습니다." }
        }
    }

    fun deleteVisitItems(noteIds: List<Long>, audioIds: List<Long>, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.deleteVisitItems(noteIds, audioIds) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "삭제하지 못했습니다." }
        }
    }

    fun addAnnotatedImage(artworkId: Long, path: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.addAnnotatedImage(artworkId, path) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "메모 이미지를 저장하지 못했습니다." }
        }
    }

    fun saveAudio(exhibitionId: Long, filePath: String, title: String = "음성 기록", durationMillis: Long? = null, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.addAudio(AudioRecordEntity(exhibitionId = exhibitionId, title = title, filePath = filePath, durationMillis = durationMillis)) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "음성 저장에 실패했습니다." }
        }
    }

    suspend fun exportBackup(uri: android.net.Uri) = backupManager.export(uri)
    suspend fun importBackup(uri: android.net.Uri) = backupManager.importReplace(uri)
}
