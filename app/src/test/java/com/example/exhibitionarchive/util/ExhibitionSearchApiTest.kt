package com.example.exhibitionarchive.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExhibitionSearchApiTest {
    private val api = ExhibitionSearchApi()

    @Test
    fun parseResponse_stripsHighlightTagsFromTitleAndDescription() {
        val rawJson = """
            {
              "items": [
                {
                  "title": "<b>노원구</b> 전시회 안내",
                  "link": "https://www.nowonarts.kr/channels/exhibition/programs/946",
                  "description": "이번 <b>전시</b>는 다양한 작품을 소개합니다."
                }
              ]
            }
        """.trimIndent()

        val results = api.parseResponse(rawJson)

        assertEquals(1, results.size)
        assertEquals("노원구 전시회 안내", results[0].title)
        assertEquals("https://www.nowonarts.kr/channels/exhibition/programs/946", results[0].link)
        assertEquals("이번 전시는 다양한 작품을 소개합니다.", results[0].description)
    }

    @Test
    fun parseResponse_returnsEmptyListWhenNoItems() {
        val results = api.parseResponse("""{"items": []}""")
        assertEquals(emptyList<SearchResultItem>(), results)
    }

    @Test
    fun parseResponse_keepsOnlyConfiguredExhibitionSites() {
        val rawJson = """
            {
              "items": [
                {"title":"국립현대미술관 전시","link":"https://www.mmca.go.kr/exhibitions/exhibitionsDetail.do?id=1","description":"설명"},
                {"title":"일반 블로그","link":"https://example.com/post/1","description":"설명"}
              ]
            }
        """.trimIndent()

        val results = api.parseResponse(rawJson)

        assertEquals(1, results.size)
        assertEquals("국립현대미술관 전시", results.single().title)
    }

    @Test
    fun buildScopedQuery_containsAllConfiguredSites() {
        val query = api.buildScopedQuery("고야")

        ExhibitionSearchApi.SEARCH_DOMAINS.forEach { domain ->
            check(query.contains("site:$domain"))
        }
    }
}
