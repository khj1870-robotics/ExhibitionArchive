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

    @Test
    fun parse_sema_extractsFieldsFromLabelValueBlocks() {
        val html = """
            <html><head><title>SeMA - 전시 상세 - 테스트 전시</title></head><body>
                <div class="c-image-letterbox o_thumb o_img_mh100" role="img" aria-label="테스트 전시명">
                    <img alt="" src="/upload/poster.jpg">
                </div>
                <div class="c-section o_Ex_more app-none">
                    <div class="t-meta pure-g">
                        <div class="l-stacked l-md-inline pure-u-1 pure-u-md-1-3 l-md-gutter-right">
                            <div class="o_h1">전시장소</div>
                            <p>테스트 갤러리</p>
                            <div class="o_h1">전시기간</div>
                            <p>2026.08.01~2026.08.24</p>
                        </div>
                    </div>
                </div>
                <div class="o_textmore wordWrap"><p>테스트 설명입니다.</p></div>
            </body></html>
        """.trimIndent()
        val url = "https://sema.seoul.go.kr/kr/whatson/exhibition/detail?exNo=1"
        val document = Jsoup.parse(html, url)

        val info = fetcher.parse(document, url)

        assertEquals("테스트 전시명", info.title)
        assertEquals("테스트 갤러리", info.venueName)
        assertEquals("2026-08-01", info.startDate)
        assertEquals("2026-08-24", info.endDate)
        assertEquals("테스트 설명입니다.", info.description)
        assertEquals("https://sema.seoul.go.kr/upload/poster.jpg", info.posterImageUrl)
    }

    @Test
    fun parse_nowonarts_overridesSiteWideOpenGraphWithExhibitionSpecificFields() {
        val html = """
            <html><head>
                <meta property="og:title" content="노원문화재단" />
                <meta property="og:description" content="사이트 공통 설명" />
                <meta property="og:image" content="https://www.nowonarts.kr/common/logo.png" />
            </head><body>
                <div class="sub_in exhibitioninfo">
                    <h2>전시</h2>
                    <div class="detailUi">
                        <p class="tit">테스트 전시명</p>
                        <p class="period"><span>전시기간</span><span>2025.12.19(금) ~ 2026.05.31(일) / 164일간</span></p>
                    </div>
                    <div class="detailCont">
                        <img src="/upload/poster2.jpg">
                        <p>본문 내용</p>
                    </div>
                </div>
            </body></html>
        """.trimIndent()
        val url = "https://www.nowonarts.kr/channels/exhibition/programs/946"
        val document = Jsoup.parse(html, url)

        val info = fetcher.parse(document, url)

        assertEquals("테스트 전시명", info.title)
        assertEquals("2025-12-19", info.startDate)
        assertEquals("2026-05-31", info.endDate)
        assertEquals("https://www.nowonarts.kr/upload/poster2.jpg", info.posterImageUrl)
        assertNull(info.description)
    }
}
