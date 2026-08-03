# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Behavioral Guidelines

Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

---

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

**항상 한국말로 답변할 것.**

## Project overview

전시기록 (Exhibition Archive) is a single-module Android MVP app for logging exhibition visits: visit dates, posters, one-line and detailed reviews, artworks/photos, artists, and tags, all stored locally. Package: `com.example.exhibitionarchive`. Source and UI strings are in Korean.

There is no personal-data backend — Room remains the local source of truth, images/audio are copied into app-private storage, and JSON+ZIP export/import is the only data portability mechanism. A GitHub Actions collector under `collector/` publishes a read-only public exhibition dataset every six hours. The app downloads that dataset for local name search; user queries and records are never sent to the collector. Link import still fetches the supplied public page directly — see `util/ExhibitionPageFetcher.kt` and `util/ExhibitionSearchApi.kt` below.

## Build & test commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew test                   # run JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.example.exhibitionarchive.data.TagRulesTest"  # single test class
./gradlew testDebugUnitTest --tests "*.normalizedName_trimsAndLowercases"              # single test method
./gradlew connectedAndroidTest   # instrumented tests, requires a device/emulator
```

Requires JDK 17 and Android SDK 36 (compileSdk/targetSdk 36, minSdk 26). CI (`.github/workflows/build-apk.yml`) runs `./gradlew testDebugUnitTest lintDebug assembleDebug` for pull requests and pushes to `main`, then uploads the debug APK artifact.

There is no linter/formatter configured (no ktlint/detekt). Match the existing dense, single-line Compose style in `Screens.kt` when editing that file rather than reformatting it.

## Architecture

Standard Hilt + Room + single-Activity Jetpack Compose Navigation app, all in one `app` module under `app/src/main/java/com/example/exhibitionarchive/`:

- **`data/`** — persistence layer.
  - `Models.kt`: all Room `@Entity` classes and relation/DTO classes (`ExhibitionWithVisit`, `ArtworkCard`, `BackupPayload`), each also `@Serializable` for backup JSON.
  - `Dao.kt`: one `@Dao` interface per aggregate (`ExhibitionDao`, `VisitDao`, `ArtistDao`, `ArtworkDao`, `MediaDao`, `TagDao`). DAOs return `Flow` for observed queries and expose `allNow()`/`insertAll()` pairs used only by backup export/import.
  - `AppDatabase.kt`: the single Room database (`version = 2`, `exportSchema = true`) with a tested `v1 → v2` migration and exported schemas under `app/schemas`.
  - `AppRepository.kt`: the only class that touches the DAOs from the UI/ViewModel side. Multi-table writes (`createExhibition`, `addArtwork`, `replaceFromBackup`) are wrapped in `db.withTransaction {}`. Add new cross-entity operations here rather than calling multiple DAOs from the ViewModel.
  - `DatabaseModule.kt`: Hilt `@Module` providing the singleton `AppDatabase`.
- **`ui/`** — one shared `AppViewModel` (`@HiltViewModel`) exposing repository `Flow`s as `stateIn(..., WhileSubscribed(5_000))` `StateFlow`s, plus a `_message`/`message` `StateFlow` used for one-shot snackbar errors (set the message, screen shows it via `LaunchedEffect` + `SnackbarHostState`, then calls `clearMessage()`). `Screens.kt` contains **all** Composable screens and `ExhibitionArchiveRoot`, which owns a single `NavHost` with string routes for home/calendar/archive/settings/search, exhibition create/detail/edit, and artwork create/detail/edit. There is no separate `navigation/` package — routes are defined inline in `ExhibitionArchiveRoot`.
- **`util/`** — `FileStore` copies picked gallery images into `filesDir/images/` and allocates paths in `filesDir/audio/` (returned paths are stored as `localPath`/`filePath` on entities — DB never stores content URIs); `downloadImage(url)` saves a remotely fetched poster into the same `images/` dir for the auto-import flow. `AudioRecorder` wraps `MediaRecorder` (M4A/AAC) but is not yet wired into any screen (see README "아직 보완할 부분"). `BackupManager` serializes/deserializes the entire DB as one `BackupPayload` via kotlinx.serialization into `data.json` inside a ZIP; import is destructive (`db.clearAllTables()` then reinsert — no merge). `ExhibitionPageFetcher` extracts `schema.org`/`Event` JSON-LD and OpenGraph fields, then applies verified site-specific handling for SeMA, Nowon Arts, and Interpark ticket pages. `ExhibitionSearchApi` downloads the collector's unified JSON dataset, caches it for ten minutes, and matches title/venue/source/description locally. It does not transmit the search query.

- **`collector/`** — Node.js collector with one adapter per public source (Art-map, Neolook, Artbava, MMCA, Daelim Museum, Leeum, SeMA). It prefers lightweight HTTP/API requests and uses Playwright/Chromium only as a fallback for blocked, certificate-broken, or client-rendered lists. `.github/workflows/collect-exhibitions.yml` tests the adapters and publishes `exhibitions.json` to the stable `exhibition-data` GitHub Release every six hours. A source failure must not erase other sources or its last successful cached entries.

### Data model relationships

`ExhibitionEntity` 1—N `VisitEntity` (a visit record per viewing), 1—N `ArtworkEntity`. `ArtworkEntity` N—1 `ArtistEntity` (nullable, `SET_NULL` on delete) and 1—N `ArtworkImageEntity`. `AudioRecordEntity` optionally links to an exhibition and/or artwork. Tags are shared many-to-many via `TagEntity` + `ExhibitionTagCrossRef`/`ArtworkTagCrossRef`, deduplicated by `normalizedName` (trimmed+lowercased, unique index) — always go through `TagDao.setExhibitionTags()` to add tags rather than inserting `TagEntity` directly, since it handles find-or-create and clearing old links.

### Adding a new entity/field

1. Add/modify the `@Entity` in `Models.kt` (keep it `@Serializable` and add the field to `BackupPayload` if it should survive backup/restore).
2. Register the entity in `AppDatabase.kt`'s `entities = [...]` list; bump `version`, add a `Migration`, export the new schema, and extend the migration test. The current database version is 2.
3. Add DAO methods in `Dao.kt` (Flow-returning for UI observation, suspend for one-shot writes; add `allNow()`/`insertAll()` if it must participate in backup).
4. Wire it through `AppRepository.kt`, then `AppViewModel.kt`, then a screen in `Screens.kt`.

## 제품 비전 & 로드맵

이 앱은 단순 전시 목록 앱이 아니라, **전시·작품·작가·감상을 서로 연결해서 쌓아가는 개인 전시 기록 시스템**이다. 새 기능을 설계할 때는 이 방향성(기록들 간의 연결)을 우선 고려한다.

### 현재 구현된 핵심 기능 (README 기준)

- 전시명, 관람일, 장소, 포스터 등록
- 한줄평과 상세 감상 기록
- 전시별 작품 사진, 작품명, 작가명, 개인 감상 저장
- 작가와 태그 기준으로 기록 분류
- 관람일 기반 달력 보기
- 전시·작품·작가 검색
- 전시 상세 화면에서 작품 빠르게 추가
- 로컬 데이터베이스(Room) 저장, 사진 앱 내부 보관
- JSON+ZIP 전체 백업 및 복원 (교체 복원만 지원, 병합 복원은 미구현)
- 음성 녹음용 `AudioRecorder` 코드는 있으나 실제 녹음·재생 UI는 아직 연결되지 않음

### 외부 연동 자동 기록 (부분 구현됨)

`ExhibitionCreateScreen`에서 전시 링크 붙여넣기(범용 OG 메타태그 + `schema.org`/`Event` JSON-LD 파싱) 또는 7개 사이트 공개 목록의 전시명 검색 → 검색 결과 선택 시 제목/포스터/장소/기간/공식 링크가 자동으로 채워지는 기능은 구현됨. 다만:
- **일부 사이트만 정밀 파싱** — 서울시립미술관, 노원문화재단, 인터파크 티켓은 전용 처리가 있고, 그 밖의 사이트는 범용 메타데이터가 없는 필드가 빈칸으로 남을 수 있음.
- **작품 목록 자동 추출은 범위에서 제외** — 사이트마다 구조가 달라 범용 파싱으로 신뢰성 있게 뽑을 수 없어서, 작품은 지금처럼 전시 상세 화면에서 수동으로 추가.
- 전시명 검색은 6시간마다 갱신되는 통합 JSON을 내려받아 기기에서 수행한다. API 키가 필요 없고 검색어는 서버로 전송되지 않는다.

### 향후 구현하고 싶은 기능 (미구현, 로드맵)

- **전시 팜플렛 사진 촬영 자동 인식**: 카메라로 전시 팜플렛을 촬영하면 위 링크/검색 가져오기와 동일하게 전시 정보가 자동으로 입력되는 기능 (OCR/이미지 인식 기반, 아이디어만 기록 — 미구현)
- **사이트별 정밀 파싱**: 자주 쓰는 아카이빙 사이트(예: nowonarts.kr)의 실제 HTML 구조를 확인해 장소·기간·작품 목록까지 정확히 뽑는 전용 파서 추가
- **전시 추천 피드**: 통합 공개 전시 데이터의 진행/예정 전시를 앱에서 보여주고, 지역·기간·관심 태그와 기존 기록을 기기 안에서 비교해 추천하는 기능. 데이터 수집 기반은 구현됐고 추천 UI·점수화는 아직 미구현이다.
- **사진 코멘트**: 한 전시 스레드 안에서 여러 사진 각각에 코멘트를 달 수 있는 기능
- **이미지 주석**: 작품 이미지 위에 직접 그림이나 글로 메모를 남기는 기능
- **3D 큐레이션**: 기록된 작품이나 검색 가능한 모든 작품을 가지고 3D 큐브 공간에서 직접 큐레이팅해보는 기능
- **성능 최적화**: 앱 로딩 및 사용 중 렉이 최대한 없도록 최적화
- 카메라 직접 촬영, 작품 상세 수정/삭제 화면, 백업에 이미지·음성 바이너리 포함, 병합 복원, DAO/UI 자동화 테스트 확대, 화면 디자인 다듬기 (README "아직 보완할 부분"에 기재된 기존 항목)

## Conventions

- All persisted timestamps (`createdAt`/`updatedAt`) are `Long` epoch millis; visit/exhibition dates (`visitedAt`, `startDate`, `endDate`) are ISO `YYYY-MM-DD` strings, parsed/formatted via `java.time.LocalDate`.
- File paths on entities (`posterPath`, `localPath`, `filePath`) are absolute paths into app-private storage produced by `FileStore`, not content URIs.
- ViewModel methods that write data take an `onDone`/`onDone(id)` callback for navigation and report failures through `_message` rather than throwing — follow this pattern for new mutations instead of exposing exceptions to the UI.
- UI strings, comments, and README are in Korean; keep new user-facing strings in Korean for consistency.
