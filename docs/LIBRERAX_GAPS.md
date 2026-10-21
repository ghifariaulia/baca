# Where Baca's page differs from LibreraX

Baca draws EPUB pages with its own renderer; LibreraX hands the book to a WebView (foliate-js plus its `bridge.js`). Every way the two pages differ is listed here, worked through one at a time, top first. A fix lands with a test, and its row moves to **Done**.

Sources:
- LibreraX's `bridge.js`: `css()`, `pinType`, `applyPadding`, `evenLines`, `keepIndentsInside`, `markImages`.
- foliate's `paginator.js`: `setImageSize`.
- A browser's default stylesheet, which is what LibreraX shows wherever the book and `bridge.js` say nothing.

## Open

| # | Gap | LibreraX | Baca now |
|---|---|---|---|
| | None known. Add a row when a book shows one. | | |

## Done

| # | Gap | Fixed in |
|---|---|---|
| 1 | Text outside `<p>`, `<li>` and headings (`<div>`, `<section>`, `<td>`, `<dd>`…) was dropped; it's now a block of its own, `<head>` text aside | 2026-10-21 |
| 2 | Picture size: own size in CSS px, the book's `width`/`height`/`max-width` (%, px, em, vh, vw; a percent of the containing block), held to the column and the page, drawn `object-fit: contain`, placed by the block's alignment; a picture that doesn't fit moves whole to the next page instead of shrinking | 2026-10-21 |
| | Not a LibreraX gap but found here: lines with a link couldn't be long-pressed to select | 2026-10-21 |
| 23 | Defaults are a browser's stylesheet, as LibreraX leaves it (checked with a probe book): no paragraph indent, 1em between paragraphs, headings left-aligned at 2/1.5/1.17em with their own margins, `<pre>`/`<code>` monospace. Baca's old 1em indent, 0.2em gap and centred headings came from Librera's MuPDF engine, not LibreraX | 2026-10-21 |
| 3 | Lists: items set in 40px with •, ◦, ▪, 1., a., i. markers (`list-style-type`, `start`, `value`) | 2026-10-21 |
| 4 | Left and right margins and padding on every block (blockquote and `dd` 40px), not only with a border | 2026-10-21 |
| 5 | Spacing of 4em or more of the running text halved | 2026-10-21 |
| 6 | Headings at a browser's `normal` line height (1.2), not the reader's | 2026-10-21 |
| 10 | The book's own `text-indent`, positive or hanging, in its own amount; a hanging line kept inside the column | 2026-10-21 |
| 11 | Absolute font sizes (`px`, `pt`, `rem`, `large`) kept in proportion; no more 0.75–2× clamp | 2026-10-21 |
| 7 | `<sup>`, `<sub>`, `<small>` smaller, sup and sub raised and lowered (`vertical-align`) | 2026-10-21 |
| 8 | `text-transform` uppercase, lowercase, capitalize, letter for letter so offsets hold | 2026-10-21 |
| 9 | `<hr>`: a grey hairline, or the book's `background-image` ornament in its band (Kinki's scene breaks) | 2026-10-21 |
| 13 | `<pre>` / `white-space: pre`: spaces, tabs and line breaks kept, never justified | 2026-10-21 |
| 17 | `letter-spacing` | 2026-10-21 |
| 19 | Tables: each row a line with its cells side by side, `<th>` bold (a book's borders and columns aren't drawn) | 2026-10-21 |
| 20 | `px` spacing stays in CSS px (the screen's density), not growing with the font; spacing 4em of the running text or wider is halved into em of it | 2026-10-21 |
| 12 | `display`: `inline` headings run into the text after them, `block` spans start lines | 2026-10-21 |
| 14 | Selectors: id, attribute (`=`, `~=`, `|=`, `^=`, `$=`, `*=`, namespaced), `*`, `~`, `:first-child`, `:first-of-type`, `:not()`, `:root`, `:link` | 2026-10-21 |
| 15 | Pictures on white multiplied into a tinted page (LibreraX's `onPlainGround` readings) | 2026-10-21 |
| 16 | Hyphenation in the book's `dc:language` | 2026-10-21 |
| 18 | `float: left` drop caps, with the lines beside them shortened | 2026-10-21 |
| 21 | Pictures dimmed to 80% on dark themes | 2026-10-21 |
| 22 | Contents links (`nav`, `ol`, `ul`) stay underlined | 2026-10-21 |
| 24 | `<svg><image>` pictures (cover pages), in the SVG's box | 2026-10-21 |
| 25 | The book's own `color` on light themes, links in a browser's blue; theme ink and light-blue links on dark ones | 2026-10-21 |
| 26 | Percent margins and padding, as shares of the column | 2026-10-21 |
| 27 | `width: 60%; margin: auto` narrows and centres a block's text | 2026-10-21 |
| | Not a LibreraX gap but found here: a `vh`-sized picture crashed the reader before the page was measured | 2026-10-21 |
| | Book CSS cascade (`!important`, specificity, order) | earlier commits |
| | Vertical margins and padding, `border-left` rule, `float: right` | earlier commits |
| | Letter-sized inline images | `e0dfa71` |
| | Running-text size and face, monospace, underline | `665c756` |
| | Page margins, top band, whole lines centred | `550aa53` |
