package com.example.exhibitionarchive.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

data class ExhibitionImportInfo(
    val title: String? = null,
    val description: String? = null,
    val posterImageUrl: String? = null,
    val officialUrl: String? = null,
    val venueName: String? = null,
    val startDate: String? = null,
    val endDate: String? = null
)

private data class JsonLdEventInfo(
    val title: String?,
    val description: String?,
    val startDate: String?,
    val endDate: String?,
    val venueName: String?,
    val imageUrl: String?
)

@Singleton
class ExhibitionPageFetcher @Inject constructor() {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(url: String): ExhibitionImportInfo = withContext(Dispatchers.IO) {
        val document = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15_000).get()
        parse(document, url)
    }

    internal fun parse(document: Document, fallbackUrl: String): ExhibitionImportInfo {
        val baseUrl = document.baseUri().ifBlank { fallbackUrl }
        val fromJsonLd = parseJsonLd(document)
        val ogTitle = document.select("meta[property=\"og:title\"]").firstOrNull()?.attr("content")
        val ogDescription = document.select("meta[property=\"og:description\"]").firstOrNull()?.attr("content")
            ?: document.select("meta[name=\"description\"]").firstOrNull()?.attr("content")
        val ogImage = document.select("meta[property=\"og:image\"]").firstOrNull()?.attr("abs:content")
        val canonical = document.select("link[rel=\"canonical\"]").firstOrNull()?.attr("abs:href")

        return ExhibitionImportInfo(
            title = fromJsonLd?.title ?: ogTitle?.ifBlank { null } ?: document.title().ifBlank { null },
            description = fromJsonLd?.description ?: ogDescription?.ifBlank { null },
            posterImageUrl = fromJsonLd?.imageUrl?.let { resolveUrl(baseUrl, it) } ?: ogImage?.ifBlank { null },
            officialUrl = canonical?.ifBlank { null } ?: baseUrl,
            venueName = fromJsonLd?.venueName,
            startDate = fromJsonLd?.startDate,
            endDate = fromJsonLd?.endDate
        )
    }

    private fun parseJsonLd(document: Document): JsonLdEventInfo? {
        val scripts = document.select("script[type=\"application/ld+json\"]")
        for (script in scripts) {
            val element = runCatching { json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            findEvent(element)?.let { return it }
        }
        return null
    }

    private fun findEvent(element: JsonElement): JsonLdEventInfo? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findEvent(it) }
        is JsonObject -> {
            val type = element["@type"]?.let { typeAsString(it) }
            if (type != null && type.contains("Event", ignoreCase = true)) {
                eventFromObject(element)
            } else {
                element["@graph"]?.let { findEvent(it) }
            }
        }
        else -> null
    }

    private fun typeAsString(element: JsonElement): String = when (element) {
        is JsonArray -> element.joinToString(",") { typeAsString(it) }
        is JsonPrimitive -> element.contentOrNull.orEmpty()
        else -> ""
    }

    private fun eventFromObject(obj: JsonObject): JsonLdEventInfo {
        val locationObj = when (val location = obj["location"]) {
            is JsonArray -> location.firstOrNull { it is JsonObject } as? JsonObject
            is JsonObject -> location
            else -> null
        }
        val venueName = locationObj?.get("name")?.asStringOrNull()
            ?: locationObj?.get("address")?.let { addressName(it) }
        val image = obj["image"]
        val imageUrl = when (image) {
            is JsonArray -> image.firstOrNull()?.let { imageUrlFrom(it) }
            null -> null
            else -> imageUrlFrom(image)
        }
        return JsonLdEventInfo(
            title = obj["name"]?.asStringOrNull(),
            description = obj["description"]?.asStringOrNull(),
            startDate = normalizeDate(obj["startDate"]?.asStringOrNull()),
            endDate = normalizeDate(obj["endDate"]?.asStringOrNull()),
            venueName = venueName,
            imageUrl = imageUrl
        )
    }

    private fun imageUrlFrom(element: JsonElement): String? = when (element) {
        is JsonObject -> element["url"]?.asStringOrNull()
        is JsonPrimitive -> element.contentOrNull
        else -> null
    }

    private fun addressName(element: JsonElement): String? = when (element) {
        is JsonObject -> element["name"]?.asStringOrNull() ?: element["streetAddress"]?.asStringOrNull()
        is JsonPrimitive -> element.contentOrNull
        else -> null
    }

    private fun resolveUrl(base: String, target: String): String? =
        runCatching { URI(base).resolve(target).toString() }.getOrNull() ?: target.ifBlank { null }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Android) ExhibitionArchiveApp"
    }
}

private fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun normalizeDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return Regex("""^\d{4}-\d{2}-\d{2}""").find(raw.trim())?.value
}
