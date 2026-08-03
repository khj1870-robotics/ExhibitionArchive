import {
  parseArtMap,
  parseArtbava,
  parseDaelim,
  parseLeeum,
  parseMmca,
  parseNeolook,
  parseSema
} from "./parsers.mjs";
import { renderPage } from "./browser.mjs";

const REQUEST_HEADERS = {
  "user-agent": "ExhibitionArchive/1.0 (+https://github.com/khj1870-robotics/ExhibitionArchive)",
  "accept-language": "ko-KR,ko;q=0.9,en;q=0.7"
};

async function request(url, asJson = false) {
  const response = await fetch(url, {
    headers: REQUEST_HEADERS,
    redirect: "follow",
    signal: AbortSignal.timeout(20_000)
  });
  if (!response.ok) throw new Error(`HTTP ${response.status}: ${url}`);
  return asJson ? response.json() : response.text();
}

async function pages(requests) {
  const settled = await Promise.allSettled(requests.map(async ([url, parser, status, json = false, browserFallback = false]) => {
    let body;
    try {
      body = await request(url, json);
    } catch (error) {
      if (!browserFallback || json) throw error;
      body = await renderPage(url);
    }
    let parsed = parser(body, status);
    if (!parsed.length && browserFallback && !json) {
      body = await renderPage(url);
      parsed = parser(body, status);
    }
    if (!parsed.length && typeof body === "string") {
      const scripts = [...body.matchAll(/<script[^>]+src=["']([^"']+)["']/gi)].map((match) => match[1]).slice(-4);
      throw new Error(`전시 목록을 찾지 못했습니다. scripts=${scripts.join(",")}`);
    }
    return parsed;
  }));
  const results = settled.filter((result) => result.status === "fulfilled").flatMap((result) => result.value);
  if (results.length) return results;
  const failure = settled.find((result) => result.status === "rejected");
  if (failure) throw failure.reason;
  return [];
}

export const sources = [
  {
    id: "artmap",
    name: "아트맵",
    homepage: "https://art-map.co.kr/",
    collect: () => pages([
      ["https://m.art-map.co.kr/exhibition/list.php?sta=1", parseArtMap, "current", false, true],
      ["https://m.art-map.co.kr/exhibition/list.php?sta=2", parseArtMap, "upcoming", false, true]
    ])
  },
  {
    id: "neolook",
    name: "네오룩",
    homepage: "https://neolook.com/",
    collect: () => pages([
      ["https://neolook.com/archives", parseNeolook, "unknown", false, true]
    ])
  },
  {
    id: "artbava",
    name: "아트바바",
    homepage: "https://www.artbava.com/",
    collect: () => pages([
      ["https://www.artbava.com/exhibits", parseArtbava, "unknown"]
    ])
  },
  {
    id: "mmca",
    name: "국립현대미술관",
    homepage: "https://www.mmca.go.kr/",
    collect: () => pages([
      ["https://www.mmca.go.kr/exhibitions/progressList.do", parseMmca, "current", false, true],
      ["https://www.mmca.go.kr/exhibitions/futureProgressList.do", parseMmca, "upcoming", false, true]
    ])
  },
  {
    id: "daelim",
    name: "대림미술관",
    homepage: "https://www.daelimmuseum.org/",
    collect: () => pages([
      ["https://api.daelimmuseum.org/v1/program/exhibition/searchCurrentExhibition?pageNo=1&prgPlcCds=", parseDaelim, "current", true],
      ["https://api.daelimmuseum.org/v1/program/exhibition/searchNextExhibition?pageNo=1", parseDaelim, "upcoming", true]
    ])
  },
  {
    id: "leeum",
    name: "리움미술관",
    homepage: "https://www.leeumhoam.org/leeum",
    collect: () => pages([1, 2].map((state) => [
      `https://www.leeumhoam.org/leeum/exhibition/list?view=grid&state%5B%5D=${state}&keyword=&limit=100&mainFlag=false&found=LM&page=1&tab=all`,
      parseLeeum,
      state === 1 ? "current" : "upcoming",
      true
    ]))
  },
  {
    id: "sema",
    name: "서울시립미술관",
    homepage: "https://sema.seoul.go.kr/",
    collect: () => pages([
      ["https://sema.seoul.go.kr/kr/whatson/landing?whatChoice2=N&whatChoice3=N&whatChoice4=N&whatChoice5=N&whatsonMenuDivList=EX&whenType=FROM_TODAY", parseSema, "current", false, true],
      ["https://sema.seoul.go.kr/kr/whatson/landing?whatChoice2=N&whatChoice3=N&whatChoice4=N&whatChoice5=N&whatsonMenuDivList=EX&whenType=PLAN_DAY", parseSema, "upcoming", false, true]
    ])
  }
];
