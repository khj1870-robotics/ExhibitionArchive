import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { dedupe } from "./lib.mjs";
import { sources } from "./sources.mjs";
import { closeBrowser } from "./browser.mjs";

function argument(name, fallback) {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : fallback;
}

async function readPrevious(path) {
  if (!path) return null;
  try {
    return JSON.parse(await readFile(resolve(path), "utf8"));
  } catch {
    return null;
  }
}

export async function buildDataset(previous = null, now = new Date(), sourceList = sources) {
  const previousItems = previous?.exhibitions ?? [];
  const settled = await Promise.allSettled(sourceList.map(async (source) => {
    const items = dedupe(await source.collect());
    if (!items.length) throw new Error("전시 목록을 찾지 못했습니다.");
    return items;
  }));

  const statuses = [];
  const items = [];
  settled.forEach((result, index) => {
    const source = sourceList[index];
    const fetchedAt = now.toISOString();
    if (result.status === "fulfilled") {
      items.push(...result.value);
      statuses.push({ id: source.id, name: source.name, homepage: source.homepage, status: "ok", count: result.value.length, fetchedAt });
      return;
    }
    const cached = previousItems.filter((item) => item.sourceId === source.id);
    items.push(...cached);
    statuses.push({
      id: source.id,
      name: source.name,
      homepage: source.homepage,
      status: cached.length ? "stale" : "error",
      count: cached.length,
      fetchedAt: previous?.sources?.find((item) => item.id === source.id)?.fetchedAt ?? null,
      error: String(result.reason?.message ?? result.reason).slice(0, 300)
    });
  });

  return {
    schemaVersion: 1,
    generatedAt: now.toISOString(),
    sources: statuses,
    exhibitions: dedupe(items)
  };
}

async function main() {
  const defaultOutput = resolve(dirname(fileURLToPath(import.meta.url)), "../output/exhibitions.json");
  const output = resolve(argument("--output", defaultOutput));
  const previous = await readPrevious(argument("--previous", ""));
  const dataset = await buildDataset(previous);
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(dataset, null, 2)}\n`);
  const ok = dataset.sources.filter((source) => source.status === "ok").length;
  process.stdout.write(`수집 완료: ${ok}/${dataset.sources.length}개 사이트, ${dataset.exhibitions.length}개 전시\n`);
  if (!dataset.exhibitions.length) throw new Error("게시할 전시정보가 없습니다.");
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  main()
    .catch((error) => {
      process.stderr.write(`${error.stack ?? error}\n`);
      process.exitCode = 1;
    })
    .finally(closeBrowser);
}
