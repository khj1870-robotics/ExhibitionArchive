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

There is no backend — Room remains the local source of truth, images/audio are copied into app-private storage, and JSON+ZIP export/import is the only data portability mechanism. The app is otherwise online only for one feature: pasting an exhibition page link on `ExhibitionCreateScreen` fetches that page and auto-fills the form — see `util/ExhibitionPageFetcher.kt` below. (Naver Search API-based name search existed briefly but was removed because it required a user-supplied API key; do not re-add key-dependent features without asking.)

## Build & test commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew test                   # run JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.example.exhibitionarchive.data.TagRulesTest"  # single test class
./gradlew testDebugUnitTest --tests "*.normalizedName_trimsAndLowercases"              # single test method
./gradlew connectedAndroidTest   # instrumented tests, requires a device/emulator
```

Requires JDK 17 and Android SDK 36 (compileSdk/targetSdk 36, minSdk 26). CI (`.github/workflows/build-apk.yml`) runs `./gradlew assembleDebug -PversionCode=${{ github.run_number }}` on pushes to `main` (and via manual `workflow_dispatch`) and uploads the debug APK artifact — there is no lint/test job in CI, so run `./gradlew test` locally before pushing.

There is no linter/formatter configured (no ktlint/detekt). Match the existing dense, single-line Compose style in `Screens.kt` when editing that file rather than reformatting it.

## 개발 워크플로우

기능 브랜치에서 개발 → PR 생성 → GitHub Actions로 APK 빌드 → 사용자가 실기기에서 직접 테스트 → 통과하면 main에 머지. 빌드는 트리거만 하고 완료 폴링은 하지 않는다(사용자가 직접 아티팩트를 내려받아 설치).

## Android APK 업데이트 설치 규칙

이 프로젝트는 실제 안드로이드 기기에 APK를 반복 설치하여 테스트한다. **모든 APK는 기존 설치본 위에 업데이트 설치가 가능해야 하고, 기존 데이터가 유지되어야 한다.**

### 절대 변경 금지

- `applicationId` (`com.example.exhibitionarchive`)
- 서명 keystore: **`app/debug.keystore`** (저장소에 커밋된 고정 키 — 실기기에 설치된 앱이 이 키로 서명되어 있다)
- key alias (`androiddebugkey`) / keystore 비밀번호 (`android`)

새 keystore를 임의로 생성하거나 서명 키를 교체하면 기존 설치본에 업데이트가 불가능해져 앱 삭제(=데이터 소실)가 강제된다. 절대 하지 말 것. 이 키는 공개 저장소에 있으므로 개인 테스트 전용이다 — 스토어 배포를 하게 되면 그 시점에 비공개 release 키로 전환해야 한다(그 1회는 재설치 필요).

### 빌드 규칙

- versionCode는 CI가 `-PversionCode=${{ github.run_number }}`로 주입해 빌드마다 자동 증가한다(`build.gradle.kts`의 `findProperty("versionCode")` 참조). 수동으로 고정값을 넣지 말 것.
- versionName은 기능 변경에 맞게 수동으로 올린다.
- 기존 앱 삭제를 요구하는 APK를 결과물로 제공하지 않는다.

### 빌드 전 검증

1. applicationId가 기존과 같은가?
2. `app/debug.keystore`로 서명되는가? (signingConfig 변경 없음)
3. versionCode가 이전 빌드보다 높아지는가? (CI 주입 경로가 살아있는가)

위 조건을 만족하지 않으면 APK를 만들지 말고 문제를 먼저 보고한다.

## Architecture

Standard Hilt + Room + single-Activity Jetpack Compose Navigation app, all in one `app` module under `app/src/main/java/com/example/exhibitionarchive/`:

- **`data/`** — persistence layer.
  - `Models.kt`: all Room `@Entity` classes and relation/DTO classes (`ExhibitionWithVisit`, `ArtworkCard`, `BackupPayload`), each also `@Serializable` for backup JSON. `VisitNoteEntity` stores one visit-mode photo or text memo per row.
  - `Dao.kt`: one `@Dao` interface per aggregate (`ExhibitionDao`, `VisitDao`, `ArtistDao`, `ArtworkDao`, `MediaDao`, `VisitNoteDao`, `TagDao`). DAOs return `Flow` for observed queries and expose `allNow()`/`insertAll()` pairs used only by backup export/import.
  - `AppDatabase.kt`: the single Room database (`version = 2`, `exportSchema = true`). `MIGRATION_1_2` adds `ArtworkEntity.sourceUrl` and the `visit_notes` table without deleting installed-app data.
  - `AppRepository.kt`: the only class that touches the DAOs from the UI/ViewModel side. Multi-table writes (`createExhibition`, `addArtwork`, `replaceFromBackup`) are wrapped in `db.withTransaction {}`. Add new cross-entity operations here rather than calling multiple DAOs from the ViewModel.
  - `DatabaseModule.kt`: Hilt `@Module` providing the singleton `AppDatabase`.
- **`ui/`** — one shared `AppViewModel` (`@HiltViewModel`) exposing repository `Flow`s as `stateIn(..., WhileSubscribed(5_000))` `StateFlow`s, plus a `_message`/`message` `StateFlow` used for one-shot snackbar errors (set the message, screen shows it via `LaunchedEffect` + `SnackbarHostState`, then calls `clearMessage()`). `Screens.kt` contains **all** Composable screens and `ExhibitionArchiveRoot`, which owns a single `NavHost` with string routes (`home`, `calendar`, `archive`, `settings`, `search`, `createExhibition`, `exhibition/{id}`, `artworkCreate/{exhibitionId}`, `visitMode/{exhibitionId}`) and a bottom `NavigationBar` shown only on the four top-level routes. There is no separate `navigation/` package — routes are defined inline in `ExhibitionArchiveRoot`.
- **`util/`** — `FileStore` copies picked gallery images into `filesDir/images/`, exposes `newImageFile()` for camera output, converts it to a FileProvider URI with `uriFor(file)`, and allocates paths in `filesDir/audio/` (returned paths are stored as `localPath`/`filePath` on entities — DB never stores content URIs). `AndroidManifest.xml` registers `${applicationId}.fileprovider` against `res/xml/file_paths.xml`; camera photos are written directly into the same private `images/` directory. `downloadImage(url)` saves a remotely fetched poster into that directory for the auto-import flow. `AudioRecorder` wraps `MediaRecorder` (M4A/AAC) and is connected to `VisitModeScreen`. `BackupManager` serializes/deserializes the entire DB as one `BackupPayload` via kotlinx.serialization into `data.json` inside a ZIP; import is destructive (`db.clearAllTables()` then reinsert — no merge). `ExhibitionPageFetcher` fetches a URL with Jsoup and extracts `ExhibitionImportInfo`: `schema.org`/`Event` JSON-LD first, then OpenGraph/meta tags, then a per-host patch step (`sema.seoul.go.kr`, `nowonarts.kr` — selectors verified against real saved HTML from each site) that overrides fields those sites' generic tags get wrong or miss; unknown hosts just get the generic result. Runs its I/O with `withContext(Dispatchers.IO)` (unlike `FileStore`/`BackupManager`'s dispatcher-less suspend functions) because network calls throw `NetworkOnMainThreadException` if left on the caller's default dispatcher. `SettingsStore` wraps a `datastore-preferences` (`Context.settingsDataStore`) `Flow<Float>` for the UI font-scale preference — plaintext DataStore is fine here since nothing sensitive is stored, unlike the removed Naver-key experiment below.

### Data model relationships

`ExhibitionEntity` 1—N `VisitEntity` (a visit record per viewing), 1—N `ArtworkEntity`, 1—N `VisitNoteEntity` (관람 모드의 사진 또는 텍스트 메모). `ArtworkEntity` N—1 `ArtistEntity` (nullable, `SET_NULL` on delete) and 1—N `ArtworkImageEntity`; 작품 하나에 여러 로컬 사진과 하나의 `sourceUrl`을 저장할 수 있다. `AudioRecordEntity` optionally links to an exhibition and/or artwork. Tags are shared many-to-many via `TagEntity` + `ExhibitionTagCrossRef`/`ArtworkTagCrossRef`, deduplicated by `normalizedName` (trimmed+lowercased, unique index) — always go through `TagDao.setExhibitionTags()` to add tags rather than inserting `TagEntity` directly, since it handles find-or-create and clearing old links.

### Adding a new entity/field

1. Add/modify the `@Entity` in `Models.kt` (keep it `@Serializable` and add the field to `BackupPayload` if it should survive backup/restore).
2. Register the entity in `AppDatabase.kt`'s `entities = [...]` list; bump `version` and supply a `Migration`. The app has an installed test base whose data must survive APK updates, so destructive migration is not allowed.
3. Add DAO methods in `Dao.kt` (Flow-returning for UI observation, suspend for one-shot writes; add `allNow()`/`insertAll()` if it must participate in backup).
4. Wire it through `AppRepository.kt`, then `AppViewModel.kt`, then a screen in `Screens.kt`.

## 제품 비전 & 로드맵

이 앱은 단순 전시 목록 앱이 아니라, **전시·작품·작가·감상을 서로 연결해서 쌓아가는 개인 전시 기록 시스템**이다. 새 기능을 설계할 때는 이 방향성(기록들 간의 연결)을 우선 고려한다.

### 현재 구현된 핵심 기능 (README 기준)

- 전시명, 관람일, 장소, 포스터 등록, 별점(5점)
- 한줄평과 상세 감상 기록
- 전시별 작품 사진, 작품명, 작가명, 개인 감상 저장
- 작가와 태그 기준으로 기록 분류
- 관람일 기반 달력 보기(요일 헤더 + 주 단위로 화면을 꽉 채우는 레이아웃, 좌우 스와이프로 월 이동)
- 전시·작품·작가 검색, 아카이브 화면 내 검색(전시/작가/태그 탭 각각 로컬 필터링), 탭 좌우 스와이프
- 전시 상세 화면에서 작품 빠르게 추가, 전시 등록 화면에서 작품을 같이 등록(임시 목록에 담았다가 전시 저장 시 일괄 등록)
- 작품 한 개에 카메라 촬영·갤러리 다중 선택으로 여러 사진 첨부, 사진별 선택 해제, 출처/설명 링크 저장
- 관람 모드 1차: 촬영, 갤러리 다중 사진 첨부, 빠른 텍스트 메모, 음성 녹음·재생
- 포스터·작품 이미지 탭하면 핀치 줌으로 확대(`ZoomableImageDialog`)
- 설정 화면에서 글자 크기 조절(작게/보통/크게, `SettingsStore` DataStore에 저장, 앱 전역 `LocalDensity`로 적용)
- 로컬 데이터베이스(Room) 저장, 사진 앱 내부 보관
- JSON+ZIP 전체 백업 및 복원 (교체 복원만 지원, 병합 복원은 미구현)

### 외부 연동 자동 기록 (부분 구현됨)

`ExhibitionCreateScreen`에서 전시 링크를 붙여넣으면(범용 OG 메타태그 + `schema.org`/`Event` JSON-LD 파싱, 그리고 `sema.seoul.go.kr`/`nowonarts.kr` 전용 패치) 제목/설명/포스터/장소/기간/공식 링크가 자동으로 채워지는 기능이 구현됨. 다만:
- **이름으로 온라인 검색하는 기능은 없다** — 한때 네이버 검색 API로 구현했었지만, 사용자가 API 키를 직접 발급·입력해야만 동작하는 구조라 실사용성이 떨어져 제거했다(2번째 세션에서 추가, 3번째 세션에서 제거). **키 발급을 요구하지 않는 대안이 없다면 이 방향의 기능을 다시 추가하지 말 것** — 필요하면 먼저 사용자와 상의.
- **사이트별 정밀 파싱은 sema.seoul.go.kr, nowonarts.kr 2곳만 구현됨** — 그 외 사이트는 범용 OG/JSON-LD로만 처리되고, 못 찾은 필드(장소·기간 등)는 빈칸으로 남아 사용자가 직접 입력해야 함. 새 사이트를 추가하려면 반드시 그 사이트의 실제 HTML(사용자가 저장해서 제공)로 셀렉터를 검증한 뒤 `ExhibitionPageFetcher`에 패치 함수를 추가할 것 — 확인 없이 셀렉터를 추측해서 넣지 말 것.
- **작품 목록 자동 추출은 불가능 판정** — 확인한 `sema.seoul.go.kr`/`nowonarts.kr` 실제 HTML에는 개별 작품 목록이 구조화되어 있지 않고 SeMA도 "작품수: 6" 같은 숫자만 제공한다. 자동 추출 대신 기록모드에서 팜플렛·설명카드를 촬영하거나 갤러리에서 여러 장 선택하고 출처 URL을 붙이는 수동 첨부 방식으로 대체했다.

### 향후 구현하고 싶은 기능 (미구현, 로드맵)

- **전시 팜플렛 사진 촬영 자동 인식**: 카메라로 전시 팜플렛을 촬영하면 위 링크 가져오기와 동일하게 전시 정보가 자동으로 입력되는 기능 (OCR/이미지 인식 기반, 아이디어만 기록 — 미구현)
- **관람 기능 (GitHub 이슈 #5)**: 1차(촬영/사진 첨부/텍스트 메모/음성 녹음) 완료. 다음 단계는 이미지 위 그리기 주석과 안드로이드 기본 `SpeechRecognizer` 기반 실시간 STT.
- **전시 정보 자동화 후속 (GitHub 이슈 #4)**: 작품 목록 자동 불러오기는 구조화된 원본 데이터가 없어 중단하고 다중 사진/출처 링크 수동 첨부로 대체. 가져온 내용 필터링 개선과 자동 채움/기존 입력값 병렬 표시는 남아 있음.
- **사진 코멘트**: 한 전시 스레드 안에서 여러 사진 각각에 코멘트를 달 수 있는 기능
- **이미지 주석**: 작품 이미지 위에 직접 그림이나 글로 메모를 남기는 기능
- **3D 큐레이션**: 기록된 작품이나 검색 가능한 모든 작품을 가지고 3D 큐브 공간에서 직접 큐레이팅해보는 기능
- **성능 최적화**: 앱 로딩 및 사용 중 렉이 최대한 없도록 최적화
- 작품 상세 수정/삭제 화면, 백업에 이미지·음성 바이너리 포함, 병합 복원, DAO/UI 자동화 테스트 확대, 화면 디자인 다듬기 (README "아직 보완할 부분"에 기재된 기존 항목)

GitHub 이슈로 백로그를 관리한다(`gh issue list` 또는 저장소 Issues 탭) — 새 작업을 시작하기 전에 열린 이슈를 확인할 것.

## Conventions

- All persisted timestamps (`createdAt`/`updatedAt`) are `Long` epoch millis; visit/exhibition dates (`visitedAt`, `startDate`, `endDate`) are ISO `YYYY-MM-DD` strings, parsed/formatted via `java.time.LocalDate`.
- File paths on entities (`posterPath`, `localPath`, `filePath`) are absolute paths into app-private storage produced by `FileStore`, not content URIs.
- ViewModel methods that write data take an `onDone`/`onDone(id)` callback for navigation and report failures through `_message` rather than throwing — follow this pattern for new mutations instead of exposing exceptions to the UI.
- UI strings, comments, and README are in Korean; keep new user-facing strings in Korean for consistency.
