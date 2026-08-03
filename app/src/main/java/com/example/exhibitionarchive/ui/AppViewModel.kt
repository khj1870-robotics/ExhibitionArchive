package com.example.exhibitionarchive.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exhibitionarchive.data.AppRepository
import com.example.exhibitionarchive.data.AudioRecordEntity
import com.example.exhibitionarchive.util.AudioRecorder
import com.example.exhibitionarchive.util.BackupManager
import com.example.exhibitionarchive.util.ExhibitionImportInfo
import com.example.exhibitionarchive.util.ExhibitionPageFetcher
import com.example.exhibitionarchive.util.ExhibitionSearchApi
import com.example.exhibitionarchive.util.FileStore
import com.example.exhibitionarchive.util.SearchResultItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PendingArtwork(
    val title: String,
    val artist: String?,
    val review: String?,
    val imagePath: String?,
    val externalImageUrl: String? = null,
    val productionYear: String? = null,
    val medium: String? = null,
    val dimensions: String? = null,
    val description: String? = null
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repository: AppRepository,
    val fileStore: FileStore,
    private val audioRecorder: AudioRecorder,
    private val backupManager: BackupManager,
    private val pageFetcher: ExhibitionPageFetcher,
    private val searchApi: ExhibitionSearchApi
) : ViewModel() {
    val exhibitions = repository.exhibitions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val visits = repository.visits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = repository.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving

    fun clearMessage() { _message.value = null }
    fun showMessage(value: String) { _message.value = value }

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
        pendingArtworks: List<PendingArtwork> = emptyList(),
        onDone: (Long) -> Unit
    ) {
        if (title.isBlank()) return showMessage("전시명을 입력하세요.")
        launchMutation("저장에 실패했습니다.", onDone) {
            val exhibitionId = repository.createExhibition(
                title, date.toString(), posterPath, venue, oneLine, detail, tags.split(','),
                description, startDate, endDate, officialUrl
            )
            pendingArtworks.forEach { artwork ->
                repository.addArtwork(
                    exhibitionId, artwork.title, artwork.artist, artwork.review, artwork.imagePath,
                    artwork.externalImageUrl, artwork.productionYear, artwork.medium, artwork.dimensions, artwork.description
                )
            }
            exhibitionId
        }
    }

    fun updateExhibition(id: Long, title: String, date: LocalDate, posterPath: String?, venue: String?, oneLine: String?, detail: String?, tags: String, onDone: () -> Unit) {
        if (title.isBlank()) return showMessage("전시명을 입력하세요.")
        launchMutation("수정에 실패했습니다.", { oldPoster ->
            fileStore.deleteManagedFile(oldPoster)
            showMessage("전시 기록을 수정했습니다.")
            onDone()
        }) {
            repository.updateExhibition(id, title, date.toString(), posterPath, venue, oneLine, detail, tags.split(','))
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

    fun searchOnline(query: String, onResult: (List<SearchResultItem>) -> Unit) {
        if (query.isBlank()) { _message.value = "검색어를 입력하세요."; return }
        viewModelScope.launch {
            runCatching { searchApi.search(query) }
                .onSuccess(onResult)
                .onFailure { _message.value = it.message ?: "공개 전시정보를 불러오지 못했습니다." }
        }
    }

    fun deleteExhibition(id: Long, onDone: () -> Unit) {
        launchMutation("삭제에 실패했습니다.", { deleted ->
            deleted.all.forEach(fileStore::deleteManagedFile)
            showMessage("전시 기록을 삭제했습니다.")
            onDone()
        }) { repository.deleteExhibition(id) }
    }

    fun addArtwork(exhibitionId: Long, title: String, artist: String?, review: String?, imagePath: String?, onDone: (Long) -> Unit) {
        if (title.isBlank()) return showMessage("작품명을 입력하세요.")
        launchMutation("작품 저장에 실패했습니다.", onDone) {
            repository.addArtwork(exhibitionId, title, artist, review, imagePath)
        }
    }

    fun updateArtwork(id: Long, title: String, artist: String?, year: String?, medium: String?, dimensions: String?, section: String?, description: String?, review: String?, onDone: () -> Unit) {
        if (title.isBlank()) return showMessage("작품명을 입력하세요.")
        launchMutation("작품 수정에 실패했습니다.", {
            showMessage("작품 정보를 수정했습니다.")
            onDone()
        }) {
            repository.updateArtwork(id, title, artist, year, medium, dimensions, section, description, review)
        }
    }

    fun addArtworkImage(id: Long, path: String) {
        viewModelScope.launch {
            runCatching { repository.addArtworkImage(id, path) }
                .onFailure { fileStore.deleteManagedFile(path); showMessage(it.message ?: "사진 저장에 실패했습니다.") }
        }
    }

    fun deleteArtworkImage(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteArtworkImage(id) }
                .onSuccess(fileStore::deleteManagedFile)
                .onFailure { showMessage(it.message ?: "사진 삭제에 실패했습니다.") }
        }
    }

    fun deleteArtwork(id: Long, onDone: () -> Unit) {
        launchMutation("작품 삭제에 실패했습니다.", { deleted ->
            deleted.all.forEach(fileStore::deleteManagedFile)
            showMessage("작품을 삭제했습니다.")
            onDone()
        }) { repository.deleteArtwork(id) }
    }

    fun exhibition(id: Long) = repository.exhibition(id)
    fun visitsFor(id: Long) = repository.visitsForExhibition(id)
    fun artworksFor(id: Long) = repository.artworksForExhibition(id)
    fun artwork(id: Long) = repository.artwork(id)
    fun audioFor(id: Long) = repository.audioForExhibition(id)
    fun audioForArtwork(id: Long) = repository.audioForArtwork(id)
    fun tagsFor(id: Long) = repository.tagsForExhibition(id)
    fun searchExhibitions(q: String) = repository.searchExhibitions(q)
    fun searchArtworks(q: String) = repository.searchArtworks(q)
    fun searchArtists(q: String) = repository.searchArtists(q)

    fun startAudioRecording(): String? {
        val file = fileStore.newAudioFile()
        return runCatching {
            audioRecorder.start(file)
            file.absolutePath
        }.onFailure {
            fileStore.deleteManagedFile(file.absolutePath)
            showMessage(it.message ?: "녹음을 시작하지 못했습니다.")
        }.getOrNull()
    }

    fun finishAudioRecording(exhibitionId: Long?, artworkId: Long?, filePath: String, durationMillis: Long) {
        if (!audioRecorder.stop()) {
            fileStore.deleteManagedFile(filePath)
            showMessage("녹음 시간이 너무 짧습니다. 다시 녹음하세요.")
            return
        }
        viewModelScope.launch {
            runCatching {
                repository.addAudio(AudioRecordEntity(
                    exhibitionId = exhibitionId,
                    artworkId = artworkId,
                    title = "음성 메모",
                    filePath = filePath,
                    durationMillis = durationMillis
                ))
            }.onFailure {
                fileStore.deleteManagedFile(filePath)
                showMessage(it.message ?: "음성 메모 저장에 실패했습니다.")
            }
        }
    }

    fun cancelAudioRecording(filePath: String) {
        audioRecorder.stopSafely()
        fileStore.deleteManagedFile(filePath)
    }

    fun deleteAudio(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteAudio(id) }
                .onSuccess(fileStore::deleteManagedFile)
                .onFailure { showMessage(it.message ?: "음성 메모 삭제에 실패했습니다.") }
        }
    }

    suspend fun exportBackup(uri: Uri) = backupManager.export(uri)
    suspend fun importBackup(uri: Uri) = backupManager.importReplace(uri)

    private fun <T> launchMutation(fallbackMessage: String, onSuccess: (T) -> Unit, block: suspend () -> T) {
        if (_saving.value) return
        _saving.value = true
        viewModelScope.launch {
            try {
                runCatching { block() }
                    .onSuccess(onSuccess)
                    .onFailure { showMessage(it.message ?: fallbackMessage) }
            } finally {
                _saving.value = false
            }
        }
    }

    override fun onCleared() {
        audioRecorder.stopSafely()
        super.onCleared()
    }
}
