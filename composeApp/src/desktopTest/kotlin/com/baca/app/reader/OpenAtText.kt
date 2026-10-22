package com.baca.app.reader

import com.baca.shared.parser.Block
import com.baca.shared.parser.ParsedEpub

/**
 * The book opening at its first chapter with text, as a `bodymatter` landmark would have it:
 * Frankenstein has none, so it opens on its cover page as LibreraX opens it.
 */
internal fun ParsedEpub.openingAtText(): ParsedEpub =
    copy(textStart = chapters.first { chapter -> chapter.blocks.any { it is Block.Text } }.href)
