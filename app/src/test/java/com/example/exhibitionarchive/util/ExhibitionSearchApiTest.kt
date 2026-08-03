package com.example.exhibitionarchive.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExhibitionSearchApiTest {
    private val api = ExhibitionSearchApi()

    private val rawJson = """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-08-03T00:00:00.000Z",
          "sources": [],
          "exhibitions": [
            {
              "sourceId": "mmca",
              "sourceName": "국립현대미술관",
              "title": "한국 현대미술의 시선",
              "venue": "국립현대미술관 서울",
              "startDate": "2026-08-01",
              "endDate": "2026-10-01",
              "status": "current",
              "imageUrl": "https://example.com/poster.jpg",
              "detailUrl": "https://www.mmca.go.kr/exhibitions/1",
              "description": "한국 현대미술을 살펴보는 전시"
            },
            {
              "sourceId": "leeum",
              "sourceName": "리움미술관",
              "title": "빛의 경계",
              "venue": "리움미술관",
              "detailUrl": "https://www.leeumhoam.org/leeum/exhibition/2"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parseDataset_readsUnifiedExhibitionData() {
        val dataset = api.parseDataset(rawJson)

        assertEquals(2, dataset.exhibitions.size)
        assertEquals("국립현대미술관", dataset.exhibitions.first().sourceName)
    }

    @Test
    fun searchDataset_matchesTitleVenueAndSourceWithoutSendingQuery() {
        val dataset = api.parseDataset(rawJson)

        assertEquals("한국 현대미술의 시선", api.searchDataset(dataset, "현대 미술").single().title)
        assertEquals("빛의 경계", api.searchDataset(dataset, "리움").single().title)
        assertEquals("한국 현대미술의 시선", api.searchDataset(dataset, "서울").single().title)
    }

    @Test
    fun searchDataset_respectsDisplayLimit() {
        val dataset = api.parseDataset(rawJson)
        assertEquals(1, api.searchDataset(dataset, "미술", display = 1).size)
    }

    @Test
    fun searchResult_convertsDirectlyToImportInfo() {
        val result = api.searchDataset(api.parseDataset(rawJson), "한국 현대").single()
        val info = result.toImportInfo()

        assertEquals(result.title, info.title)
        assertEquals(result.link, info.officialUrl)
        assertEquals(result.venueName, info.venueName)
        assertEquals(result.posterImageUrl, info.posterImageUrl)
        assertEquals("한국 현대미술을 살펴보는 전시", info.description)
    }
}
