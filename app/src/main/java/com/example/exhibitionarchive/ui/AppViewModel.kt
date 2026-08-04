package com.example.exhibitionarchive.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.exhibitionarchive.data.*
import com.example.exhibitionarchive.util.BackupManager
import com.example.exhibitionarchive.util.ExhibitionImportInfo
import com.example.exhibitionarchive.util.ExhibitionPageFetcher
import com.example.exhibitionarchive.util.ExhibitionSearchApi
import com.example.exhibitionarchive.util.FileStore
import com.example.exhibitionarchive.util.SearchResultItem
import com.example.exhibitionarchive.util.SecureKeyStore
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
    private val searchApi: ExhibitionSearchApi,
    private val secureKeyStore: SecureKeyStore
) : ViewModel() {
    val exhibitions = repository.exhibitions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val visits = repository.visits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val artists = repository.artists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags = repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
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
        onDone: (Long) -> Unit
    ) {
        if (title.isBlank()) { _message.value = "전시명을 입력하세요."; return }
        viewModelScope.launch {
            runCatching {
                repository.createExhibition(title, date.toString(), posterPath, venue, oneLine, detail, tags.split(','), description, startDate, endDate, officialUrl)
            }
                .onSuccess(onDone)
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

    fun searchOnline(query: String, onResult: (List<SearchResultItem>) -> Unit) {
        if (query.isBlank()) { _message.value = "검색어를 입력하세요."; return }
        val keys = secureKeyStore.getNaverKeys()
        if (keys == null) { _message.value = "설정 화면에서 네이버 검색 API 키를 먼저 입력하세요."; return }
        viewModelScope.launch {
            runCatching { searchApi.search(query, keys.first, keys.second) }
                .onSuccess(onResult)
                .onFailure { _message.value = it.message ?: "검색에 실패했습니다." }
        }
    }

    fun hasNaverApiKeys(): Boolean = secureKeyStore.getNaverKeys() != null

    fun saveNaverApiKeys(clientId: String, clientSecret: String) {
        secureKeyStore.saveNaverKeys(clientId.trim(), clientSecret.trim())
        _message.value = "네이버 API 키를 저장했습니다."
    }

    fun addArtwork(exhibitionId: Long, title: String, artist: String?, review: String?, imagePath: String?, onDone: () -> Unit) {
        if (title.isBlank()) { _message.value = "작품명을 입력하세요."; return }
        viewModelScope.launch {
            runCatching { repository.addArtwork(exhibitionId, title, artist, review, imagePath) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "작품 저장에 실패했습니다." }
        }
    }

    fun exhibition(id: Long) = repository.exhibition(id)
    fun visitsFor(id: Long) = repository.visitsForExhibition(id)
    fun artworksFor(id: Long) = repository.artworksForExhibition(id)
    fun audioFor(id: Long) = repository.audioForExhibition(id)
    fun tagsFor(id: Long) = repository.tagsForExhibition(id)
    fun searchExhibitions(q: String) = repository.searchExhibitions(q)
    fun searchArtworks(q: String) = repository.searchArtworks(q)
    fun searchArtists(q: String) = repository.searchArtists(q)


    fun saveAudio(exhibitionId: Long, filePath: String, title: String = "음성 기록", onDone: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching { repository.addAudio(AudioRecordEntity(exhibitionId = exhibitionId, title = title, filePath = filePath)) }
                .onSuccess { onDone() }
                .onFailure { _message.value = it.message ?: "음성 저장에 실패했습니다." }
        }
    }

    suspend fun exportBackup(uri: android.net.Uri) = backupManager.export(uri)
    suspend fun importBackup(uri: android.net.Uri) = backupManager.importReplace(uri)
}
