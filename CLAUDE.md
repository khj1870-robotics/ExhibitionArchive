# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

전시기록 (Exhibition Archive) is a single-module Android MVP app for logging exhibition visits: visit dates, posters, one-line and detailed reviews, artworks/photos, artists, and tags, all stored locally. Package: `com.example.exhibitionarchive`. Source and UI strings are in Korean.

There is no backend — the app is fully offline. Room is the source of truth, images/audio are copied into app-private storage, and JSON+ZIP export/import is the only data portability mechanism.

## Build & test commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew test                   # run JVM unit tests (app/src/test)
./gradlew testDebugUnitTest --tests "com.example.exhibitionarchive.data.TagRulesTest"  # single test class
./gradlew testDebugUnitTest --tests "*.normalizedName_trimsAndLowercases"              # single test method
./gradlew connectedAndroidTest   # instrumented tests, requires a device/emulator
```

Requires JDK 17 and Android SDK 36 (compileSdk/targetSdk 36, minSdk 26). CI (`.github/workflows/build-apk.yml`) runs `./gradlew assembleDebug` on pushes to `main` and uploads the debug APK artifact — there is no lint/test job in CI, so run `./gradlew test` locally before pushing.

There is no linter/formatter configured (no ktlint/detekt). Match the existing dense, single-line Compose style in `Screens.kt` when editing that file rather than reformatting it.

## Architecture

Standard Hilt + Room + single-Activity Jetpack Compose Navigation app, all in one `app` module under `app/src/main/java/com/example/exhibitionarchive/`:

- **`data/`** — persistence layer.
  - `Models.kt`: all Room `@Entity` classes and relation/DTO classes (`ExhibitionWithVisit`, `ArtworkCard`, `BackupPayload`), each also `@Serializable` for backup JSON.
  - `Dao.kt`: one `@Dao` interface per aggregate (`ExhibitionDao`, `VisitDao`, `ArtistDao`, `ArtworkDao`, `MediaDao`, `TagDao`). DAOs return `Flow` for observed queries and expose `allNow()`/`insertAll()` pairs used only by backup export/import.
  - `AppDatabase.kt`: the single Room database (`version = 1`, `exportSchema = true` — schema JSON should be added under `app/schemas` if a migration is ever introduced).
  - `AppRepository.kt`: the only class that touches the DAOs from the UI/ViewModel side. Multi-table writes (`createExhibition`, `addArtwork`, `replaceFromBackup`) are wrapped in `db.withTransaction {}`. Add new cross-entity operations here rather than calling multiple DAOs from the ViewModel.
  - `DatabaseModule.kt`: Hilt `@Module` providing the singleton `AppDatabase`.
- **`ui/`** — one shared `AppViewModel` (`@HiltViewModel`) exposing repository `Flow`s as `stateIn(..., WhileSubscribed(5_000))` `StateFlow`s, plus a `_message`/`message` `StateFlow` used for one-shot snackbar errors (set the message, screen shows it via `LaunchedEffect` + `SnackbarHostState`, then calls `clearMessage()`). `Screens.kt` contains **all** Composable screens and `ExhibitionArchiveRoot`, which owns a single `NavHost` with string routes (`home`, `calendar`, `archive`, `settings`, `search`, `createExhibition`, `exhibition/{id}`, `artworkCreate/{exhibitionId}`) and a bottom `NavigationBar` shown only on the four top-level routes. There is no separate `navigation/` package — routes are defined inline in `ExhibitionArchiveRoot`.
- **`util/`** — `FileStore` copies picked gallery images into `filesDir/images/` and allocates paths in `filesDir/audio/` (returned paths are stored as `localPath`/`filePath` on entities — DB never stores content URIs). `AudioRecorder` wraps `MediaRecorder` (M4A/AAC) but is not yet wired into any screen (see README "아직 보완할 부분"). `BackupManager` serializes/deserializes the entire DB as one `BackupPayload` via kotlinx.serialization into `data.json` inside a ZIP; import is destructive (`db.clearAllTables()` then reinsert — no merge).

### Data model relationships

`ExhibitionEntity` 1—N `VisitEntity` (a visit record per viewing), 1—N `ArtworkEntity`. `ArtworkEntity` N—1 `ArtistEntity` (nullable, `SET_NULL` on delete) and 1—N `ArtworkImageEntity`. `AudioRecordEntity` optionally links to an exhibition and/or artwork. Tags are shared many-to-many via `TagEntity` + `ExhibitionTagCrossRef`/`ArtworkTagCrossRef`, deduplicated by `normalizedName` (trimmed+lowercased, unique index) — always go through `TagDao.setExhibitionTags()` to add tags rather than inserting `TagEntity` directly, since it handles find-or-create and clearing old links.

### Adding a new entity/field

1. Add/modify the `@Entity` in `Models.kt` (keep it `@Serializable` and add the field to `BackupPayload` if it should survive backup/restore).
2. Register the entity in `AppDatabase.kt`'s `entities = [...]` list; bump `version` and supply a `Migration` if the app has shipped (currently `version = 1`, no migrations exist yet — schema-breaking changes are done by editing entities directly since there's no installed base).
3. Add DAO methods in `Dao.kt` (Flow-returning for UI observation, suspend for one-shot writes; add `allNow()`/`insertAll()` if it must participate in backup).
4. Wire it through `AppRepository.kt`, then `AppViewModel.kt`, then a screen in `Screens.kt`.

## Conventions

- All persisted timestamps (`createdAt`/`updatedAt`) are `Long` epoch millis; visit/exhibition dates (`visitedAt`, `startDate`, `endDate`) are ISO `YYYY-MM-DD` strings, parsed/formatted via `java.time.LocalDate`.
- File paths on entities (`posterPath`, `localPath`, `filePath`) are absolute paths into app-private storage produced by `FileStore`, not content URIs.
- ViewModel methods that write data take an `onDone`/`onDone(id)` callback for navigation and report failures through `_message` rather than throwing — follow this pattern for new mutations instead of exposing exceptions to the UI.
- UI strings, comments, and README are in Korean; keep new user-facing strings in Korean for consistency.
