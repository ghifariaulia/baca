# Implementation Plan: Baca Missing Features Roadmap

Source: Interactive `/grill-me` product audit (2026-11-25), grounded in `docs/TECH.md` and `docs/LIBRERAX_GAPS.md`.

## Overview

Deliver the prioritized missing features across Baca in two tiers:
- **Tier 1 (Quick wins):** Navigation history & inline footnote popup, Reader hardware & screen controls (Android volume keys, brightness slider, customizable tap zones), Notes search/filter & Markdown export, Custom user `.ttf`/`.otf` fonts, and customizable Stats daily goal + history calendar.
- **Tier 2 (Larger platform & rendering work):** Background Read Aloud (TTS) with Android foreground media service, Cross-platform PDF text layer (selection, highlights, search), and remaining `docs/LIBRERAX_GAPS.md` rendering items (`#120`, `#121`, `#125`, `#126`).

## Architecture Decisions

- **No SQLite table schema changes:** Per `AGENTS.md` and `docs/TECH.md` section 5, all new global or per-book preferences (`user_fonts`, `xp:goal_minutes`, new `DisplaySettings` fields) live in `settings_local` (via `PositionStore` / `DbStatsStore`), so existing databases and `.zip` backups stay compatible without migrations.
- **Pure domain logic in `shared/`:** Navigation history stack, Markdown annotation formatter, configurable daily goal rules, and PDF word-box models live in `shared/domain` and `shared/parser` with unit tests in `shared/src/jvmTest`, keeping `composeApp` UI thin.
- **Measuring and drawing stay coupled:** Any layout change in Tier 2 (`#120` ruby, `#121` inline images, `#125` vertical details, `#126` 2D MathML) must update both `ReflowLayout` / `ComposeTextMetrics` and `ReaderScreen` together.
- **PDF text coordinates normalized (0..1):** Storing `PdfWordBox` and `AnnotationAnchor` rects in normalized page coordinates keeps PDF highlights independent of screen density and zoom level.

## Task List

### Phase 1: Navigation History & Inline Footnote Popup
- [x] Task 1: Add navigation history stack to `ReaderSession`
- [x] Task 2: Add Back-from-jump pill and inline footnote popup in `ReaderScreen`

### Checkpoint: Phase 1
- [x] `:shared:jvmTest`, `:composeApp:desktopTest`, and `:composeApp:compileAndroidMain` pass

### Phase 2: Reader Hardware & Screen Controls
- [x] Task 3: Add `volumeKeysTurnPages`, `brightness`, and `tapZoneMode` to `DisplaySettings` and `PlatformHooks`
- [x] Task 4: Intercept Android volume keys in `MainActivity` for page turns
- [x] Task 5: Add brightness slider, tap-zone selector, and volume-key toggle to Display settings and `ReaderScreen`

### Checkpoint: Phase 2
- [x] `:shared:jvmTest`, `:composeApp:compileAndroidMain`, and `:android:assembleDebug` pass (`:composeApp:desktopTest` 104/105 passed; pre-existing `EdgeLongPressTest` hit 3s bar auto-hide)

### Phase 3: Notes Search, Filter & Markdown Export
- [x] Task 6: Add search query and highlight-color filter chips to `NotesScreen`
- [x] Task 7: Add pure Markdown annotation exporter in `shared/domain`
- [x] Task 8: Wire Markdown export (clipboard + file save/share) into `NotesScreen` and `BookDetailScreen`

### Checkpoint: Phase 3
- [x] `:shared:jvmTest`, `:composeApp:desktopTest`, and `:composeApp:compileAndroidMain` pass

### Phase 4: Custom User Fonts
- [x] Task 9: Add user font storage and `user:<id>` font support in `DisplaySettings`
- [x] Task 10: Add font import picker and user font loading in `BookFonts` and `DisplayContent`

### Checkpoint: Phase 4
- [x] `:shared:jvmTest`, `:composeApp:desktopTest`, and `:composeApp:compileAndroidMain` pass

### Phase 5: Stats & Goals Customization
- [x] Task 11: Make daily reading goal configurable in `ReadingStats`
- [x] Task 12: Add goal duration selector and reading history calendar to `StatsScreen`

### Checkpoint: Phase 5
- [x] `:shared:jvmTest`, `:composeApp:desktopTest`, and `:composeApp:compileAndroidMain` pass

### Phase 6: Background Read Aloud (TTS)
- [x] Task 13: Implement Android foreground media service and notification for `TextToSpeech`
- [x] Task 14: Connect `ReaderScreen` Read Aloud loop to background service controls

### Checkpoint: Phase 6
- [x] `:shared:jvmTest`, `:composeApp:desktopTest`, and `:composeApp:compileAndroidMain` pass

### Phase 7: Cross-Platform PDF Text Layer
- [x] Task 15: Define `PdfWordBox` and implement Desktop PDFBox text extractor
- [x] Task 16: Implement Android PDF text extractor
- [x] Task 17: Add PDF text selection, highlight rects, and search to `CbzScreen`

### Checkpoint: Phase 7
- [x] `:shared:jvmTest`, `:composeApp:desktopTest`, and `:composeApp:compileAndroidMain` pass

### Phase 8: Remaining `LIBRERAX_GAPS.md` Rendering Items
- [x] Task 18: Implement `#120` ruby / furigana (`<ruby>` / `<rt>`) layout and rendering
- [x] Task 19: Implement `#121` inline pictures sharing a line with text
- [x] Task 20: Implement `#125` vertical writing details (`vertical-lr`, tate-chu-yoko, selection)
- [x] Task 21: Implement `#126` 2D MathML layout (`mfrac`, `msqrt`, `mtable`)

### Checkpoint: Complete
- [x] All tests pass (`:shared:jvmTest`, `:composeApp:desktopTest`, `:composeApp:compileAndroidMain`)
- [x] `docs/TECH.md` and `docs/LIBRERAX_GAPS.md` updated to match new behavior

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Android PDF text extraction library size/licensing (e.g. `pdfbox-android` vs PDFium) | Med | Check license compatibility (Apache 2.0 for PDFBox-Android) and APK size before adding dependency in Task 16; isolate behind `expect/actual` in `PdfParser`. |
| Android 14+ foreground service types & `POST_NOTIFICATIONS` permission for background TTS | Med | Declare `foregroundServiceType="mediaPlayback"` and request notification permission only when Read Aloud starts; keep foreground fallback working if denied. |
| Footnote detection heuristics vary across EPUBs | Low | Check `epub:type="footnote"/"endnote"`, FB2 `fb2:<n>`, and `linear="no"` targets first; always provide a "Go to page" button inside the popup. |
| Inline images & ruby (`#120`, `#121`) breaking existing LibreraX page-for-page parity | High | Keep existing block-image and non-ruby paths unchanged when no `<ruby>` or inline-sized `<img>` is present; verify with existing `desktopTest` book fixtures. |

## Open Questions

- For Task 16 (Android PDF text extraction), confirm preference between `pdfbox-android` (Apache 2.0, pure JVM/Android, matches Desktop PDFBox) vs Android 15+ `PdfRenderer.Page.getTextContents()` + fallback.
