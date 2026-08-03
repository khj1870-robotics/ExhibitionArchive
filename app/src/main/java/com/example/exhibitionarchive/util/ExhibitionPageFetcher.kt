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
import org.jsoup.nodes.Element
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
    val endDate: String? = null,
    val artworks: List<ImportedArtworkInfo> = emptyList()
)

data class ImportedArtworkInfo(
    val title: String,
    val artistName: String? = null,
    val imageUrl: String? = null,
    val productionYear: String? = null,
    val medium: String? = null,
    val dimensions: String? = null,
    val description: String? = null
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
        interparkGoodsCode(url)?.let { return@withContext fetchInterpark(it, url) }
        val document = Jsoup.connect(url).userAgent(USER_AGENT).timeout(15_000).get()
        parse(document, url)
    }

    private fun fetchInterpark(goodsCode: String, originalUrl: String): ExhibitionImportInfo {
        val response = Jsoup.connect("$INTERPARK_API_BASE/v1/goods/$goodsCode/summary")
            .userAgent(USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .header("Origin", "https://tickets.interpark.com")
            .referrer("https://tickets.interpark.com/")
            .ignoreContentType(true)
            .timeout(15_000)
            .execute()
        return parseInterparkSummary(response.body(), originalUrl)
    }

    internal fun parseInterparkSummary(rawJson: String, originalUrl: String): ExhibitionImportInfo {
        val root = json.parseToJsonElement(rawJson) as? JsonObject
            ?: error("인터파크 응답 형식이 올바르지 않습니다.")
        val data = root["data"] as? JsonObject
            ?: error("인터파크 전시 정보를 찾지 못했습니다.")
        val title = data["goodsName"]?.asStringOrNull()?.ifBlank { null }
            ?: error("인터파크 전시명을 찾지 못했습니다.")
        val poster = data["goodsLargeImageUrl"]?.asStringOrNull()
            ?.let(::normalizeProtocolRelativeUrl)
        val description = listOfNotNull(
            data["displayTemplate"]?.asStringOrNull()?.ifBlank { null },
            data["playTime"]?.asStringOrNull()?.ifBlank { null }
        ).joinToString("\n").ifBlank { null }
        return ExhibitionImportInfo(
            title = title,
            description = description,
            posterImageUrl = poster,
            officialUrl = originalUrl,
            venueName = data["placeName"]?.asStringOrNull()?.ifBlank { null },
            startDate = normalizeCompactDate(data["playStartDate"]?.asStringOrNull()),
            endDate = normalizeCompactDate(data["playEndDate"]?.asStringOrNull())
        )
    }

    internal fun parse(document: Document, fallbackUrl: String): ExhibitionImportInfo {
        val baseUrl = document.baseUri().ifBlank { fallbackUrl }
        val fromJsonLd = parseJsonLd(document)
        val artworks = (parseJsonLdArtworks(document, baseUrl) + parseCaptionArtworks(document, baseUrl))
            .distinctBy { "${it.artistName.orEmpty().trim().lowercase()}|${it.title.trim().lowercase()}" }
        val ogTitle = document.select("meta[property=\"og:title\"]").firstOrNull()?.attr("content")
        val ogDescription = document.select("meta[property=\"og:description\"]").firstOrNull()?.attr("content")
            ?: document.select("meta[name=\"description\"]").firstOrNull()?.attr("content")
        val ogImage = document.select("meta[property=\"og:image\"]").firstOrNull()?.attr("abs:content")
        val canonical = document.select("link[rel=\"canonical\"]").firstOrNull()?.attr("abs:href")

        val base = ExhibitionImportInfo(
            title = fromJsonLd?.title ?: ogTitle?.ifBlank { null } ?: document.title().ifBlank { null },
            description = fromJsonLd?.description ?: ogDescription?.ifBlank { null },
            posterImageUrl = fromJsonLd?.imageUrl?.let { resolveUrl(baseUrl, it) } ?: ogImage?.ifBlank { null },
            officialUrl = canonical?.ifBlank { null } ?: baseUrl,
            venueName = fromJsonLd?.venueName,
            startDate = fromJsonLd?.startDate,
            endDate = fromJsonLd?.endDate,
            artworks = artworks
        )
        return applySitePatch(document, base)
    }

    private fun applySitePatch(document: Document, base: ExhibitionImportInfo): ExhibitionImportInfo {
        val host = runCatching { URI(document.baseUri()) }.getOrNull()?.host.orEmpty()
        return when {
            host.endsWith("sema.seoul.go.kr") -> patchSema(document, base)
            host.endsWith("nowonarts.kr") -> patchNowonarts(document, base)
            else -> base
        }
    }

    private fun patchSema(document: Document, base: ExhibitionImportInfo): ExhibitionImportInfo {
        val titleEl = document.selectFirst("div.c-image-letterbox[aria-label]")
        val title = titleEl?.attr("aria-label")?.ifBlank { null }
        val posterUrl = document.selectFirst("div.c-image-letterbox img")?.attr("abs:src")?.ifBlank { null }
        val fields = document.select("div.c-section.o_Ex_more div.t-meta.pure-g div.l-stacked")
            .flatMap { labelValuePairs(it) }
            .toMap()
        val (start, end) = extractDateRange(fields["전시기간"])
        val description = document.selectFirst("div.o_textmore")?.text()?.ifBlank { null }
        return base.copy(
            title = title ?: base.title,
            posterImageUrl = posterUrl ?: base.posterImageUrl,
            venueName = fields["전시장소"] ?: base.venueName,
            startDate = start ?: base.startDate,
            endDate = end ?: base.endDate,
            description = description ?: base.description
        )
    }

    private fun patchNowonarts(document: Document, base: ExhibitionImportInfo): ExhibitionImportInfo {
        val info = document.selectFirst("div.exhibitioninfo div.detailUi") ?: return base
        val title = info.selectFirst("p.tit")?.text()?.ifBlank { null }
        val (start, end) = extractDateRange(info.selectFirst("p.period")?.text())
        val posterUrl = document.selectFirst("div.detailCont img")?.attr("abs:src")?.ifBlank { null }
        return base.copy(
            title = title ?: base.title,
            startDate = start ?: base.startDate,
            endDate = end ?: base.endDate,
            posterImageUrl = posterUrl ?: base.posterImageUrl,
            description = null
        )
    }

    private fun labelValuePairs(block: Element): List<Pair<String, String>> =
        block.select("> div.o_h1").mapNotNull { label ->
            val value = label.nextElementSibling()
            if (value != null && value.tagName() == "p") label.text().trim() to value.text().trim() else null
        }

    private fun parseJsonLd(document: Document): JsonLdEventInfo? {
        val scripts = document.select("script[type=\"application/ld+json\"]")
        for (script in scripts) {
            val element = runCatching { json.parseToJsonElement(script.data()) }.getOrNull() ?: continue
            findEvent(element)?.let { return it }
        }
        return null
    }

    private fun parseJsonLdArtworks(document: Document, baseUrl: String): List<ImportedArtworkInfo> {
        val result = mutableListOf<ImportedArtworkInfo>()
        document.select("script[type=\"application/ld+json\"]").forEach { script ->
            val element = runCatching { json.parseToJsonElement(script.data()) }.getOrNull() ?: return@forEach
            collectJsonLdArtworks(element, baseUrl, result)
        }
        return result
    }

    private fun collectJsonLdArtworks(element: JsonElement, baseUrl: String, result: MutableList<ImportedArtworkInfo>) {
        when (element) {
            is JsonArray -> element.forEach { collectJsonLdArtworks(it, baseUrl, result) }
            is JsonObject -> {
                val type = element["@type"]?.let(::typeAsString).orEmpty()
                if (ARTWORK_TYPES.any { type.contains(it, ignoreCase = true) }) {
                    artworkFromJsonLd(element, baseUrl)?.let(result::add)
                }
                element.values.forEach { collectJsonLdArtworks(it, baseUrl, result) }
            }
            else -> Unit
        }
    }

    private fun artworkFromJsonLd(obj: JsonObject, baseUrl: String): ImportedArtworkInfo? {
        val title = obj["name"]?.asStringOrNull()?.trim()?.ifBlank { null } ?: return null
        val imageUrl = when (val image = obj["image"]) {
            is JsonArray -> image.firstNotNullOfOrNull(::imageUrlFrom)
            null -> null
            else -> imageUrlFrom(image)
        }?.let { resolveUrl(baseUrl, it) }
        return ImportedArtworkInfo(
            title = title,
            artistName = creatorName(obj["creator"] ?: obj["artist"]),
            imageUrl = imageUrl,
            productionYear = obj["dateCreated"]?.asStringOrNull()?.let { YEAR.find(it)?.value },
            medium = (obj["artMedium"] ?: obj["material"])?.asStringOrNull()?.trim()?.ifBlank { null },
            dimensions = obj["size"]?.asStringOrNull()?.trim()?.ifBlank { null },
            description = obj["description"]?.asStringOrNull()?.trim()?.ifBlank { null }
        )
    }

    private fun creatorName(element: JsonElement?): String? = when (element) {
        is JsonArray -> element.mapNotNull(::creatorName).distinct().joinToString(", ").ifBlank { null }
        is JsonObject -> element["name"]?.asStringOrNull()?.trim()?.ifBlank { null }
        is JsonPrimitive -> element.contentOrNull?.trim()?.ifBlank { null }
        else -> null
    }

    private fun parseCaptionArtworks(document: Document, baseUrl: String): List<ImportedArtworkInfo> =
        document.select("img[alt], [role=img][aria-label]").mapNotNull { element ->
            val caption = if (element.tagName() == "img") element.attr("alt") else element.attr("aria-label")
            val image = if (element.tagName() == "img") element else element.selectFirst("img")
            val imageUrl = image?.let { imageElement ->
                listOf("abs:src", "abs:data-src", "abs:data-original")
                    .firstNotNullOfOrNull { attribute -> imageElement.attr(attribute).ifBlank { null } }
                    ?.let { resolveUrl(baseUrl, it) }
            }
            artworkFromCaption(caption, imageUrl)
        }

    private fun artworkFromCaption(rawCaption: String, imageUrl: String?): ImportedArtworkInfo? {
        val caption = rawCaption.replace(Regex("\\s+"), " ").trim()
        val match = ARTWORK_CAPTION.matchEntire(caption) ?: return null
        val artist = match.groupValues[1].trim().ifBlank { return null }
        val title = match.groupValues[2].trim().ifBlank { return null }
        val details = match.groupValues[3].trim().trimStart(',').trim()
        val yearMatch = YEAR.find(details)
        val medium = yearMatch?.let { details.substring(it.range.last + 1).trim().trimStart(',').trim().ifBlank { null } }
        return ImportedArtworkInfo(
            title = title,
            artistName = artist,
            imageUrl = imageUrl,
            productionYear = yearMatch?.value,
            medium = medium,
            dimensions = DIMENSIONS.find(details)?.value,
            description = caption
        )
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
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0 Mobile Safari/537.36"
        private const val INTERPARK_API_BASE = "https://api-ticketfront.interpark.com"
        private val ARTWORK_TYPES = listOf("VisualArtwork", "Painting", "Sculpture", "Photograph", "Drawing")
        private val ARTWORK_CAPTION = Regex("^(.{1,100}?),\\s*[‹〈《「『«<](.+?)[›〉》」』»>]\\s*,?\\s*(.*)$")
        private val YEAR = Regex("(?:19|20)\\d{2}(?:\\s*[-–]\\s*(?:19|20)?\\d{2})?")
        private val DIMENSIONS = Regex("\\d+(?:\\.\\d+)?\\s*(?:cm|mm|m)?\\s*[×xX]\\s*\\d+(?:\\.\\d+)?(?:\\s*(?:cm|mm|m))?", RegexOption.IGNORE_CASE)
    }
}

private fun JsonElement.asStringOrNull(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun normalizeDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return Regex("""^\d{4}-\d{2}-\d{2}""").find(raw.trim())?.value
}

private fun normalizeCompactDate(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (!Regex("""\d{8}""").matches(value)) return null
    return "${value.substring(0, 4)}-${value.substring(4, 6)}-${value.substring(6, 8)}"
}

private fun normalizeProtocolRelativeUrl(raw: String): String? = when {
    raw.isBlank() -> null
    raw.startsWith("//") -> "https:$raw"
    raw.startsWith("http://") -> "https://${raw.removePrefix("http://")}"
    else -> raw
}

private fun interparkGoodsCode(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    if (!uri.host.orEmpty().endsWith("interpark.com")) return null
    return Regex("""/goods/(\d+)""").find(uri.path.orEmpty())?.groupValues?.get(1)
}

private fun extractDateRange(text: String?): Pair<String?, String?> {
    if (text.isNullOrBlank()) return null to null
    val matches = Regex("""(\d{4})\.(\d{2})\.(\d{2})""").findAll(text).map { it.value.replace('.', '-') }.toList()
    return matches.getOrNull(0) to matches.getOrNull(1)
}
