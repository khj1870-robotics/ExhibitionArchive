package com.example.exhibitionarchive.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResultItem(
    val title: String,
    val link: String,
    val description: String,
    val venueName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val posterImageUrl: String? = null,
    val sourceName: String,
    val exhibitionDescription: String? = null
)

fun SearchResultItem.toImportInfo() = ExhibitionImportInfo(
    title = title,
    description = exhibitionDescription,
    posterImageUrl = posterImageUrl,
    officialUrl = link,
    venueName = venueName,
    startDate = startDate,
    endDate = endDate
)

@Serializable
internal data class PublicExhibitionDataset(
    val schemaVersion: Int = 1,
    val generatedAt: String = "",
    val exhibitions: List<PublicExhibition> = emptyList()
)

@Serializable
internal data class PublicExhibition(
    val sourceId: String = "",
    val sourceName: String = "",
    val title: String = "",
    val venue: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val status: String = "unknown",
    val imageUrl: String? = null,
    val detailUrl: String = "",
    val description: String? = null
)

@Singleton
class ExhibitionSearchApi @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cache: CachedDataset? = null

    suspend fun search(query: String, display: Int = 10): List<SearchResultItem> = withContext(Dispatchers.IO) {
        searchDataset(loadDataset(), query, display)
    }

    internal fun parseDataset(rawJson: String): PublicExhibitionDataset =
        json.decodeFromString<PublicExhibitionDataset>(rawJson)

    internal fun searchDataset(dataset: PublicExhibitionDataset, query: String, display: Int = 10): List<SearchResultItem> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()
        return dataset.exhibitions.asSequence()
            .mapNotNull { item -> score(item, normalizedQuery)?.let { score -> score to item } }
            .sortedWith(compareBy<Pair<Int, PublicExhibition>> { it.first }.thenBy { it.second.startDate ?: "9999-99-99" })
            .take(display)
            .map { (_, item) -> item.toSearchResult() }
            .toList()
    }

    private fun loadDataset(): PublicExhibitionDataset {
        val now = System.currentTimeMillis()
        val cached = cache
        cached?.takeIf { now - it.loadedAt < CACHE_MILLIS }?.let { return it.dataset }
        return runCatching {
            val response = Jsoup.connect(DATASET_URL)
                .ignoreContentType(true)
                .followRedirects(true)
                .userAgent("ExhibitionArchive Android")
                .timeout(15_000)
                .execute()
            parseDataset(response.body()).also { cache = CachedDataset(it, now) }
        }.getOrElse { error ->
            cached?.dataset ?: throw IllegalStateException("공개 전시정보를 불러오지 못했습니다. 잠시 후 다시 시도하세요.", error)
        }
    }

    private fun score(item: PublicExhibition, query: String): Int? {
        val title = normalize(item.title)
        return when {
            title.startsWith(query) -> 0
            title.contains(query) -> 1
            normalize(item.venue).contains(query) -> 2
            normalize(item.sourceName).contains(query) -> 3
            normalize(item.description).contains(query) -> 4
            else -> null
        }
    }

    private fun PublicExhibition.toSearchResult(): SearchResultItem {
        val dates = listOfNotNull(startDate, endDate).joinToString(" ~ ")
        val summary = listOf(sourceName, venue, dates.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")
        return SearchResultItem(
            title = title,
            link = detailUrl,
            description = summary,
            venueName = venue,
            startDate = startDate,
            endDate = endDate,
            posterImageUrl = imageUrl,
            sourceName = sourceName,
            exhibitionDescription = description
        )
    }

    private fun normalize(value: String?): String = value.orEmpty()
        .normalizeUnicode()
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE, "")

    private fun String.normalizeUnicode(): String = java.text.Normalizer.normalize(this, java.text.Normalizer.Form.NFKC)

    private data class CachedDataset(val dataset: PublicExhibitionDataset, val loadedAt: Long)

    companion object {
        private const val DATASET_URL = "https://github.com/khj1870-robotics/ExhibitionArchive/releases/download/exhibition-data/exhibitions.json"
        private const val CACHE_MILLIS = 10 * 60 * 1000L
        private val WHITESPACE = Regex("\\s+")
    }
}
