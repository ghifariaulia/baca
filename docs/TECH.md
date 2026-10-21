# Baca Technical Documentation

Baca is a cross-device e-reader: Android and Desktop (JVM) now, iOS later. Shared logic and UI in Kotlin Multiplatform. Book state syncs as local JSON files through a pluggable transport.

## 1. Stack

| Layer | Choice |
|---|---|
| UI | Compose Multiplatform |
| Domain logic | Kotlin Multiplatform (KMP) |
| Persistence | SQLDelight |
| Serialization | kotlinx.serialization (JSON) |
| ZIP / file IO | Okio (multiplatform) |
| XML / XHTML parsing | KMP XML library (e.g. xmlutil) |
| Text formats (EPUB/FB2/TXT/HTML) | pure Kotlin parser |
| CBZ | ZIP of images, decoded with platform image APIs (no native renderer) |


Targets now: `androidTarget`, `jvm` (desktop). iOS (`iosArm64`/`iosSimulatorArm64`) deferred until Android + Desktop are done; the design stays KMP so it slots in without rework.

## 2. Module layout

```
baca/
  composeApp/          shared UI library (Compose, KMP: android + desktop targets)
  shared/
    domain/            use cases, models, locator, conflict rules
    parser/            epub, fb2, txt readers
    render/            CBZ reader (fixed-layout page source)
    sync/              per-device files, pure merge functions, transport interface
    db/                SQLDelight schema + queries
  android/             Android entry point. AGP 9 does not allow com.android.application
                       on a KMP module, so native entries are thin modules depending
                       on composeApp
  desktop/             desktop entry point (JVM, hosts the Compose window)
  iosApp/              iOS entry point (added later, when iOS starts)
  backend/             sync HTTP service (v2)
```

Controller → use case → repository/parser/renderer. Controllers hold no logic of their own.

## 3. Book format support

Phase 1 (pure Kotlin, no native deps):

- **EPUB 2/3** — ZIP container, `META-INF/container.xml` locates OPF, spine gives reading order, chapters are XHTML. Parse: unzip with Okio's multiplatform ZIP support (`java.util.zip` and `zip4j` are JVM-only and do not work on iOS), read OPF metadata (title, author, cover) with the KMP XML library, render spine documents through the shared XHTML block parser.
- **FB2, TXT, HTML** — straightforward text/markup parsing.

Phase 2:

- **CBZ** — a ZIP of images. Read with Okio, sort entries naturally, decode with platform image APIs. No native renderer needed.
- **PDF** — deferred. Not part of the current plan; add later behind a renderer interface when needed.
- **DjVu** — only if a real need appears. **MOBI/AZW3** — not planned.

### 3.1 Book CSS (EPUB)

EPUB chapters keep their own styling the way LibreraX shows them: the book's CSS cascades as in a browser (`!important`, specificity, order), on top of a few reader defaults. The reader's font, size and line spacing are not CSS here. Stylesheets come from `<link rel="stylesheet">`, `<style>` and `style=""` (`shared/parser/BookCss.kt`).

- **Reader defaults:** headings centred and bold unless the book says otherwise; links in the link colour unless a book rule outranks `a { color: link !important }`; where the book sets no `text-indent`, the reader's 1em indent; where it sets no vertical margin or padding, the reader's 0.2em gap between blocks.
- **Applied from the book:** `text-align: center/right`; `float: right` at the right edge, on the row of the text before it when it fits there a space apart (as in a browser), else on its own line; `text-indent` zero (none), positive (the reader's indent) or negative (hanging: first line flush, the rest indented); vertical `margin`/`padding` (em, px, pt) as the gap above a block, margins collapsing to the largest and padding adding up; `font-variant: small-caps`; `font-weight` / `font-style`; `display: none`; relative `font-size` (`em`, `%`, keywords) for blocks (clamped to 0.75–2× body text) and for inline spans; `font-family: sans-serif` on inline elements (drawn in the reader's sans font; paragraphs and headings keep the reader's font, as in LibreraX); a link whose winning rule sets `color` and `text-decoration: none` draws as body text; `border-left` draws a hairline rule beside the element's blocks (at its `margin-left`, running on through the gaps between them), with the text set in by `margin-left` + `padding-left`.
- **Ignored:** `text-align: left/justify` (the reader justifies), other horizontal margins and borders, absolute font sizes, color values, named fonts.
- **Selectors:** compounds (`tag`, `.class`, `tag.class`) joined by descendant (space), child (`>`) or next-sibling (`+`) combinators; `~`, attribute, id and pseudo selectors are skipped.
- **Hyphenation:** body text hyphenates where the platform can (Android); the renderer draws the hyphen at a mid-word break.
- Whitespace follows HTML (3.x parser notes); a paragraph holding only `&nbsp;` is a blank line, as books use it for scene breaks.

## 4. Rendering

Two paths, decided per format:

**Reflowable (EPUB/FB2/TXT/HTML).** Parser converts each chapter into styled blocks (paragraph, heading, image, blockquote). Layout engine paginates blocks into `Page` objects sized to the current viewport; page list cached per (book hash, viewport size, font settings). Recompute cache on font-size change or rotation. Pages are a **local cache only**: nothing that is persisted or synced may depend on a page number (see locator, section 5).

This is the largest technical risk in the project: pagination, hyphenation, a CSS subset, images and RTL in Compose. It is de-risked by a time-boxed spike (M0) before the rest of M1 is built.

Fallback if the Compose reflow engine stalls the project: render chapters in a WebView on mobile. Costs night-mode and font control, and does **not** cover desktop (needs a separate answer, e.g. a JVM WebView/JCEF, or accepting reduced desktop scope). Treat as an escape hatch only.

**Fixed (CBZ).** Direct page images, zoom + pan, no pagination engine.

## 5. Persistence

SQLDelight database per device, one schema for all targets. **The local database is the source of truth on a device. The JSON files are the sync format only**: they are produced from the DB on export and merged back into the DB on import, at the sync boundary and nowhere else.

- `books` — id, book_hash, title, author, path, format, cover, added_at, deleted
- `positions` — book_hash, device_id, locator (JSON), lamport, updated_at
- `annotations` — id, book_hash, kind, anchor (JSON), text, note, created_at, device_id, lamport, deleted (tombstone flag)
- `settings_local` — key/value, never synced (font size, theme, brightness, layout options)
- `settings_synced` — key, value, lamport (small, explicit allowlist)
- `sync_state` — device_id, path, last_seen_counter, plus this device's own counter and Lamport clock

Databases live on device. Sync files live in the sync folder.

### 5.1 `book_hash`

`book_hash = SHA-256(file_size || first 64 KiB || last 64 KiB)`. Hashing a whole 200 MB file is too slow, and this is stable enough in practice. Two different files of a book get different hashes; all data stays keyed by `book_hash`.

**Edition linking.** Files of the same EPUB edition (re-zipped, re-downloaded) are linked by a work key: the OPF's unique `dc:identifier` plus the title, both normalized (`workKeyOf`). The key is stored per book as the local setting `work:<book_hash>` and travels as `workKey` in `library.json`. Reading a book considers every linked hash: the newest position wins, and annotations from all linked files show. Anything made in a different file is re-found by its quote, then by book-wide progression; its href/block/offset are never trusted, since they can point at different text. Unfound annotations show as "can't locate" instead of highlighting the wrong text. Different editions (different ISBNs) and books without an identifier stay separate.

### 5.2 Locator (stable position)

A locator describes a place in a book independent of viewport, font and device. It is the only position format stored or synced.

```jsonc
// reflowable
{ "href": "OEBPS/ch12.xhtml",   // spine document
  "block_index": 41,            // block within the document
  "char_offset": 128,           // offset within the block's text
  "progression": 0.57,          // overall book progress, display and fallback only
  "quote": "short snippet of text at the position" }

// fixed layout
{ "page_index": 211, "progression": 0.57 }
```

Resolution order when restoring: `href` + `block_index` + `char_offset`, then the `quote` snippet searched inside the document, then `progression`. `chapter/page/percent` from earlier drafts are replaced: `page` is derived locally from the locator and the current page cache, `percent` is `progression`. An EPUB CFI may be stored in addition for interoperability, but is not required.

### 5.3 Annotation anchor

```jsonc
// reflowable
{ "href": "OEBPS/ch12.xhtml", "start": {"block_index": 41, "char_offset": 128},
  "end": {"block_index": 41, "char_offset": 190},
  "quote": "exact highlighted text",
  "prefix": "~32 chars before", "suffix": "~32 chars after" }

// fixed layout
{ "page_index": 211, "rects": [[x, y, w, h]] }
```

Offsets are the primary anchor. If the parse changes (parser update, different edition, offset no longer matches `quote`), re-anchor by searching for `prefix + quote + suffix` in the document. If that fails, keep the annotation, show it in the annotation list, and flag it as detached rather than dropping it.

### 5.4 Book files and the library folder

Without a library folder, an imported book is copied into app storage (desktop `~/.baca/books`, Android `filesDir/books`) as `<book_hash>.<ext>`, and `books.path` points at that copy.

With a **library folder** (Settings, stored as the device-only local setting `library_folder`), books are read where they are and never copied:
- `books.path` holds the book's location in the folder: a file path on desktop, a document URI inside a persisted SAF tree grant on Android. Parsers need a real file, so Android stages a temporary cache copy to read from.
- The folder and all its subfolders are scanned at launch and on Rescan. New files are added. A known `book_hash` found at a new location keeps its reading data and gets the new path (moved or renamed). Books the user removed stay removed. Books whose file is gone from the folder are hidden until it comes back.
- **Add books** copies the picked file into the folder's top level, never overwriting (`Book (2).epub`), then reads it in place.
- **Remove from library** hides the book (tombstone, 6.4); the file stays in the folder.
- Only covers are stored in app storage.

## 6. Sync

### 6.1 Principles

- **Each device writes only its own files.** No file is ever written by two devices, so folder-sync tools (iCloud, Dropbox, Syncthing) cannot produce "conflicted copy" files. Every device reads everyone's files and merges on read.
- **Ordering uses a Lamport clock, not wall clocks.** Each device keeps a Lamport counter: on every local write `lamport = max(own, highest lamport seen) + 1`. Records are ordered by `(lamport, device_id)`. `updated_at` is stored for display and debugging only, so clock skew cannot make the wrong value win.
- **Merge is pure.** Merge functions take records and return records. No IO, no clock reads.

### 6.2 File layout (JSON, human-readable, stable schema)

```text
devices/<device-id>/meta.json
devices/<device-id>/state/<book-hash>.json
devices/<device-id>/annots/<book-hash>.json
devices/<device-id>/library.json
devices/<device-id>/settings.json
```

Field shapes:

```jsonc
// devices/<id>/meta.json
{ "device_id": "phone-pixel8", "schema_version": 1, "counter": 41, "lamport": 903,
  "updated_at": 1712345678 }

// devices/<id>/state/<book-hash>.json
{ "locator": { "href": "OEBPS/ch12.xhtml", "block_index": 41, "char_offset": 128,
               "progression": 0.57, "quote": "..." },
  "lamport": 903, "updated_at": 1712345678 }

// devices/<id>/annots/<book-hash>.json — every annotation this device created, edited or deleted
{ "annotations": [
    { "id": "uuid", "kind": "highlight", "anchor": { /* see 5.3 */ },
      "text": "...", "note": "", "created_at": 1712345678,
      "lamport": 880, "deleted": false } ] }

// devices/<id>/library.json
{ "books": [ { "book_hash": "...", "title": "...", "added_at": 1712345678,
               "lamport": 700, "deleted": false,
               "workKey": "...", "finished": true } ] }   // last two optional

// devices/<id>/settings.json — synced allowlist only
{ "settings": { "dictionary": { "value": "...", "lamport": 650 } } }
```

`finished` is the reader's "Mark as finished" flag, kept locally as `finished:<book_hash>`. A book counts as finished when marked or read through (98%, `isFinished` in `shared/domain/ReadState.kt`); "Mark as unread" clears the flag and saves a start-of-book position, so it syncs over the old place; changing it bumps the book's Lamport value so the newest choice wins like any library change. Files from older versions omit `workKey` and `finished`, and a missing value keeps what the device already knows.

`meta.json` holds the device's counter, bumped on every write batch. A peer detects "this device has new data" by comparing `counter` with its locally stored `last_seen_counter` for that device (`sync_state`), with no cross-device timestamp comparison. This replaces the shared `sync/journal.json`, which every device would have written on every change.

### 6.3 Merge rules

All rules are per record, deterministic, and use the order `(lamport, device_id)` with the highest winning.

- **Position**: per book, collect the position record from every device; the highest `(lamport, device_id)` wins. The losing device is told a merge happened; a "pick my version / keep incoming" prompt ships later if needed.
- **Annotations**: union by `id` across all devices' files. For each `id`, the record with the highest `(lamport, device_id)` wins. Edits are just a newer record with the same `id`; a deletion is a newer record with `deleted: true`. So a delete followed by an edit (or the reverse) resolves by clock order instead of one side always winning. Two devices editing the same note concurrently resolve to the later one; the earlier text is not preserved in v1.
- **Library**: union by `book_hash`; the highest `(lamport, device_id)` entry wins, whether it is an add or a delete. Re-adding a deleted book therefore works, because the new add has a higher Lamport value than the old tombstone.
- **Settings (synced allowlist)**: per-key last-writer-wins by `(lamport, device_id)`. Device-specific settings (font size, theme, brightness, anything viewport-dependent) are in `settings_local` and are never synced.

Deletions and edits never lose to positions: they are in different record types and never compete.

### 6.4 Tombstone garbage collection

Tombstones (`deleted: true` records) are kept until every known device has seen them: a tombstone may be dropped once each device in `devices/` has a `last_seen_counter` at or past the write that produced it, and it is at least 90 days old. A device that has not synced for longer than that may resurrect deleted items when it returns. This is accepted in v1 and documented to the user.

### 6.5 Sync loop

After any local change:

1. bump Lamport and counter, write this device's files (and `meta.json`) from the DB,
2. push this device's changed files via the transport,
3. list peers' `meta.json` files; for each peer whose `counter` is newer than `last_seen_counter`, pull its changed files,
4. run the pure merge functions over local + pulled records, persist the result to SQLDelight, update `sync_state`.

The merge functions in `shared/sync` have no IO and are covered by property tests: **idempotent** (merging the same input twice changes nothing), **commutative** (order of peers does not matter) and **associative** (merge grouping does not matter), plus example tests for each rule above. Merging in any order must converge to the same state.

### 6.6 Transports

One interface, built bottom-up:

```kotlin
interface SyncTransport {
    suspend fun push(relativePath: String, bytes: ByteArray)
    suspend fun pull(relativePath: String): ByteArray?
    suspend fun list(): List<String>
}
```

1. **v1 — folder transport.** User picks any folder synced by external tools (iCloud, Dropbox, Syncthing). Zero server, covers most users, and is the reference implementation for tests. Platform access is not a plain path on mobile:
   - **Android**: Storage Access Framework; persist the tree URI permission and access files through `DocumentFile`/`ContentResolver`.
   - **iOS** (when the iOS target starts): security-scoped bookmarks; start and stop access around each operation, and handle iCloud files that are listed but not yet downloaded (request download, retry).
   - **Desktop**: plain filesystem path.
   Because of this, the folder transport is an `expect/actual` implementation, and the M4 estimate should assume real mobile work.
Self-hosted HTTP server: **out of scope** (2026-10-14, user decision — sync is fully local: folder transport + manually copying the sync folder; Kotlin does the local file IO).

LAN direct peer sync: out of scope until asked for.

### 6.7 Backup and restore

Separate from sync, on the Sync screen. **Create backup** writes one zip: `library.json` (every table: books, positions, annotations, synced and local settings) plus the imported book files and covers under `books/`. Paths are stored as file names, so a backup restores on any device or platform.

**Restore** replaces the library after a confirmation. It first saves the current library as `before-restore.zip` next to the books folder, copies the book files, then swaps the tables in one transaction; an archive without `library.json` changes nothing. This device's identity (`device_id`), its sync folder and last-sync time are kept, and its Lamport clock is advanced past the restored records so the next local edit wins (6.1). Code: `shared/backup/LibraryBackup.kt`.

## 7. Licensing

- KOReader itself: AGPL-3 — not used directly (it is a Lua application, not a renderer library).
- crengine (KOReader's EPUB engine): GPL — avoided; EPUB parsed in Kotlin instead.
- PDF: deferred. When it is added, pick the renderer with licensing in mind: muPDF is AGPL-3 (or commercial), which would make the app AGPL and conflicts with App Store distribution; platform renderers and PDFium avoid this.

## 8. Milestones

0. **M0 — reflow spike (time-boxed).** One real-world EPUB, paginated in Compose on one target, with a font-size change and a rotation, producing a locator and restoring it. Go/no-go on the Compose reflow engine; decide on the WebView fallback and its desktop story here, not later.
1. **M1 — core reading loop.** KMP skeleton, EPUB import + Okio unzip, reflow render of one chapter, position saved locally **as a locator**. The `positions` and `annotations` schemas and the locator/anchor formats are locked in this milestone, because changing them after M4 means data migrations. Largest chunk of the project; land it before anything else.
2. **M2 — library.** Book list, metadata, cover, delete, TXT/FB2 import, `book_hash`.
3. **M3 — fixed-layout.** CBZ via zip + image decoding; open, zoom, pan. PDF deferred.
4. **M4 — sync v1.** Per-device file layout, Lamport clock, pure merge functions with property tests, tombstone GC, folder transport including Android SAF. iOS bookmarks arrive with the iOS target.
5. **M5 — sync backend.** HTTP service, Docker setup, desktop polish.

## 9. Open questions

1. WebView fallback: acceptable on mobile only, and what is the desktop answer if reflow fails? Decide at M0.
2. Folder-only sync acceptable for first release? Affects M4 scope only.
3. Is the 90-day tombstone horizon acceptable, or should long-offline devices be forced to do a full resync?

## 10. UI design guide

Mockups: `Baca App UI Design.html` in the repo root — 18 pages (phone: Library, Empty
library, Book detail, Add books, Import progress, Reader, Reader selection, Contents sheet,
Search in book, Display settings, Note editor, Notes, Sync, Sync errors; desktop: Library,
Reader, Display settings popover, Notes). This replaces the earlier claude.ai artifact link
for the desktop variants; phone specs below still describe the same designs.

### 10.1 Principles

- **The page is the product.** Chrome stays quiet and neutral; the reading surface gets the warmth and the typography.
- **One accent.** Teal marks the primary action and the active state. Nothing else competes with it.
- **Sync is visible but not noisy.** A status line and a banner when something changed; no dialogs for routine syncs.
- **Same look on every target.** Phone and desktop share tokens and components; only layout differs.

### 10.2 Tokens

| Token | Value | Use |
|---|---|---|
| `ground` | `#F5F6F8` | app background |
| `surface` | `#FFFFFF` | cards, sidebar, sheets |
| `ink` | `#12151C` | primary text, secondary buttons |
| `muted` | `#5B6472` | secondary text, inactive icons |
| `line` | `#E3E6EB` | borders, progress track |
| `accent` | `#0F766E` | primary buttons, active nav, progress |
| `accentTint` | `#E6F2F0` | active nav item, info banner |
| `paper` | `#F7F3EA` | reader background (Paper theme) |
| `readerInk` | `#1F1B16` | reader text (Paper theme) |
| `highlight` | `#F6D860` | highlights |

Contrast: `muted` on `ground`/`surface` and white on `accent` both pass 4.5:1. Do not use `muted` on `paper` below 14px without checking.

Typography: **DM Sans** (400/500/700) for UI, **Literata** (400/600/700) for book text and cover titles; **Libron** (static Regular/Italic/Bold/BoldItalic) as an optional reading font. All are open-licensed; bundle them as app resources instead of loading from the network.

| Style | Size / weight |
|---|---|
| Screen title | 28 phone, 32 desktop / 700 |
| Card title | 16–18 / 700 |
| Body, buttons | 14–15 / 500–700 |
| Section label | 13 / 700, uppercase, `muted` |
| Caption | 12–13 / 400, `muted` |
| Reader text | 19 / 1.7 phone, 21 / 1.75 desktop, Literata |

Shape and spacing: card radius 16, sheet top radius 20, chip radius 18, pill buttons 22 (44 high), book cover radius 6, progress bars 3–4 high. Screen padding 20 (phone), 32–40 (desktop). Gaps in steps of 4, mostly 8, 12, 16, 24.

### 10.3 Components

- **Book cover**: 2:3 ratio, flat color with title (Literata, bold) and author (caps, 9–10px). A real cover image replaces it when available. Under it: title (12–13 / 500) and a thin progress bar.
- **Buttons**: primary (accent fill, white text), secondary (ink fill), text button (accent text, "Done", "Undo"), icon button (44×44 minimum). All targets are at least 44px.
- **Chips and segmented controls**: 36–44 high, selected state is filled (ink for filters, accent for settings).
- **Bottom nav (phone)**: Library, Notes, Sync; active item uses accent and bold label. **Sidebar (desktop)**: 240 wide, same three items plus sync status at the bottom.
- **Bottom sheet**: white, 20 top radius, handle, title row with "Done", dimmed reader behind (45% ink overlay).
- **Banner**: `accentTint` background, one line of text, one text button.
- **Annotation card** (desktop notes panel): highlight swatch, quoted text in Literata 15, optional note, meta line "Chapter · device".
- Icons: simple 2px-stroke line icons; icon-only buttons need an accessible label.

### 10.4 Screens

**Library** (phone and desktop): title, search, add-book action, filter chips (All / Reading / Finished; phone), "continue reading" card with resume action and progress, all-books grid (3 columns phone, 6 desktop) with per-book progress.

**Reader**: top bar with back, centered muted book title, contents (☰) and display settings (Aa); the phone's overflow (⋮) and in-book search are designed but not implemented yet (need the features behind them). Text column: full width with display-setting margins on phone, centered at max 680px on desktop. Footer: **whole-book** progress — thin accent bar (1.5dp idle, 12dp while hovering/dragging, strip stays 24dp for the 44px touch target) plus "Page x of y" left and percent right, x/y counted over all chapters, not the current one (deviation from the mock, which labels per-chapter pages; user decision 2026-10-14). Under the page, always shown and in the reading theme (LibreraX's footer, `ReaderFooter.kt`): a progress track with a tick at each chapter start, "Pages left: n" in the chapter, the time and battery (read on each page turn; no battery on desktop) and "page / total"; the page gives up the footer's height so text never runs under it. Highlights render with `highlight`. Desktop adds a right panel (340 wide) with Contents / Notes tabs.

The contents sheet and display sheet are single shared bottom-sheet layouts (per the mock's phone pages): handle, title + Done, and — for the display sheet — font-size stepper, font segmented control (accent fill on selection), theme color swatches (accent ring on selection), line spacing, and margins. The contents sheet lists toc entries with right-aligned chapter percents; the current chapter is accent-bold with a dot and "You are here". Both use the same percent formula `(pagesBefore × 100)/(pagesInBook − 1)` as the footer so jump targets line up with the clicked row.

**Display settings** (bottom sheet on phone; a popover from Aa on desktop): font size stepper, font (Serif / Sans / Mono), theme (Paper / White / Dim / Black), line spacing (Compact / Normal / Relaxed), margins (Narrow / Medium / Wide). Footnote: settings stay on this device. These map to `settings_local` (section 5) and are never synced. Reader text colors for Dim and Black are not specified yet.

**Sync**: status card (up to date, last synced, Sync now), transport rows (shared folder; picking re-chooses), device list with last-seen times (from each device's `meta.json`), and a banner when a merge changed the local position ("Position updated from Desktop", with Undo). No server option — sync is fully local (2026-10-14 decision).

### 10.5 Layout rules

- Reference sizes: phone 390×844, desktop 1280×900. Desktop layouts are fluid: sidebar and panels have fixed widths, the content area flexes, the reading column has a max width.
- On desktop widths below about 900px, collapse the sidebar to icons and move the notes panel into a sheet or popover.
- Page text is always paginated for the actual viewport; the footer page numbers come from the local page cache, never from synced data (sections 4 and 5.2).

### 10.6 Behavior notes that touch the sync design

- **Merge banner and Undo**: section 6.3 only says the losing device is told a merge happened. Undo needs the previous local locator to be kept until the banner is dismissed. This is a small addition to the merge step in 6.5.
- **Annotation origin**: the notes panel shows the originating device, so the UI reads `device_id` from each annotation record (section 6.2).
- **Detached annotations** (section 5.3) appear in the notes list with a "can't locate in text" label and are not drawn on the page.
- **Device list** shows only devices with a `devices/<id>/` folder; removing a device from the list does not delete its files.

### 10.7 Accessibility

- Text 4.5:1 minimum; touch targets 44px; selected states differ by fill and label weight, not color alone.
- Real controls for every action, labels on icon-only buttons, and system font scaling respected in the UI. The reader uses its own font-size setting.
- Reader themes must keep body text at 7:1 or better where practical.

### 10.8 Implementation notes

- Define tokens once in a shared `AppTheme` (Compose `MaterialTheme` color scheme, typography and shapes) in `composeApp`; screens use theme values, not literals.
- Build reusable components (`BookCover`, `ProgressBar`, `SegmentedControl`, `Banner`, `AnnotationCard`) in `composeApp` and keep them free of business logic, per section 2.
- Window size classes choose phone vs desktop layouts; the screens themselves are shared.
- Implemented as of 2026-10-14: `AppTheme` tokens/typography/shapes, Reader (top bar, paged column, footer, tap zones), Contents and Display sheets, Library screen (search, filter chips, continue-reading card, 3-column grid with covers + progress, long-press delete), Empty-library page, Book detail (cover, progress card, Continue reading, format/size/added/last-read metadata, remove), the Add-books sheet with the Import progress screen (per-file statuses added/skipped/unsupported/broken; Android multi-pick, desktop single-file + folder import), import pipeline (hash dedupe, copy into app storage, EPUB cover extraction incl. wrapper-page covers, TXT/FB2 in `shared/parser/PlainTextParsers.kt`), and the CBZ fixed-layout reader (`CbzScreen`, zoom/pan via pinch, tap zones, thin seek bar, page-index locator through `CbzSession`/`loadFixedPosition`). Sync core shipped 2026-10-14 (Merge.kt/Lamport.kt/SyncBoundary.kt + tests; TECH 6.3/6.4/6.5). Sync UI shipped 2026-10-14 (SyncScreen + bottom nav/sidebar entry; no server row). Notes shipped 2026-10-14 (long-press toolbar → note editor → highlights render on pages; contents-sheet/panel Notes tab; Notes screen grouped by book; AnnotationEntry.color added). Not yet built: fine-grained text selection (whole-block highlights in v1), DM Sans/Literata font bundling. Desktop layout variants shipped (sidebar, 6-col grid, wide continue card with Resume, reader notes/contents panel at 340dp, 680dp max text column, popover display settings; below 900px everything falls back to the phone layout).

## 11. Revision notes

Changes from the previous draft:

- Positions and annotations moved from `chapter/page/percent` and bare `range` to a viewport-independent locator and anchor with re-anchoring fallbacks.
- Shared JSON files and `sync/journal.json` replaced by per-device files, which removes folder-sync conflicted copies; ordering uses a Lamport clock instead of wall clocks.
- Annotation edits, library re-adds and tombstone cleanup are now defined; settings split into local and synced.
- `zip4j`/`java.util.zip` replaced by Okio; CBZ is plain zip + images; PDF and muPDF removed from scope (deferred).
- Mobile folder access (SAF, security-scoped bookmarks), `book_hash`, and DB-vs-JSON source of truth are now specified.
- Added M0 reflow spike, locator lock in M1, and property-tested pure merge logic in M4.
- Added a UI design guide (section 10): tokens, components, screens, layout and accessibility rules, and the UI behaviors that depend on sync.
