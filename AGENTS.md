# AGENTS.md

Guidelines for AI coding agents working in this repository. Follow these before writing or changing any code.

## Project

**Baca** is a cross-device e-reader for Android and desktop (JVM); iOS comes later. Shared logic and UI are written in Kotlin Multiplatform with Compose Multiplatform. Reading state syncs between devices as JSON files in a user-chosen folder.

`docs/TECH.md` is the design source of truth: formats, locators, sync rules, UI tokens. Read the relevant section before changing behavior, and update it when behavior changes.

| Module | Holds |
|---|---|
| `shared/` | Pure Kotlin: `domain/` (models, reader session, use-case logic), `parser/` (EPUB, FB2, TXT, CBZ), `render/` (pagination), `sync/` (DTOs, pure merge, sync boundary), `db/` (SQLDelight repositories) |
| `composeApp/` | Compose UI shared by Android and desktop: `ui/` screens, `reader/` reader screens and platform glue (`expect`/`actual`) |
| `android/` | Android app entry (`MainActivity`) |
| `desktop/` | Desktop app entry (`Main.kt`) |

Stack: Kotlin 2.4, Compose Multiplatform 1.12, SQLDelight 2.4, Okio, kotlinx.serialization, xmlutil. Versions live in `gradle/libs.versions.toml`.

## Skills

Always use these skills:

- `context7-mcp`
- `caveman`
- `antislop`
- `antislop-code`

If another skill is relevant to the task, use it too.

## Layer Responsibilities

- **UI (`composeApp`)**: rendering and UI state only. Call into `shared` for any decision about books, positions, annotations or sync.
- **Domain (`shared/domain`)**: ALL business logic. Pure: no IO, no Compose, no wall clock reads.
- **Repositories (`shared/db`, `Db*Repository`, `DbPositionStore`)**: database operations only, through SQLDelight queries in `shared/src/commonMain/sqldelight`.
- **Parsers (`shared/parser`)**: turn a book file into blocks and metadata. No database, no UI.
- **Sync (`shared/sync`)**: merge functions in `Merge.kt` are pure. `SyncBoundary` is the only place that does sync IO. Transports (`SyncTransport` actuals) only move bytes.
- **Platform glue (`androidMain` / `desktopMain`)**: platform APIs only (file pickers, SAF, clipboard, folders).

### ✅ Correct Flow

```
Compose screen → domain logic (shared) → repository → SQLDelight → DB
```

### ❌ Never Do

- Database queries or file IO inside a composable's body (do it in a coroutine on `Dispatchers.IO`, or in `shared`)
- Business logic in a repository, parser or transport
- New logic piled into `composeApp/.../App.kt`. It already orchestrates too much; put new orchestration in `shared`
- Reading, printing or committing secrets. `local.properties` is machine-specific and stays out of version control

## Toolchain

- Build with the Gradle wrapper only (`./gradlew`), JDK 17 or newer (code targets JVM 17). No Docker.
- Desktop data: `~/.baca/baca.db` and imported books in `~/.baca/books`.
- A running `./gradlew :desktop:run` holds the Gradle lock; other Gradle commands wait until the app window closes.

### Commands

```bash
./gradlew :shared:jvmTest                  # shared logic tests (fast)
./gradlew :composeApp:desktopTest          # Compose UI tests (headless)
./gradlew :composeApp:compileAndroidMain   # Android code compiles
./gradlew :desktop:run                     # run the desktop app
./gradlew :android:assembleDebug           # Android debug APK
```

Before calling a change done, run the first three.

## Project Rules

These come from bugs already fixed here; keep them true.

- **Books are keyed by `book_hash`** (SHA-256 of size plus first and last 64 KiB, TECH 5.1). Files of the same EPUB edition are linked by a work key (`workKeyOf`). A position or annotation made in a *different* file must be re-found by its quote (`findQuote`, `relinkEntry`), never by href, block index or offset.
- **Measuring and drawing must agree.** `ReflowLayout` paginates with `ComposeTextMetrics`, and `ReaderScreen` draws each line with `lineStyle`. Font, size, line height, indent and `LineHeightStyle` must match on both sides, or pages overflow or leave blank space. Change them together.
- **HTML whitespace collapses** in the EPUB parser; only `<br>` is a hard line break.
- **Sync writes only to the device's own folder** (`devices/<id>/`). Merges order by `(lamport, device_id)`. New fields in sync DTOs must be optional with defaults, so older app versions can still read the files.
- **No schema changes to existing tables.** Desktop has no migration path yet (`Schema.create` ignores "already exists"). Add new queries freely; for new per-book data, a `settings_local` key (like `progress:<hash>`, `work:<hash>`) avoids a migration. Adding real migrations is its own task.
- **Reader chrome follows the app theme; only the page area takes the reading theme.** Bars, sheets and panels use the Material color scheme; page text and background use `ReaderThemes` colors explicitly, since those aren't in the color scheme.
- **Bundled fonts need their OFL license** in `docs/licenses/`.

## Testing

- `shared/src/jvmTest`: plain JVM tests. Fakes: `GridMetrics` (deterministic text measuring), `inMemoryDb()`, `MemoryTransport`, and tiny EPUBs from `parser/Fixtures.kt`.
- `composeApp/src/desktopTest`: Compose UI tests with `createComposeRule`; `books/frankenstein.epub` is the real-book fixture. Test density is 1, so 1dp equals 1px.
- Fix a bug by first writing a test that fails, then make it pass. If you can, confirm the test fails without the fix.
- Test behavior (what the user sees or what gets stored), not implementation details.
- Rendering problems: capture the screen (`captureToImage`) and look at it before guessing.

## Core Principles

### KISS: Keep It Simple, Stupid

Choose the simplest solution that works.

- Prefer plain functions and straightforward control flow over clever abstractions.
- If a solution needs a long explanation, simplify it.
- Use the standard library and existing project dependencies before adding new ones.
- Keep functions small and single-purpose. If a function does two things, split it.
- Avoid deep nesting; use early returns and guard clauses.
- Readable beats clever. Write code the next person understands in one pass.

### DRY: Don't Repeat Yourself

Every piece of knowledge should have one authoritative place.

- Before writing new code, search the codebase for an existing helper, util, or pattern that does the job.
- Extract shared logic once it appears a **third time** (rule of three). Two similar blocks are not yet a pattern.
- Centralize constants, config values, and validation rules. No magic numbers or strings scattered across files.
- Share types and schemas instead of redefining them.
- DRY applies to knowledge, not text. Code that looks alike but changes for different reasons should stay separate.

### YAGNI: You Aren't Gonna Need It

Build only what is required right now.

- Implement what the task asks for. No speculative features, options, or extension points.
- No unused parameters, flags, config keys, or "future-proof" abstractions.
- Don't add interfaces, base classes, or plugin systems until there are at least two real implementations.
- Don't add dependencies for a single small use.
- Delete dead code instead of commenting it out.

## How the Principles Interact

When principles conflict, resolve in this order:

1. **YAGNI** first: don't build it if it isn't needed.
2. **KISS** second: if it is needed, build the simplest version.
3. **DRY** last: deduplicate only when it makes the code simpler. A wrong abstraction costs more than a little duplication.

## Working Rules

- **Make the smallest change that solves the problem.** Don't refactor unrelated code in the same change.
- **Match existing conventions** (naming, structure, formatting, error handling) over personal preference.
- **Read before writing.** Understand the surrounding code and existing patterns first.
- **Don't invent requirements.** If something is ambiguous and the answer changes the implementation, ask. Otherwise state your assumption and proceed.
- **Handle errors where they can be acted on.** No empty catch blocks, no swallowed exceptions. Avoid `runCatching { … }.getOrNull()` that hides a failure the user should hear about.
- **Comments explain why, not what.** Good naming should cover the what.
- **Tests:** add or update tests for behavior you change.
- **No drive-by changes.** Don't reformat files, rename things, or upgrade dependencies unless asked.
- **Destructive actions need a yes.** Ask before deleting user data (`baca.db`, `~/.baca`) or making mass edits; take a backup first if the folder isn't under version control.

## Before Submitting a Change

Check each item:

- [ ] Does it do only what was asked? (YAGNI)
- [ ] Is there a simpler way to write it? (KISS)
- [ ] Did I reuse existing code instead of duplicating it? (DRY)
- [ ] Did I remove unused code, imports, and variables I introduced?
- [ ] Do `:shared:jvmTest`, `:composeApp:desktopTest` and `:composeApp:compileAndroidMain` pass, and are new behaviors covered?
- [ ] Did I update `docs/TECH.md` if behavior changed?
- [ ] Is the diff small and easy to review?

## Anti-Patterns to Avoid

| Anti-pattern | Violates | Instead |
|---|---|---|
| Generic "manager/factory/handler" layers for one use case | KISS, YAGNI | Write the direct function |
| Config option nobody requested | YAGNI | Hardcode until a second case exists |
| Copy-pasting a block to tweak one line | DRY | Parameterize or extract |
| Abstracting after seeing the pattern only once | YAGNI, DRY | Wait for the third occurrence |
| Merging unrelated code into one helper to avoid repetition | KISS | Keep them separate |
| Leaving commented-out code | YAGNI | Delete it; version control remembers |
