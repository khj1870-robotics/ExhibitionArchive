import { load } from "cheerio";
import {
  absoluteUrl,
  cleanText,
  decodeNextFlight,
  exhibition,
  extractDates,
  extractJsonArray,
  normalizeDate
} from "./lib.mjs";

const SOURCES = {
  artmap: ["artmap", "아트맵", "https://art-map.co.kr/"],
  neolook: ["neolook", "네오룩", "https://neolook.com/"],
  artbava: ["artbava", "아트바바", "https://www.artbava.com/"],
  mmca: ["mmca", "국립현대미술관", "https://www.mmca.go.kr/"],
  daelim: ["daelim", "대림미술관", "https://www.daelimmuseum.org/"],
  leeum: ["leeum", "리움미술관", "https://www.leeumhoam.org/leeum"],
  sema: ["sema", "서울시립미술관", "https://sema.seoul.go.kr/"]
};

function fromCard(sourceKey, card, link, status = "unknown") {
  const [sourceId, sourceName, homepage] = SOURCES[sourceKey];
  const text = cleanText(card.text()) ?? "";
  const dates = extractDates(text);
  const title = cleanText(
    card.find("h1,h2,h3,h4,.title,.tit,.subject,.ex-title,strong").first().text()
  ) || cleanText(link.attr("title")) || cleanText(link.text());
  const image = card.find("img").first();
  return exhibition({
    sourceId,
    sourceName,
    homepage,
    title,
    venue: card.find(".venue,.place,.location,.museum,.gallery").first().text(),
    startDate: dates.startDate,
    endDate: dates.endDate,
    status,
    imageUrl: image.attr("data-src") || image.attr("src"),
    detailUrl: link.attr("href")
  });
}

function parseCards(html, sourceKey, linkSelector, status) {
  const $ = load(html);
  const results = [];
  $(linkSelector).each((_, element) => {
    const link = $(element);
    const card = link.closest("article,li,.item,.card,.list-item,.exhibition-item,.board-list");
    results.push(fromCard(sourceKey, card.length ? card : link.parent(), link, status));
  });
  return results.filter(Boolean);
}

export function parseArtMap(html, status = "unknown") {
  return parseCards(html, "artmap", 'a[href*="exhibition/view.php"]', status);
}

export function parseNeolook(html, status = "unknown") {
  return parseCards(html, "neolook", 'a[href*="/archives/"]', status);
}

export function parseArtbava(html) {
  const flight = decodeNextFlight(html);
  return extractJsonArray(flight, "initialExhibits").map((item) => exhibition({
    sourceId: SOURCES.artbava[0],
    sourceName: SOURCES.artbava[1],
    homepage: SOURCES.artbava[2],
    title: item.name,
    venue: item.gallery?.name_kr || item.gallery?.name,
    startDate: item.start_date,
    endDate: item.end_date,
    status: item.status || "unknown",
    imageUrl: item.image,
    detailUrl: `/exhibits/${item.slug}`,
    description: item.fee_summery
  })).filter(Boolean);
}

export function parseMmca(html, status = "unknown") {
  return parseCards(html, "mmca", 'a[href*="exhibitionsDetail.do"]', status);
}

export function parseDaelim(payload, status = "unknown") {
  const items = payload?.data?.data ?? payload?.data?.list ?? payload?.data ?? [];
  if (!Array.isArray(items)) return [];
  return items.map((item) => exhibition({
    sourceId: SOURCES.daelim[0],
    sourceName: SOURCES.daelim[1],
    homepage: SOURCES.daelim[2],
    title: item.prgNm,
    venue: item.prgPlcVal,
    startDate: item.prgStartDt,
    endDate: item.prgEndDt,
    status,
    imageUrl: item.prgImgFileUrl || item.prgImgFileUrlMobile,
    detailUrl: `/exhibition/current/${item.prgIdx}`,
    description: item.prgDesc
  })).filter(Boolean);
}

export function parseLeeum(payload, status = "unknown") {
  const items = payload?.list ?? [];
  return items.map((item) => exhibition({
    sourceId: SOURCES.leeum[0],
    sourceName: SOURCES.leeum[1],
    homepage: SOURCES.leeum[2],
    title: item.title,
    venue: item.location,
    startDate: item.startDate,
    endDate: ["9999-12-31", "1900-01-01"].includes(normalizeDate(item.endDate)) ? null : item.endDate,
    status,
    imageUrl: item.image ? `https://www.leeumhoam.org/upload/exhibition/${encodeURIComponent(item.image)}` : null,
    detailUrl: `/leeum/exhibition/${item.exhibitionSeq}?params=Y`,
    description: item.imageAlt
  })).filter(Boolean);
}

export function parseSema(html, status = "unknown") {
  const results = parseCards(html, "sema", 'a[href*="/whatson/exhibition/detail"]', status);
  if (results.length) return results;
  const $ = load(html);
  return $('a[href*="exNo="]').map((_, element) => {
    const link = $(element);
    return fromCard("sema", link.closest("article,li,div"), link, status);
  }).get().filter(Boolean);
}

export { absoluteUrl };
