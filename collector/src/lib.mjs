import { load } from "cheerio";

export function cleanText(value) {
  if (value == null) return null;
  const text = load(`<body>${String(value)}</body>`)("body").text().replace(/\s+/g, " ").trim();
  return text || null;
}

export function normalizeDate(value) {
  const text = cleanText(value);
  if (!text) return null;
  const match = text.match(/(\d{4})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})/);
  if (!match) return null;
  return `${match[1]}-${match[2].padStart(2, "0")}-${match[3].padStart(2, "0")}`;
}

export function extractDates(value) {
  const text = cleanText(value) ?? "";
  const matches = [...text.matchAll(/\d{4}\s*[.\-/년]\s*\d{1,2}\s*[.\-/월]\s*\d{1,2}/g)]
    .map((match) => normalizeDate(match[0]))
    .filter(Boolean);
  return { startDate: matches[0] ?? null, endDate: matches[1] ?? null };
}

export function absoluteUrl(value, baseUrl) {
  if (!value) return null;
  try {
    return new URL(value, baseUrl).toString();
  } catch {
    return null;
  }
}

export function exhibition(input) {
  const title = cleanText(input.title);
  const detailUrl = absoluteUrl(input.detailUrl, input.homepage);
  if (!title || !detailUrl) return null;
  return {
    sourceId: input.sourceId,
    sourceName: input.sourceName,
    title,
    venue: cleanText(input.venue),
    startDate: normalizeDate(input.startDate),
    endDate: normalizeDate(input.endDate),
    status: input.status ?? "unknown",
    imageUrl: absoluteUrl(input.imageUrl, input.homepage),
    detailUrl,
    description: cleanText(input.description)
  };
}

export function dedupe(items) {
  const byKey = new Map();
  for (const item of items.filter(Boolean)) {
    const key = [item.title, item.venue, item.startDate, item.endDate]
      .map((value) => (value ?? "").normalize("NFKC").toLowerCase().replace(/\s+/g, ""))
      .join("|");
    const existing = byKey.get(key);
    if (!existing || completeness(item) > completeness(existing)) byKey.set(key, item);
  }
  return [...byKey.values()].sort((a, b) =>
    (a.startDate ?? "9999-99-99").localeCompare(b.startDate ?? "9999-99-99") || a.title.localeCompare(b.title, "ko")
  );
}

function completeness(item) {
  return [item.venue, item.startDate, item.endDate, item.imageUrl, item.description].filter(Boolean).length;
}

export function decodeNextFlight(html) {
  const chunks = [];
  const pattern = /self\.__next_f\.push\(\[1,"((?:\\.|[^"\\])*)"\]\)/g;
  for (const match of html.matchAll(pattern)) {
    try {
      chunks.push(JSON.parse(`"${match[1]}"`));
    } catch {
      // A malformed chunk must not discard valid chunks from the same page.
    }
  }
  return chunks.join("\n");
}

export function extractJsonArray(text, propertyName) {
  const marker = `"${propertyName}":`;
  const markerIndex = text.indexOf(marker);
  if (markerIndex < 0) return [];
  const start = text.indexOf("[", markerIndex + marker.length);
  if (start < 0) return [];
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let index = start; index < text.length; index += 1) {
    const character = text[index];
    if (inString) {
      if (escaped) escaped = false;
      else if (character === "\\") escaped = true;
      else if (character === '"') inString = false;
      continue;
    }
    if (character === '"') inString = true;
    else if (character === "[") depth += 1;
    else if (character === "]") {
      depth -= 1;
      if (depth === 0) {
        try {
          return JSON.parse(text.slice(start, index + 1));
        } catch {
          return [];
        }
      }
    }
  }
  return [];
}
