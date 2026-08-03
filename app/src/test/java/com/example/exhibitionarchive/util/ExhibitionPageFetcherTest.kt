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

    @Test
    fun parseInterparkSummary_extractsGoodsInformationFromClientApi() {
        val rawJson = """
            {
              "common": { "message": "success" },
              "data": {
                "goodsName": "렘브란트에서 고야까지 : 톨레도 미술관 명작전",
                "placeName": "더현대 서울 ALT. 1",
                "playStartDate": "20260321",
                "playEndDate": "20260704",
                "goodsLargeImageUrl": "//ticketimage.interpark.com/Play/image/large/26/26003737_p.gif",
                "displayTemplate": "특별할인 티켓은 현장에서 구매 가능합니다.",
                "playTime": "평일 오전 10시 30분 - 오후 8시"
              }
            }
        """.trimIndent()
        val url = "https://tickets.interpark.com/goods/26003737"

        val info = fetcher.parseInterparkSummary(rawJson, url)

        assertEquals("렘브란트에서 고야까지 : 톨레도 미술관 명작전", info.title)
        assertEquals("더현대 서울 ALT. 1", info.venueName)
        assertEquals("2026-03-21", info.startDate)
        assertEquals("2026-07-04", info.endDate)
        assertEquals("https://ticketimage.interpark.com/Play/image/large/26/26003737_p.gif", info.posterImageUrl)
        assertEquals(url, info.officialUrl)
        assertEquals("특별할인 티켓은 현장에서 구매 가능합니다.\n평일 오전 10시 30분 - 오후 8시", info.description)
    }

    @Test
    fun parse_extractsVisualArtworkFromJsonLd() {
        val html = """
            <html><head>
                <script type="application/ld+json">
                {
                  "@context": "https://schema.org",
                  "@type": "ExhibitionEvent",
                  "name": "구조화 작품 테스트",
                  "workFeatured": [
                    {
                      "@type": "VisualArtwork",
                      "name": "파도",
                      "creator": { "@type": "Person", "name": "김작가" },
                      "dateCreated": "2024",
                      "artMedium": "캔버스에 유채",
                      "size": "100 × 80 cm",
                      "image": "/images/wave.jpg",
                      "description": "푸른 파도를 그린 회화"
                    }
                  ]
                }
                </script>
            </head><body></body></html>
        """.trimIndent()
        val url = "https://example.com/exhibitions/3"

        val info = fetcher.parse(Jsoup.parse(html, url), url)

        assertEquals(1, info.artworks.size)
        assertEquals("파도", info.artworks.single().title)
        assertEquals("김작가", info.artworks.single().artistName)
        assertEquals("2024", info.artworks.single().productionYear)
        assertEquals("캔버스에 유채", info.artworks.single().medium)
        assertEquals("100 × 80 cm", info.artworks.single().dimensions)
        assertEquals("https://example.com/images/wave.jpg", info.artworks.single().imageUrl)
    }

    @Test
    fun parse_extractsArtworkCaptionsAndIgnoresPosterAltText() {
        val html = """
            <html><body>
                <img src="/poster.jpg" alt="올해의 작가상 2025" />
                <img src="/art/one.jpg" alt="김영은, ‹미래의 청취자들에게 I›, 2022, 단채널 비디오, 8분." />
                <img src="/art/two.jpg" alt="김지평, ‹디바-무당›, 2023, 혼합 재료, 170 × 115 cm." />
                <img src="/art/duplicate.jpg" alt="김지평, ‹디바-무당›, 2023, 혼합 재료, 170 × 115 cm." />
            </body></html>
        """.trimIndent()
        val url = "https://www.mmca.go.kr/exhibitions/exhibitionsDetail.do?exhId=1"

        val info = fetcher.parse(Jsoup.parse(html, url), url)

        assertEquals(2, info.artworks.size)
        assertEquals("미래의 청취자들에게 I", info.artworks[0].title)
        assertEquals("김영은", info.artworks[0].artistName)
        assertEquals("2022", info.artworks[0].productionYear)
        assertEquals("https://www.mmca.go.kr/art/one.jpg", info.artworks[0].imageUrl)
        assertEquals("170 × 115 cm", info.artworks[1].dimensions)
    }
}
