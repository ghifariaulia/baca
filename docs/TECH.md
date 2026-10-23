# Baca Technical Documentation

Baca is a cross-device e-reader: Android and Desktop (JVM) now, iOS later. Shared logic and UI in Kotlin Multiplatform. Reading data moves between devices with backup and restore; there is no sync.

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
    db/                SQLDelight schema + queries
  android/             Android entry point. AGP 9 does not allow com.android.application
                       on a KMP module, so native entries are thin modules depending
                       on composeApp
  desktop/             desktop entry point (JVM, hosts the Compose window)
  iosApp/              iOS entry point (added later, when iOS starts)
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

EPUB chapters are laid out the way LibreraX shows them: its WebView applies a browser's default stylesheet, then the book's CSS (cascading as in a browser: `!important`, specificity, order), then its own few rules. Baca reproduces that cascade (`shared/parser/BookCss.kt`) and the parts of a page it can draw. Stylesheets come from `<link rel="stylesheet">`, `<style>` and `style=""`.

- **Defaults (a browser's, `DEFAULT_CSS`):** no paragraph indent, 1em between paragraphs, headings left-aligned at 2/1.5/1.17em with their own margins, lists and blockquotes set in 40px, `<pre>`/`<code>` monospace, `small`/`sub`/`sup` smaller, `<th>` bold and centred, `<hr>` two 1px inset grey borders (whole device px, the top darker), `[hidden]`, `script`, `template`, `noscript` and `rp` not shown, `<q>` quoted, `mark` on yellow, `big` larger, `rt` half size. A linked stylesheet's `-epub-` prefixes are dropped, and the book's `html` padding gives way to foliate's. On top, as LibreraX's `css()`: the reader's line height on body, `p`, `li`, `div`, `blockquote`, `dd`, `dt`, `td`, `th` and `figcaption` (other blocks, headings and what's inside them among them, keep the book's, or inherit the reader's); hyphens on body, `p`, `li` and `div`; contents links (`nav`/`ol`/`ul a[href]`) underlined and contents entries ranged left.
- **Applied from the book:** alignment (centre/right; left and justify leave the reader's justification on body, p, li, div and blockquote, while elsewhere a book's left stays ragged; captions centred); `text-indent` in its own amount (a percentage of the block's width), negative hanging (kept inside the column); margins and padding on every side (em, px, pt, %, negative side margins pulling text past the column, `auto` centring a narrower `width`; `Spacing` keeps em, which grow with the reader's font, apart from CSS px, which follow the screen's density as in a browser, and shares of the page's height, the window's and the column's width (`vh`, a linked sheet's `vh`, percent top and bottom margins), which the layout measures with its `Room`), vertical ones collapsing as in CSS (negative ones too, the largest positive plus the most negative; not across a padding); borders (each side's width and colour), `border-radius` and `background-color` drawn round the block's box (`BlockBox`, `ReflowLayout.boxesOf`; a box split over pages is cut at the page edge; no background on dark themes); a blank block of fixed `height` held at that height; `column-count`/`column-gap`, the text measured at a column's width and balanced over the columns (`columnLines`; a block split over pages keeps its rows, not a browser's refill); page breaks as a browser's columns take them after foliate's rewriting: a linked stylesheet's `page-break-*` and `break-*: page` become column breaks (`linkedSheet`), and only `break-*: column` and `-webkit-column-break-*: always` force one (a `<style>` element's or `style` attribute's `page-break-before: always` doesn't); `avoid` and `avoid-column` (with `break-inside`): a page break the book avoids goes back to the page's last one it doesn't; inline `padding`/`margin` and `inline-block` `min-width` as space in the line; `float: right` beside the line before when it fits; `float: left` first letters as drop caps; `display` (`none`, `inline` headings, `block` spans); `line-height` (a ratio, px kept as px, `normal` as the font's own ascent and descent; the reader's leading on running text, not inside headings); font size relative and absolute (keywords in Chromium's px, plain `monospace` at 13px where its size comes from a keyword); `font-family` as its generic face (`sans-serif`, `monospace` and `serif` in the system's own faces); weight, style, small caps, `text-transform` (letter for letter), `letter-spacing`, `word-spacing`, `vertical-align` (keywords, `super`/`sub` by a third/fifth of the parent's size and a device px, lengths, percentages of the line height; `top`/`middle`/`bottom` align a cell or picture), underline and line-through (`u`, `ins`, `s`, `del`…), `hyphens`, `visibility: hidden`; `white-space: pre`; `<wbr>`; `color` (light themes, not on html or body; dark themes force the ink, every `<a>` in light blue, as LibreraX); list markers from `list-style-type`; `<hr>` ornaments from `background-image`; picture sizes (see below). A line's boxes (its font's strut, a span in another face or size, a raised mark) reach as far above and below its baseline as Chromium makes them (`lineExtents`: ascent and descent rounded to whole device px, a px moved to the descent where it rounded down, the leading halved in 1/64 px), and a line is as tall as they reach.
- **Running text (LibreraX's `bridge.js`):** after parsing, the size and face most of the book's body text is set in are its running text. That size becomes the reader's font size and every other size keeps its ratio, each to the fraction of a px as a browser keeps it (`ReflowSettings.fontSizePx` is a Float; lines and gaps stack at their exact height too, and the renderer draws each row between its top and bottom rounded to whole px, so rows never drift from the measured page); that face becomes the reader's font and any other face is drawn in the reader's sans or monospace font (`shared/parser/RunningText.kt`). Spacing is put in em of the running text, and any of it 4em or more is halved, as LibreraX halves spacing a phone can't spare. A book with under 200 characters of body text takes each chapter's body size and face as its running text, as LibreraX takes the section's body.
- **Pictures:** their own size in CSS px, or the book's `width`/`height`/`max-width` (a `width="300"` attribute is 300 CSS px; a linked stylesheet's `vh`/`vw` of the reader's whole window, as foliate makes them px of the WebView; a `<style>` element's `vh` of the page, its `vw` 0), held to the column and the page (`object-fit: contain`), centred by their block; one that doesn't fit moves whole to the next page. A picture set inline stands on the baseline of a line in its containing block's font, size and line height, the line's descent and half-leading below it (`ReflowLayout.strutOf`). Letter-sized pictures (height ≤ 1em) stay in their line, drawn as other pictures are. `<svg><image>` pictures take the SVG's box. On a tinted page a drawing on white is multiplied into the page; on a dark page pictures are dimmed to 80%.
- **Selectors:** compounds of tag, `*`, `#id`, `.class`, attribute tests and `:first-child`/`:last-child`/`:only-child`, the `-of-type` forms, `:nth-child()` and the other `nth` forms, `:empty`, `:not()`, `:root`, `:link`, joined by descendant, `>`, `+` and `~` (`elementFacts` counts siblings ahead of the parse). Rules with other pseudo-classes or pseudo-elements are skipped. `@media` blocks apply when they match a 400×700 CSS px screen with the reading theme's colour scheme (dark below Rec.601 luminance 0.4; a book whose CSS asks for it is read again when the theme turns). A `<link>` or `<style>` with a `media` its query must match too, and an alternate stylesheet isn't applied; `@import`ed sheets apply before the sheet importing them.
- **Tables:** laid out in columns as a browser's auto table layout does: `<col>`/cell percentage widths; other columns each get their longest word and share what's left by how much wider their text runs on one line (CSS 2.1), all inside the padding of the boxes around the table and the table's `border-spacing` (Chromium's 2px, none when collapsed, `cellspacing`); a row is as tall as its tallest cell, and a shorter cell sits in it by its `vertical-align` (middle in a `tbody`/`thead`/`tfoot` the book writes, top otherwise) (`ReflowLayout.lineBoxOf`, `rowLines`). A table resets `text-indent`; `cellpadding` pads its cells. Attributes like these, `<font>`'s `size`, `face` and `color`, a list's `type`, and `align` on p, headings, div and table parts (Chromium's `-webkit-center`/`-webkit-right`, which also place narrower blocks inside, as `<center>` does), are presentational hints, over Baca's UA rules and under the book's. A cell with a border or background is a box drawn at its column's edges and as tall as its row; each column holds its cells' own border and padding. `border="N"` borders the table (N px outset) and its cells (1px inset) in gray.
- **Line breaking and hyphenation:** lines break as a browser breaks them, not as Compose does (`shared/render/LineBreaking.kt`): each line takes every word that fits, breaking at spaces, after a hyphen between letters, around an em dash, at `<wbr>` and around inline pictures, and the word that overflows is hyphenated at the last point where its first part and the hyphen still fit. `ComposeTextMetrics` lays a paragraph out once on one unbroken line and gives the breaker each character's edge. Hyphen points come from Liang's TeX patterns (`Hyphenator`, en-us as Android ships them) with a WebView's limits: words of 5 letters or more, 2 before the hyphen and 3 after, no capitalized English words, letters only. A block's language is the nearest `lang`/`xml:lang` round it, else the book's `dc:language`. On Android each language is hyphenated with the system's own compiled patterns (`/system/usr/hyphen-data/hyph-<locale>.hyb`, read by `HybHyphenator` as AOSP reads them, the locale found as Chromium's `MapLocale` finds it), which is what LibreraX's WebView uses; capitals are hyphenated outside English. Desktop carries American English only. Text in a language without patterns, or in none, breaks without hyphens. A paragraph's last word is never hyphenated unless it's the paragraph's only one, as Chromium's breaker leaves it. Text is measured and drawn unhinted (`TextMotion.Animated`), at each letter's exact advance as the WebView places it, and the reader's variable faces (Literata, DM Sans) at the running text's optical size, as `font-optical-sizing: auto` sets them. Every line but a paragraph's last is justified to the column edge however far it stretches, no-break spaces stretching with the others; the renderer draws the hyphen at a mid-word break.
- Whitespace follows HTML (3.x parser notes); a paragraph holding only `&nbsp;`, or only a printed page number (which is never shown), is a blank line, as books use it for scene breaks. A `<br>` outside any paragraph is a line of its own. TXT paragraphs (runs of lines between blank ones, each line its own) get a browser's 1em gap. Vertical writing (`writing-mode: vertical-rl` on html or body, `ParsedEpub.vertical`): the book is laid out in a turned frame, lines as long as the page is wide stacking across it, then drawn rotated a quarter (`rotatedPage`); each line is drawn by `VerticalLineText`, ideographs, kana and fullwidth forms turned back upright in their `vert` forms, other text sideways. Its page is a square the page's width on each side, centred in the height, as LibreraX's; pages turn right to left. `<math>` is written out along the line (`MathMl.kt`), not laid out in two dimensions. Right-to-left text: a block's direction is its `dir`, CSS `direction` or the book's language (`Block.Text.rtl`); its lines begin at the right edge, the first-line indent is there (`LineBox.rightPx`), `text-align: left`/`right` are physical, and a line with right-to-left letters is measured by its letters' widths since the glyphs run backwards. Such a book (spine `page-progression-direction`, language or first page: `ParsedEpub.pageRtl`) turns pages right to left. A fixed-layout EPUB (`rendition:layout` pre-paginated, or the iBooks/Kobo `fixed-layout` option) whose every page is one picture opens in the comic reader (`ParsedEpub.fixedPages`); with text on a page it is read reflowed. HTML, XHTML, Markdown, RTF and DOCX files are one chapter of the page LibreraX makes of them (`shared/parser/TextBooks.kt`: `HtmlNormalizer` shapes HTML as a browser would, `Markdown.kt`, `Rtf.kt` and `docxToHtml` convert the rest); their contents are the h1–h3 headings, and a contents entry or link with an id opens the page of the block it points at (`Chapter.anchorBlocks`). FB2 is converted to XHTML as foliate's `fb2.js` converts it (`shared/parser/Fb2.kt`): each part of the first `<body>` is a section of its own, every further body (the notes) one more out of the reading order, each with foliate's stylesheet and parsed as an EPUB document; note links (`fb2:<n>`) go to the note's section, `<binary>` pictures load as `fb2img:<id>` (`ParsedEpub.binaries`), and the file is read in the encoding its XML declaration names.

## 4. Rendering

Two paths, decided per format:

**Reflowable (EPUB/FB2/TXT/HTML).** Parser converts each chapter into styled blocks (paragraph, heading, image, blockquote). Layout engine paginates blocks into `Page` objects sized to the current viewport; page list cached per (book hash, viewport size, font settings). Recompute cache on font-size change or rotation. Pages are a **local cache only**: nothing that is persisted or synced may depend on a page number (see locator, section 5).

This is the largest technical risk in the project: pagination, hyphenation, a CSS subset, images and RTL in Compose. It is de-risked by a time-boxed spike (M0) before the rest of M1 is built.

Fallback if the Compose reflow engine stalls the project: render chapters in a WebView on mobile. Costs night-mode and font control, and does **not** cover desktop (needs a separate answer, e.g. a JVM WebView/JCEF, or accepting reduced desktop scope). Treat as an escape hatch only.

**Fixed (CBZ).** Direct page images, zoom + pan, no pagination engine.

### 4.1 Reader options

`DisplaySettings` (saved with the other display settings, on the Display sheet) carries options that change what is read or how pages sit:

- **Direction** (`auto`, `ltr`, `rtl`): `auto` follows the book; the others force the root's direction (`ParseOptions.rtl`). Books are read again when it changes.
- **Custom CSS** (`userCss`), under *Advanced* in the Display sheet (folded by default, with find and replace, so they are not changed by accident): added to the book's stylesheets as the last, strongest author rule (`Stylesheet.addUser`).
- **Find and replace** (`replacements`), also under *Advanced*: literal text rules, such as fixing a recurring typo, changing a name or dropping a stray character in a book's text; applied to every text run as the book is read (`ParseOptions.replaced`; TXT applies them to the raw text). Markup is never touched.
- `ParseOptions.needsReread` says when a changed setting needs the book parsed again (direction, CSS, replacements, or the colour scheme in a book whose CSS depends on it); `ReaderScreen` then calls `onOptionsChange` and `App` re-parses under the new options.
- **Columns** (1 or 2): two pages side by side. The layout column is half the page less each column's own margins; a turn moves two pages. Vertical books and scrolled flow ignore it.
- **Flow** (`paged`, `scrolled`): `scrolled` stacks the chapter's pages in one scrolling list (`LazyColumn`); the page on top is the reading position (`ReaderSession.showPage`), and the arrow keys still move a page, into the next chapter at the end. The list moves only up and down: horizontal swipes do nothing, and every tap just shows or hides the bars (no edge taps). Scrolling stays within a chapter.

## 5. Persistence

SQLDelight database per device, one schema for all targets. **The local database is the source of truth on a device.** It leaves the device only in a backup (section 6).

- `books` — id, book_hash, title, author, path, format, cover, added_at, deleted
- `positions` — book_hash, device_id, locator (JSON), lamport, updated_at
- `annotations` — id, book_hash, kind, anchor (JSON), text, note, created_at, device_id, lamport, deleted (tombstone flag)
- `settings_local` — key/value (font size, theme, brightness, layout options, library folder; per book `progress:`, `work:`, `info:` and `finished:` keys)
- `settings_synced` — key, value, lamport; unused since sync was removed, kept so the schema stays as it was
- `sync_state` — this device's own counter and Lamport clock, which stamps each record it writes

Databases live on device.

### 5.1 `book_hash`

`book_hash = SHA-256(file_size || first 64 KiB || last 64 KiB)`. Hashing a whole 200 MB file is too slow, and this is stable enough in practice. Two different files of a book get different hashes; all data stays keyed by `book_hash`.

**Edition linking.** Files of the same EPUB edition (re-zipped, re-downloaded) are linked by a work key: the OPF's unique `dc:identifier` plus the title, both normalized (`workKeyOf`). The key is stored per book as the local setting `work:<book_hash>` and travels as `workKey` in `library.json`. Reading a book considers every linked hash: the newest position wins, and annotations from all linked files show. Anything made in a different file is re-found by its quote, then by book-wide progression; its href/block/offset are never trusted, since they can point at different text. Unfound annotations show as "can't locate" instead of highlighting the wrong text. Different editions (different ISBNs) and books without an identifier stay separate.

### 5.2 Locator (stable position)

A locator describes a place in a book independent of viewport, font and device. It is the only position format stored.

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
- **Remove from library** hides the book (tombstone); the file stays in the folder.
- Only covers are stored in app storage.

## 6. Moving data between devices

Baca has no sync. An earlier design wrote per-device JSON files into a shared folder and merged them; it was removed on 2026-10-23 in favour of backup and restore, which is simpler to reason about and cannot leave devices disagreeing. Reading data moves between devices in a backup file you copy yourself.

### 6.1 Backup and restore

In Settings, under *Backup and restore*. **Create backup** writes one zip: `library.json` (every table: books, positions, annotations, settings) plus the imported book files and covers under `books/`. Paths are stored as file names, so a backup restores on any device or platform. Books read from a library folder that is a plain file path are included; on Android, where such books are document URIs, they are skipped, because the folder is already their copy.

**Restore** replaces the library after a confirmation. It first saves the current library as `before-restore.zip` next to the books folder, copies the book files, then swaps the tables in one transaction; an archive without `library.json` changes nothing. This device's identity (`device_id`) and library folder are kept, and its Lamport clock is advanced past the restored records so the next local edit gets a higher stamp than any restored one. Code: `shared/backup/LibraryBackup.kt`.

### 6.2 Lamport stamps

Every position, annotation and book record carries a `lamport` stamp: this device's counter plus one at each local write (`sync_state`). With no merge it only orders edits made on one device, and makes a restore safe: restored records keep their old stamps, and new edits are stamped above them.

## 7. Licensing

- KOReader itself: AGPL-3 — not used directly (it is a Lua application, not a renderer library).
- crengine (KOReader's EPUB engine): GPL — avoided; EPUB parsed in Kotlin instead.
- PDF: deferred. When it is added, pick the renderer with licensing in mind: muPDF is AGPL-3 (or commercial), which would make the app AGPL and conflicts with App Store distribution; platform renderers and PDFium avoid this.

## 8. Milestones

0. **M0 — reflow spike (time-boxed).** One real-world EPUB, paginated in Compose on one target, with a font-size change and a rotation, producing a locator and restoring it. Go/no-go on the Compose reflow engine; decide on the WebView fallback and its desktop story here, not later.
1. **M1 — core reading loop.** KMP skeleton, EPUB import + Okio unzip, reflow render of one chapter, position saved locally **as a locator**. The `positions` and `annotations` schemas and the locator/anchor formats are locked in this milestone, because changing them after M4 means data migrations. Largest chunk of the project; land it before anything else.
2. **M2 — library.** Book list, metadata, cover, delete, TXT/FB2 import, `book_hash`.
3. **M3 — fixed-layout.** CBZ via zip + image decoding; open, zoom, pan. PDF deferred.
4. **M4 — sync v1 (built, then removed 2026-10-23).** Per-device file layout, Lamport clock, pure merge functions, folder transport. Replaced by backup and restore (section 6).
5. **M5 — sync backend (dropped).** Out of scope with sync.

## 9. Open questions

1. WebView fallback: acceptable on mobile only, and what is the desktop answer if reflow fails? Decide at M0.

## 10. UI design guide

Mockups: `Baca App UI Design.html` in the repo root — 18 pages (phone: Library, Empty
library, Book detail, Add books, Import progress, Reader, Reader selection, Contents sheet,
Search in book, Display settings, Note editor, Notes, Sync, Sync errors (the sync pages predate its removal; backup and restore moved into Settings); desktop: Library,
Reader, Display settings popover, Notes). This replaces the earlier claude.ai artifact link
for the desktop variants; phone specs below still describe the same designs.

### 10.1 Principles

- **The page is the product.** Chrome stays quiet and neutral; the reading surface gets the warmth and the typography.
- **One accent.** Teal marks the primary action and the active state. Nothing else competes with it.
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
| `paper` | `#F4ECD8` | reader background (Paper theme, LibreraX's Book) |
| `readerInk` | `#5B4636` | reader text (Paper theme) |
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
- **Bottom nav (phone)**: Library, Notes, Bookmarks, Settings; active item uses accent and bold label. **Sidebar (desktop)**: 240 wide, same items.
- **Bottom sheet**: white, 20 top radius, handle, title row with "Done", dimmed reader behind (45% ink overlay).
- **Banner**: `accentTint` background, one line of text, one text button.
- **Annotation card** (desktop notes panel): highlight swatch, quoted text in Literata 15, optional note, meta line "Chapter · device".
- Icons: simple 2px-stroke line icons; icon-only buttons need an accessible label.

### 10.4 Screens

**Library** (phone and desktop): title, search, add-book action, filter chips (All / Reading / Finished; phone), "continue reading" card with resume action and progress, all-books grid (3 columns phone, 6 desktop) with per-book progress and a check on the cover of a finished book.

**Reader**: top bar with back, centered muted book title, contents (☰) and display settings (Aa); the phone's overflow (⋮) is designed but not implemented yet. Search (🔍): a sheet on phones, a popover on desktop; matches follow the query as it's typed (`searchBook` in `shared/domain/reader`, case-insensitive, curly and straight apostrophes alike, first 200 matches), each with its chapter and the text around it; picking one jumps to its page and tints the match. Text column: the page laid out as LibreraX lays it out (its `bridge.js`): side margins the padding's share of the page width, the page being the whole window on Android (tablets and landscape too) and the 680px column on desktop, a band above the text of 4dp for each percent of padding (24dp at 6%), the same below unless the footer is taller, and whole lines of body text with 3px to spare, with what is left under the last one split above and below in whole CSS px so the text sits centred (`evenLinesPx`; each chapter's lines are its first non-empty paragraph's, `chapterEvenPx`). Footer: **whole-book** progress — thin accent bar (1.5dp idle, 12dp while hovering/dragging, strip stays 24dp for the 44px touch target) plus "Page x of y" left and percent right, x/y counted over the whole book, not the current chapter (deviation from the mock, which labels per-chapter pages; user decision 2026-10-14). Under the page, always shown and in the reading theme (LibreraX's footer, `ReaderFooter.kt`): a progress track with a tick at each chapter start, "Pages left: n" in the chapter, the time and battery (read on each page turn; no battery on desktop) and "page / total"; the page gives up the footer's height so text never runs under it. For an EPUB both bars count pages as LibreraX does, in foliate's locations (`BookLocations`): each spine document weighs its bytes in the EPUB (at least 1500, none when `linear="no"`), the book has ceil(bytes / 1500) of them whatever the layout, and a page's place is its document's start plus page k of n's share of the document. The page shows floor(place × total) + 1 (the last location on the book's last page); "Pages left" is ceil((next chapter − place) × total − 0.01), at least 1, the chapters being the contents' entries at their top two levels (also the ticks), an entry in a document that holds several placed by the share of the document's text before its anchor; the percent, the saved reading progress and seeking (to the first page at or past a fraction, LibreraX's `goToFractionOf`) go by the same place, so opening a book lays out only the chapter shown. FB2 sections weigh what foliate counts: the section's XHTML in its template, less the picture sources; a text file, the length of the one HTML page LibreraX makes of it (a `<p>` for each run of lines between blank ones, lines kept apart by `<br>`). Page turns pass over documents out of the reading order (`linear="no"`, FB2's notes), which links and the contents still open. A book opened for the first time starts where foliate's `goToTextStart` puts it: the nav's `bodymatter` landmark, else the EPUB 2 guide's `text` reference, else the first document in the reading order. Highlights render with `highlight`. Desktop adds a right panel (340 wide) with Contents / Notes tabs.

The contents sheet and display sheet are single shared bottom-sheet layouts (per the mock's phone pages): handle, title + Done, and — for the display sheet — font-size stepper, font segmented control (accent fill on selection), theme color swatches (accent ring on selection), and line spacing and margin steppers. The contents sheet lists toc entries with right-aligned chapter percents; the current chapter is accent-bold with a dot and "You are here". Both use the same percent formula `(pagesBefore × 100)/(pagesInBook − 1)` as the footer so jump targets line up with the clicked row.

**Display settings** (bottom sheet on phone; a popover from Aa on desktop): steppers in LibreraX's steps for font size (50–250% of 16sp, 5% a step; 120% by default), line spacing (1.0–2.5, 0.1 a step; 1.7) and margins (padding 0–25% of the page width, 1% a step; 6%) and separate top and bottom margins (the band above and below the text, 4dp a percent each, same range; each follows the side margins until it is changed; the top one is none while the status bar is hidden, and scrolled flow has no top margin so text leaves at the screen's edge), font (Serif / Sans / Mono), theme (Paper / White / Dim / Black). Settings saved with the old named steps (sp sizes, Compact/Normal/Relaxed, Narrow/Medium/Wide) load at their nearest step. Footnote: settings stay on this device. These map to `settings_local` (section 5) and are never synced. Reader text colors for Dim and Black are not specified yet.

**Settings**: a menu with four rows, each opening its own page, with a back row to the menu (system Back does the same). *App*: app theme and library folder. *Reading display*: the same options as the reader's Display sheet, as defaults for every book. *Backup and restore*: Create backup and Restore, the result message under them, and a note on moving to another device (create here, copy the file, restore there). *About Baca*: version, what Baca is, source, acknowledgements, fonts.

**Book detail**: cover, title and author, a progress card with Start or Continue reading, a list of the book's facts (author, series, publisher, date, language, subjects, identifier, rights, format, size, length as pages and words, chapters, file name and where it is stored when in a library folder, added, last read; rows the book has nothing for are left out), the book's own description, the counts of highlights, notes and bookmarks in it, and Mark as finished and Remove from library. What the book says about itself is a `BookInfo` (`shared/parser/BookInfo.kt`: the OPF's or FB2's description block plus counts from reading it), stored as the `settings_local` key `info:<book_hash>` at import and read once for books imported before that.

### 10.5 Layout rules

- Reference sizes: phone 390×844, desktop 1280×900. Desktop layouts are fluid: sidebar and panels have fixed widths, the content area flexes, the reading column has a max width.
- On desktop widths below about 900px, collapse the sidebar to icons and move the notes panel into a sheet or popover.
- Page text is always paginated for the actual viewport; the footer page numbers come from the local page cache (EPUB: the book's document sizes), never from stored data of another device (sections 4 and 5.2).

### 10.6 Behavior notes

- **Annotation origin**: each annotation record keeps the `device_id` that wrote it.
- **Detached annotations** (section 5.3) appear in the notes list with a "can't locate in text" label and are not drawn on the page.

### 10.7 Accessibility

- Text 4.5:1 minimum; touch targets 44px; selected states differ by fill and label weight, not color alone.
- Real controls for every action, labels on icon-only buttons, and system font scaling respected in the UI. The reader uses its own font-size setting.
- Reader themes are LibreraX's six (White, Paper, Grey, Dim, Dusk, Black; `ReaderThemes`), whose text contrast is theirs, plus Ink: Paper's page with black text.

### 10.8 Implementation notes

- Define tokens once in a shared `AppTheme` (Compose `MaterialTheme` color scheme, typography and shapes) in `composeApp`; screens use theme values, not literals.
- Build reusable components (`BookCover`, `ProgressBar`, `SegmentedControl`, `Banner`, `AnnotationCard`) in `composeApp` and keep them free of business logic, per section 2.
- Window size classes choose phone vs desktop layouts; the screens themselves are shared.
- Implemented as of 2026-10-14: `AppTheme` tokens/typography/shapes, Reader (top bar, paged column, footer, tap zones), Contents and Display sheets, Library screen (search, filter chips, continue-reading card, 3-column grid with covers + progress, long-press delete), Empty-library page, Book detail (cover, progress card, Continue reading, format/size/added/last-read metadata, remove), the Add-books sheet with the Import progress screen (per-file statuses added/skipped/unsupported/broken; Android multi-pick, desktop single-file + folder import), import pipeline (hash dedupe, copy into app storage, EPUB cover extraction incl. wrapper-page covers, TXT/FB2 in `shared/parser/PlainTextParsers.kt` and `Fb2.kt`), and the CBZ fixed-layout reader (`CbzScreen`, zoom/pan via pinch, tap zones, thin seek bar, page-index locator through `CbzSession`/`loadFixedPosition`). Sync core shipped 2026-10-14 (Merge.kt/Lamport.kt/SyncBoundary.kt + tests; TECH 6.3/6.4/6.5). Sync UI shipped 2026-10-14 (SyncScreen + bottom nav/sidebar entry; no server row). Notes shipped 2026-10-14 (long-press toolbar → note editor → highlights render on pages; contents-sheet/panel Notes tab; Notes screen grouped by book; AnnotationEntry.color added). Text selection: a long press selects the word (`TextSelection` in `shared/domain/reader`), two handles drag its ends across the page's lines and blocks (`ReaderSelection.kt`), and the toolbar's color, Note and Copy act on exactly that range. Focus mode (Display → Full screen, on by default): on phones the status and navigation bars hide with the reader's bars. As in LibreraX, with the setting on the bars come up over the page when the reader's bars are shown, and don't make room: the page keeps clear only of a camera cutout up to Android 14, and of nothing from 15 (`stableReaderInsets`), so nothing re-paginates and the top and bottom margins in Display settings are the whole gap. The reader's top and bottom bars add the system bars' heights themselves. With it off the page stays clear of the bars. Desktop layout variants shipped (sidebar, 6-col grid, wide continue card with Resume, reader notes/contents panel at 340dp, 680dp max text column, popover display settings; below 900px everything falls back to the phone layout).

## 11. Revision notes

Changes from the previous draft:

- Positions and annotations moved from `chapter/page/percent` and bare `range` to a viewport-independent locator and anchor with re-anchoring fallbacks.
- (Historical, sync since removed.) Shared JSON files and `sync/journal.json` replaced by per-device files, which removes folder-sync conflicted copies; ordering uses a Lamport clock instead of wall clocks.
- Annotation edits, library re-adds and tombstone cleanup are now defined; settings split into local and synced.
- `zip4j`/`java.util.zip` replaced by Okio; CBZ is plain zip + images; PDF and muPDF removed from scope (deferred).
- Mobile folder access (SAF, security-scoped bookmarks), `book_hash`, and DB-vs-JSON source of truth are now specified.
- Added M0 reflow spike, locator lock in M1, and property-tested pure merge logic in M4.
- Added a UI design guide (section 10): tokens, components, screens, layout and accessibility rules, and the UI behaviors that depend on sync.
- 2026-10-23: sync removed. Backup and restore is the only way to move reading data between devices. Settings is a menu of App, Reading display, Backup and restore, and About pages; the Sync tab is gone; the book detail lists file, storage and annotation counts.
