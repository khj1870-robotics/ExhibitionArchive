import test from "node:test";
import assert from "node:assert/strict";
import {
  parseArtMap,
  parseArtbava,
  parseDaelim,
  parseLeeum,
  parseMmca,
  parseNeolook,
  parseSema
} from "../src/parsers.mjs";
import { dedupe } from "../src/lib.mjs";

test("HTML 목록형 사이트 파서가 공통 전시 형식으로 변환한다", () => {
  const cases = [
    [parseArtMap, '<li><a href="/exhibition/view.php?idx=1"><img src="/a.jpg"><strong>빛의 전시</strong><span class="place">아트홀</span><span>2026.08.01 - 2026.09.02</span></a></li>', "artmap"],
    [parseNeolook, '<article><a href="/archives/20260801"><h3>새로운 풍경</h3></a><p class="venue">갤러리 봄</p><p>2026.08.01~2026.08.31</p></article>', "neolook"],
    [parseMmca, '<li><a href="/exhibitions/exhibitionsDetail.do?exhId=1"><h3>현대의 시선</h3></a><span class="place">서울</span><span>2026.08.01 - 2026.10.01</span></li>', "mmca"],
    [parseSema, '<article><a href="/kr/whatson/exhibition/detail?exNo=1"><h3>도시의 감각</h3></a><span class="location">서소문본관</span><span>2026-08-01 ~ 2026-09-30</span></article>', "sema"]
  ];
  for (const [parser, html, sourceId] of cases) {
    const item = parser(html, "current").at(0);
    assert.equal(item.sourceId, sourceId);
    assert.equal(item.startDate, "2026-08-01");
    assert.ok(item.title);
    assert.ok(item.detailUrl.startsWith("https://"));
  }
});

test("아트바바 Next.js 초기 데이터를 읽는다", () => {
  const data = '0:{"initialExhibits":[{"name":"경계의 빛","slug":"light-border","start_date":"2026-08-01","end_date":"2026-09-01","image":"/poster.jpg","gallery":{"name_kr":"갤러리 A"}}]}';
  const encoded = JSON.stringify(data);
  const html = `<script>self.__next_f.push([1,${encoded}])</script>`;
  const item = parseArtbava(html).at(0);
  assert.equal(item.title, "경계의 빛");
  assert.equal(item.venue, "갤러리 A");
  assert.equal(item.detailUrl, "https://www.artbava.com/exhibits/light-border");
});

test("대림미술관 API 응답을 읽는다", () => {
  const item = parseDaelim({ data: { data: [{ prgIdx: 42, prgNm: "사진의 시간", prgPlcVal: "대림미술관", prgStartDt: "2026-08-01", prgEndDt: "2026-11-01", prgImgFileUrl: "https://img.example/poster.jpg" }] } }, "current").at(0);
  assert.equal(item.title, "사진의 시간");
  assert.equal(item.detailUrl, "https://www.daelimmuseum.org/exhibition/current/42");
});

test("리움 API의 무기한 종료일을 비운다", () => {
  const item = parseLeeum({ list: [{ exhibitionSeq: 7, title: "상설전", location: "리움미술관", startDate: "2026-01-01", endDate: "9999-12-31", image: "포스터.jpg" }] }, "current").at(0);
  assert.equal(item.endDate, null);
  assert.match(item.imageUrl, /%ED%8F%AC%EC%8A%A4%ED%84%B0/);
});

test("같은 전시는 정보가 더 풍부한 항목 하나만 남긴다", () => {
  const base = { sourceId: "a", sourceName: "A", title: "같은 전시", venue: "같은 장소", startDate: "2026-08-01", endDate: "2026-09-01", status: "current", imageUrl: null, detailUrl: "https://a.example/1", description: null };
  const results = dedupe([base, { ...base, sourceId: "b", sourceName: "B", imageUrl: "https://b.example/image.jpg", description: "설명" }]);
  assert.equal(results.length, 1);
  assert.equal(results[0].sourceId, "b");
});
