# Baca Missing Features — Task List

---

## Task 1: Add navigation history stack to `ReaderSession`

**Description:** Add an in-memory jump-history stack of `Locator`s to `ReaderSession` so jumping via TOC, internal links, or search records the previous reading position and allows stepping back.

**Acceptance criteria:**
- [x] `ReaderSession` exposes `canGoBack: Boolean` and `suspend fun goBack(): Boolean`
- [x] Jumping to a chapter/locator pushes the prior locator onto the history stack (capped at a reasonable depth, e.g. 32), while normal page turns do not push
- [x] Calling `goBack()` restores the previous locator, saves it, and fires `onChange`

**Verification:**
- [x] Tests pass: `./gradlew :shared:jvmTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/domain/reader/ReaderSession.kt`
- `shared/src/jvmTest/kotlin/com/baca/shared/domain/reader/ReaderSessionTest.kt`

**Estimated scope:** Small (2 files)

---

## Task 2: Add Back-from-jump pill and inline footnote popup in `ReaderScreen`

**Description:** Show a dismissible "Back" pill in `ReaderScreen` when `session.canGoBack` is true after a jump, and intercept footnote link taps (`epub:type="footnote"`/`endnote`, FB2 `fb2:<n>`, or `linear="no"` chapters) to display the target block(s) in an inline bottom sheet/popover with a "Go to page" action.

**Acceptance criteria:**
- [x] Tapping a footnote link opens an inline popup/sheet showing the footnote text without leaving the current page
- [x] Tapping a non-footnote internal link or TOC entry jumps and shows a "Back" pill that returns to the prior position via `session.goBack()`
- [x] Popup includes a "Go to page" button for long footnotes

**Verification:**
- [x] Tests pass: `./gradlew :composeApp:desktopTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`
- [ ] Manual check: Tap a footnote link and a TOC entry in a test EPUB/FB2 book

**Dependencies:** Task 1

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderScreen.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/parser/Parser.kt`
- `composeApp/src/desktopTest/kotlin/com/baca/app/ReaderNavigationTest.kt`

**Estimated scope:** Medium (3 files)

---

## Checkpoint: After Tasks 1-2
- [x] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [x] Footnote popup and Back-from-jump flow work end-to-end

---

## Task 3: Add `volumeKeysTurnPages`, `brightness`, and `tapZoneMode` to `DisplaySettings` and `PlatformHooks`

**Description:** Extend `DisplaySettings` with `volumeKeysTurnPages: Boolean = false`, `brightness: Float = -1f` (`-1f` = system default, `0f..1f` = manual), and `tapZoneMode: String = "standard"` (`standard` | `swapped` | `disabled`), and add an `expect` composable/hook to apply window brightness on Android (no-op on Desktop).

**Acceptance criteria:**
- [ ] `DisplaySettings` serializes/deserializes new fields with backward-compatible defaults for existing saved JSON
- [ ] Invalid `tapZoneMode` or out-of-range `brightness` fails validation in `DisplaySettings.init`
- [ ] `ApplyScreenBrightness(brightness)` updates Android window `screenBrightness` and restores system brightness when `< 0f` or leaving the reader

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/domain/DisplaySettings.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/PlatformHooks.kt`
- `composeApp/src/androidMain/kotlin/com/baca/app/reader/PlatformHooks.android.kt`
- `composeApp/src/desktopMain/kotlin/com/baca/app/reader/PlatformHooks.desktop.kt`

**Estimated scope:** Medium (4 files)

---

## Task 4: Intercept Android volume keys in `MainActivity` for page turns

**Description:** Forward Android hardware `KEYCODE_VOLUME_UP` and `KEYCODE_VOLUME_DOWN` events from `MainActivity` to the active reader when `volumeKeysTurnPages` is enabled, turning previous/next page without triggering the system volume overlay.

**Acceptance criteria:**
- [ ] Pressing Volume Down turns forward and Volume Up turns backward when the reader is open and `volumeKeysTurnPages` is true
- [ ] When outside the reader or when `volumeKeysTurnPages` is false, volume keys behave normally
- [ ] Works in both `ReaderScreen` and `CbzScreen`

**Verification:**
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain :android:assembleDebug`
- [ ] Manual check: Verify on Android device/emulator with `volumeKeysTurnPages` toggled on and off

**Dependencies:** Task 3

**Files likely touched:**
- `android/src/main/kotlin/com/baca/android/MainActivity.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/App.kt`

**Estimated scope:** Small (2 files)

---

## Task 5: Add brightness slider, tap-zone selector, and volume-key toggle to Display settings and `ReaderScreen`

**Description:** Expose the brightness slider (with "Auto" reset), tap-zone mode selector (`Standard`, `Swapped`, `Off`), and Android volume-keys toggle in `DisplayContent.kt`, and wire `tapZoneMode` into `ReaderScreen` and `CbzScreen` tap handling.

**Acceptance criteria:**
- [ ] `DisplayContent` shows Tap zones selector on all platforms, and Brightness slider + Volume-key toggle on Android
- [ ] `Swapped` tap mode reverses left/right page-turn taps; `Off` restricts taps to toggling reader bars
- [ ] Brightness changes take effect immediately while reading

**Verification:**
- [ ] Tests pass: `./gradlew :composeApp:desktopTest`
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** Task 3

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/DisplayContent.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/CbzScreen.kt`

**Estimated scope:** Medium (3 files)

---

## Checkpoint: After Tasks 3-5
- [ ] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [ ] Tap zones, brightness, and Android volume-key navigation verified

---

## Task 6: Add search query and highlight-color filter chips to `NotesScreen`

**Description:** Add a search field and highlight-color filter chips to `NotesScreen` so users can filter annotations across all books by quote text, note text, book title, or highlight color.

**Acceptance criteria:**
- [ ] Typing in the search field filters displayed annotations case-insensitively by quote, note, or book title
- [ ] Selecting a highlight color chip filters to annotations of that color (combining with All/Highlights/With notes)
- [ ] Empty state message clarifies when no annotations match the active search/filter

**Verification:**
- [ ] Tests pass: `./gradlew :composeApp:desktopTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/ui/NotesScreen.kt`
- `composeApp/src/desktopTest/kotlin/com/baca/app/NotesScreenTest.kt`

**Estimated scope:** Small (2 files)

---

## Task 7: Add pure Markdown annotation exporter in `shared/domain`

**Description:** Implement a pure `formatAnnotationsMarkdown` helper in `shared/domain` that converts one or more books' annotations into structured Markdown grouped by book and chapter, with highlights as blockquotes and notes underneath.

**Acceptance criteria:**
- [ ] Output includes book title, author, chapter headings, quoted text as `> ...`, and user notes below each quote
- [ ] Deleted annotations and bookmarks are excluded (or bookmarks optionally omitted)
- [ ] Handles empty notes, multi-line quotes, and missing chapter names cleanly

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/domain/AnnotationExport.kt`
- `shared/src/jvmTest/kotlin/com/baca/shared/domain/AnnotationExportTest.kt`

**Estimated scope:** Small (2 files)

---

## Task 8: Wire Markdown export (clipboard + file save/share) into `NotesScreen` and `BookDetailScreen`

**Description:** Add "Export Markdown" actions to `NotesScreen` (all visible/all books) and `BookDetailScreen` (single book) offering both Copy to clipboard and Save/Share `.md` file via platform hooks.

**Acceptance criteria:**
- [ ] `NotesScreen` has an Export action that copies or saves/shares Markdown for all notes
- [ ] `BookDetailScreen` has an Export action (when the book has highlights/notes) for that book only
- [ ] Platform file save/share works on both Desktop (save dialog) and Android (share sheet / `ACTION_CREATE_DOCUMENT`)

**Verification:**
- [ ] Tests pass: `./gradlew :composeApp:desktopTest`
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** Task 7

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/ui/NotesScreen.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/ui/BookDetailScreen.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/BackupFiles.kt`

**Estimated scope:** Medium (3 files)

---

## Checkpoint: After Tasks 6-8
- [ ] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [ ] Notes search, color filter, and Markdown export work end-to-end

---

## Task 9: Add user font storage and `user:<id>` font support in `DisplaySettings`

**Description:** Add a `UserFont` model and store in `shared/domain` (persisted via `settings_local` key `user_fonts` + font files in app storage `fonts/`), and update `DisplaySettings.font` validation to accept `"user:<id>"`.

**Acceptance criteria:**
- [ ] `DisplaySettings` accepts `"serif"`, `"sans"`, `"mono"`, `"libron"`, and `"user:<id>"`
- [ ] User font metadata (`id`, `name`, `fileName`) is stored in `settings_local` without altering SQLite tables
- [ ] Deleting a user font that is currently active falls back gracefully to `"serif"`

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/domain/DisplaySettings.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/domain/UserFonts.kt`
- `shared/src/jvmTest/kotlin/com/baca/shared/domain/UserFontsTest.kt`

**Estimated scope:** Medium (3 files)

---

## Task 10: Add font import picker and user font loading in `BookFonts` and `DisplayContent`

**Description:** Allow users to import `.ttf` / `.otf` files via the file picker, load the selected `user:<id>` font into Compose `FontFamily` for both `ComposeTextMetrics` and `ReaderScreen`, and manage imported fonts in `DisplayContent`.

**Acceptance criteria:**
- [ ] Users can pick a `.ttf` or `.otf` file from `DisplayContent` (and Settings → Reading display) and see it in the font selector
- [ ] Selecting an imported font re-paginates and renders text using the exact same `FontFamily` in measurement and drawing
- [ ] Imported fonts can be removed from the selector

**Verification:**
- [ ] Tests pass: `./gradlew :composeApp:desktopTest`
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** Task 9

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/BookFonts.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/DisplayContent.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderScreen.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/FilePicker.kt`

**Estimated scope:** Medium (4 files)

---

## Checkpoint: After Tasks 9-10
- [ ] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [ ] Custom `.ttf`/`.otf` import, rendering, and removal verified

---

## Task 11: Make daily reading goal configurable in `ReadingStats`

**Description:** Replace the hardcoded 15-minute daily reading goal in `ReadingStats` with a configurable `xp:goal_minutes` setting stored in `settings_local` (default 15, range 5–180 minutes).

**Acceptance criteria:**
- [ ] `ReadingStats` reads and updates `goalMinutes` from `settings_local` (default 15)
- [ ] New reading days evaluate `goalMet` against the configured `goalMinutes * 60` seconds
- [ ] Previously completed days retain their recorded `goalMet` flag

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/domain/stats/ReadingStats.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/db/DbStatsStore.kt`
- `shared/src/jvmTest/kotlin/com/baca/shared/domain/stats/ReadingStatsTest.kt`

**Estimated scope:** Medium (3 files)

---

## Task 12: Add goal duration selector and reading history calendar to `StatsScreen`

**Description:** Add a daily goal minutes stepper/picker and a visual reading-activity calendar/heatmap (showing days read and goal-met days) to `StatsScreen`.

**Acceptance criteria:**
- [ ] User can adjust the daily goal (e.g. 5–120 min in 5-min steps) directly on `StatsScreen`
- [ ] Calendar/heatmap displays at least the last 30–90 days, distinguishing no-reading, partial reading, and goal-met days
- [ ] Selecting a day shows that day's minutes, pages, and XP

**Verification:**
- [ ] Tests pass: `./gradlew :composeApp:desktopTest`
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** Task 11

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/ui/StatsScreen.kt`
- `composeApp/src/desktopTest/kotlin/com/baca/app/StatsScreenTest.kt`

**Estimated scope:** Small (2 files)

---

## Checkpoint: After Tasks 11-12
- [ ] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [ ] Tier 1 complete — review before starting Tier 2

---

## Task 13: Implement Android foreground media service and notification for `TextToSpeech`

**Description:** Move Android `TextToSpeech` execution into a foreground service (`foregroundServiceType="mediaPlayback"`) with a media-style notification (Play/Pause/Stop actions) and audio focus handling so TTS continues when the screen is off or the app is backgrounded.

**Acceptance criteria:**
- [ ] `AndroidManifest.xml` declares `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` (requested when starting Read Aloud)
- [ ] TTS continues speaking when the app is backgrounded or the screen turns off
- [ ] Notification Pause/Resume/Stop actions control playback

**Verification:**
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain :android:assembleDebug`
- [ ] Manual check: Start Read Aloud on Android, lock screen, verify audio continues and notification Stop halts playback

**Dependencies:** None

**Files likely touched:**
- `android/src/main/AndroidManifest.xml`
- `composeApp/src/androidMain/kotlin/com/baca/app/reader/ReadAloudService.kt`
- `composeApp/src/androidMain/kotlin/com/baca/app/reader/Speech.android.kt`

**Estimated scope:** Medium (3 files)

---

## Task 14: Connect `ReaderScreen` Read Aloud loop to background service controls

**Description:** Update `Speech` and `ReaderScreen`'s Read Aloud coroutine so external Pause/Stop events from the Android notification cleanly update the UI state and current sentence highlight, and page advances stay synchronized when returning to the foreground.

**Acceptance criteria:**
- [ ] Pressing Stop in the notification dismisses the "Stop reading" pill and clears the sentence highlight in `ReaderScreen`
- [ ] Returning to the foreground shows the reader on the page of the currently spoken sentence
- [ ] macOS `Speech` implementation continues to work unchanged

**Verification:**
- [ ] Tests pass: `./gradlew :composeApp:desktopTest`
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** Task 13

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/Speech.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderScreen.kt`
- `composeApp/src/desktopMain/kotlin/com/baca/app/reader/Speech.desktop.kt`

**Estimated scope:** Medium (3 files)

---

## Checkpoint: After Tasks 13-14
- [ ] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [ ] Background Read Aloud verified on Android

---

## Task 15: Define `PdfWordBox` and implement Desktop PDFBox text extractor

**Description:** Define a normalized-coordinate `PdfWordBox(text, left, top, right, bottom)` model in `PdfParser.kt` and implement `expect fun extractPdfPageText(path: Path, pageIndex: Int): List<PdfWordBox>` on Desktop using Apache PDFBox's `PDFTextStripper`.

**Acceptance criteria:**
- [ ] `extractPdfPageText` returns words with normalized `0f..1f` bounding boxes relative to page width and height
- [ ] Works on multi-column and rotated PDF pages without crashing
- [ ] Unit test verifies word text and bounding-box ordering on a sample PDF

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/parser/PdfParser.kt`
- `shared/src/jvmMain/kotlin/com/baca/shared/parser/PdfParser.jvm.kt`
- `shared/src/jvmTest/kotlin/com/baca/shared/parser/PdfTextTest.kt`

**Estimated scope:** Medium (3 files)

---

## Task 16: Implement Android PDF text extractor

**Description:** Implement `extractPdfPageText(path: Path, pageIndex: Int)` on Android using `pdfbox-android` (or `PdfRenderer.Page.getTextContents()` on API 35+ with `pdfbox-android` fallback) returning the same normalized `PdfWordBox` list.

**Acceptance criteria:**
- [ ] Android `extractPdfPageText` produces normalized `0f..1f` `PdfWordBox` coordinates matching Desktop
- [ ] Runs off the main thread without blocking UI rendering

**Verification:**
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain :android:assembleDebug`
- [ ] Manual check: Open a text-based PDF on Android and verify word boxes align with rendered page text

**Dependencies:** Task 15

**Files likely touched:**
- `gradle/libs.versions.toml`
- `shared/build.gradle.kts`
- `shared/src/androidMain/kotlin/com/baca/shared/parser/PdfParser.android.kt`

**Estimated scope:** Medium (3 files)

---

## Task 17: Add PDF text selection, highlight rects, and search to `CbzScreen`

**Description:** Enable long-press word selection, drag-handle range selection, highlight/note/copy/define toolbar, persistent `Fixed` annotation rects (`[[x, y, w, h]]`), and in-book search for PDFs in `CbzScreen`.

**Acceptance criteria:**
- [ ] Long-pressing text on a PDF page selects the word and lets the user drag handles to highlight, add a note, copy, or Define
- [ ] Saved PDF highlights render as translucent yellow/colored rects over the page image and appear in `NotesScreen`
- [ ] In-book search finds matching text across PDF pages and jumps to the page

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest :composeApp:desktopTest`
- [ ] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** Task 15, Task 16

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/CbzScreen.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/domain/reader/CbzSession.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/domain/reader/BookSearch.kt`

**Estimated scope:** Medium (3 files)

---

## Checkpoint: After Tasks 15-17
- [ ] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [ ] PDF text selection, highlights, and search work on Desktop and Android

---

## Task 18: Implement `#120` ruby / furigana (`<ruby>` / `<rt>`) layout and rendering

**Description:** Parse `<ruby>` base + `<rt>` annotation pairs into structured ruby runs, reserve space above the line in `ReflowLayout`, and draw `<rt>` centered over its base text in `ReaderScreen`.

**Acceptance criteria:**
- [ ] `<rt>` text renders at half size centered above its base characters rather than inline after them
- [ ] Line height expands when needed so ruby annotations do not collide with the line above
- [ ] `<rp>` remains hidden

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest :composeApp:desktopTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/parser/Parser.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/render/ReflowLayout.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderScreen.kt`

**Estimated scope:** Medium (3 files)

---

## Task 19: Implement `#121` inline pictures sharing a line with text

**Description:** Measure inline `<img>` dimensions before line breaking so images inside a paragraph share the line beside surrounding text instead of breaking the paragraph into separate blocks.

**Acceptance criteria:**
- [ ] Inline images fit beside text on the same line when space allows, wrapping to the next line if they overflow
- [ ] Line height grows to accommodate taller inline images (sitting on the baseline)
- [ ] Block-level images (`display: block`) retain existing behavior

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest :composeApp:desktopTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/render/LineBreaking.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/render/ReflowLayout.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderScreen.kt`

**Estimated scope:** Medium (3 files)

---

## Task 20: Implement `#125` vertical writing details (`vertical-lr`, tate-chu-yoko, selection)

**Description:** Extend vertical writing support to handle `writing-mode: vertical-lr`, upright 2-digit tate-chu-yoko (`text-combine-upright`), and coordinate mapping for text selection and highlights on vertical pages.

**Acceptance criteria:**
- [ ] `vertical-lr` stacks columns left-to-right and turns pages left-to-right
- [ ] 2-digit numbers / `text-combine-upright: all` render upright side-by-side within a vertical column
- [ ] Long-press text selection and highlights work accurately on vertical pages

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest :composeApp:desktopTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/VerticalText.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/render/ReflowLayout.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderSelection.kt`

**Estimated scope:** Medium (3 files)

---

## Task 21: Implement `#126` 2D MathML layout (`mfrac`, `msqrt`, `mtable`)

**Description:** Upgrade MathML rendering from linear fallback strings to 2D box layout for stacked fractions (`<mfrac>`), radicals (`<msqrt>`/`<mroot>`), and matrices (`<mtable>`).

**Acceptance criteria:**
- [ ] `<mfrac>` draws numerator above horizontal bar above denominator
- [ ] `<msqrt>`/`<mroot>` draws radical sign and vinculum over radicand
- [ ] `<mtable>` lays out aligned matrix rows/columns

**Verification:**
- [ ] Tests pass: `./gradlew :shared:jvmTest :composeApp:desktopTest`
- [x] Build succeeds: `./gradlew :composeApp:compileAndroidMain`

**Dependencies:** None

**Files likely touched:**
- `shared/src/commonMain/kotlin/com/baca/shared/parser/MathMl.kt`
- `shared/src/commonMain/kotlin/com/baca/shared/render/ReflowLayout.kt`
- `composeApp/src/commonMain/kotlin/com/baca/app/reader/ReaderScreen.kt`

**Estimated scope:** Medium (3 files)

---

## Checkpoint: Complete (After Tasks 18-21)
- [ ] `./gradlew :shared:jvmTest :composeApp:desktopTest :composeApp:compileAndroidMain` passes
- [ ] `docs/TECH.md` and `docs/LIBRERAX_GAPS.md` updated to reflect all completed features
- [ ] Ready for final review
