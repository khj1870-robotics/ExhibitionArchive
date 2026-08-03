package com.example.exhibitionarchive.util

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExhibitionPageFetcherTest {
    private val fetcher = ExhibitionPageFetcher()

    @Test
    fun parse_usesOpenGraphTagsWhenNoJsonLd() {
        val html = """
            <html><head>
                <title>Fallback Title</title>
                <meta property="og:title" content="오픈그래프 제목" />
                <meta property="og:description" content="오픈그래프 설명" />
                <meta property="og:image" content="/images/poster.jpg" />
                <link rel="canonical" href="https://example.com/exhibitions/1" />
            </head><body></body></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com/exhibitions/1")

        val info = fetcher.parse(document, "https://example.com/exhibitions/1")

        assertEquals("오픈그래프 제목", info.title)
        assertEquals("오픈그래프 설명", info.description)
        assertEquals("https://example.com/images/poster.jpg", info.posterImageUrl)
        assertEquals("https://example.com/exhibitions/1", info.officialUrl)
        assertNull(info.venueName)
        assertNull(info.startDate)
    }

    @Test
    fun parse_prefersJsonLdEventFieldsOverOpenGraph() {
        val html = """
            <html><head>
                <meta property="og:title" content="오픈그래프 제목" />
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "ExhibitionEvent",
                  "name": "구조화 데이터 제목",
                  "description": "구조화 데이터 설명",
                  "startDate": "2024-05-01T10:00:00+09:00",
                  "endDate": "2024-06-30",
                  "location": { "@type": "Place", "name": "노원아트홀" },
                  "image": "https://example.com/poster2.jpg"
                }
                </script>
            </head><body></body></html>
        """.trimIndent()
        val document = Jsoup.parse(html, "https://example.com/exhibitions/2")

        val info = fetcher.parse(document, "https://example.com/exhibitions/2")

        assertEquals("구조화 데이터 제목", info.title)
        assertEquals("구조화 데이터 설명", info.description)
        assertEquals("노원아트홀", info.venueName)
        assertEquals("2024-05-01", info.startDate)
        assertEquals("2024-06-30", info.endDate)
        assertEquals("https://example.com/poster2.jpg", info.posterImageUrl)
    }
}
