package com.example.exhibitionarchive.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import java.net.URI
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class SearchResultItem(val title: String, val link: String, val description: String)

@Serializable
private data class NaverSearchResponse(val items: List<NaverSearchItem> = emptyList())

@Serializable
private data class NaverSearchItem(val title: String = "", val link: String = "", val description: String = "")

@Singleton
class ExhibitionSearchApi @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String, clientId: String, clientSecret: String, display: Int = 10): List<SearchResultItem> =
        withContext(Dispatchers.IO) {
            val encodedQuery = URLEncoder.encode(buildScopedQuery(query), "UTF-8")
            val response = Jsoup.connect("$BASE_URL?query=$encodedQuery&display=$display")
                .header("X-Naver-Client-Id", clientId)
                .header("X-Naver-Client-Secret", clientSecret)
                .ignoreContentType(true)
                .timeout(15_000)
                .execute()
            parseResponse(response.body())
        }

    internal fun parseResponse(rawJson: String): List<SearchResultItem> {
        val parsed = json.decodeFromString<NaverSearchResponse>(rawJson)
        return parsed.items.map { item ->
            SearchResultItem(title = stripHtml(item.title), link = item.link, description = stripHtml(item.description))
        }.filter { isAllowedExhibitionSite(it.link) }
    }

    internal fun buildScopedQuery(query: String): String =
        "${query.trim()} (${SEARCH_DOMAINS.joinToString(" OR ") { "site:$it" }})"

    private fun stripHtml(text: String): String = Jsoup.parse(text).text()

    companion object {
        private const val BASE_URL = "https://openapi.naver.com/v1/search/webkr.json"
        val SEARCH_DOMAINS = listOf(
            "art-map.co.kr",
            "neolook.com",
            "artbava.com",
            "mmca.go.kr",
            "daelimmuseum.org",
            "leeumhoam.org",
            "sema.seoul.go.kr",
            "nowonarts.kr"
        )
    }
}

private fun isAllowedExhibitionSite(url: String): Boolean {
    val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return ExhibitionSearchApi.SEARCH_DOMAINS.any { host == it || host.endsWith(".$it") }
}
