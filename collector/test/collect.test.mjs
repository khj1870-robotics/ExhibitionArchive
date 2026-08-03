import test from "node:test";
import assert from "node:assert/strict";
import { buildDataset } from "../src/collect.mjs";

const cachedItem = {
  sourceId: "blocked",
  sourceName: "차단된 사이트",
  title: "마지막 정상 전시",
  venue: "미술관",
  startDate: "2026-08-01",
  endDate: "2026-09-01",
  status: "current",
  imageUrl: null,
  detailUrl: "https://blocked.example/exhibition/1",
  description: null
};

test("수집 실패 시 해당 사이트의 마지막 정상 데이터를 유지한다", async () => {
  const previous = {
    sources: [{ id: "blocked", fetchedAt: "2026-08-01T00:00:00.000Z" }],
    exhibitions: [cachedItem]
  };
  const sourceList = [{ id: "blocked", name: "차단된 사이트", homepage: "https://blocked.example", collect: async () => { throw new Error("403"); } }];

  const dataset = await buildDataset(previous, new Date("2026-08-03T00:00:00.000Z"), sourceList);

  assert.deepEqual(dataset.exhibitions, [cachedItem]);
  assert.equal(dataset.sources[0].status, "stale");
  assert.equal(dataset.sources[0].fetchedAt, "2026-08-01T00:00:00.000Z");
});

test("이전 데이터가 없는 첫 실패는 오류 상태로 기록한다", async () => {
  const sourceList = [{ id: "empty", name: "빈 사이트", homepage: "https://empty.example", collect: async () => [] }];

  const dataset = await buildDataset(null, new Date("2026-08-03T00:00:00.000Z"), sourceList);

  assert.equal(dataset.exhibitions.length, 0);
  assert.equal(dataset.sources[0].status, "error");
});
