package com.baca.shared.parser

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.openZip

/** One fixed-layout book: the zip's image entries in reading order. */
data class CbzBook(
    val title: String,
    /** Zip paths (locator hrefs, TECH 5.2 fixed layout), natural-sorted. */
    val pages: List<String>,
)

private val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/**
 * CBZ = zip of images (TECH 9.1). Entries sort naturally (page2 < page10);
 * anything that is not an image (cover.xml, __MACOSX, .nfo) is dropped.
 */
fun parseCbz(path: Path, fileName: String): CbzBook {
    val zip = FileSystem.SYSTEM.openZip(path)
    val entries = zip.listRecursively("/".toPath())
        .filter { "__MACOSX" !in it.toString() }
        .filter { it.name.substringAfterLast('.').lowercase() in imageExtensions }
         .toList().map { it.toString() }
        .sortedWith(pageOrder())
    return CbzBook(title = fileName.substringBeforeLast('.'), pages = entries)
}

/** "page2" < "page10": split digit runs and compare segment by segment. */
internal fun pageOrder(): Comparator<String> = Comparator { a, b ->
    val av = digitRuns(a)
    val bv = digitRuns(b)
    for (i in 0 until minOf(av.size, bv.size)) {
        val an = av[i].toLongOrNull()
        val bn = bv[i].toLongOrNull()
        val cmp = if (an != null && bn != null) an.compareTo(bn) else av[i].compareTo(bv[i], ignoreCase = true)
        if (cmp != 0) return@Comparator cmp
    }
    av.size.compareTo(bv.size)
}

/** "page10.jpg" -> ["page", "10", ".jpg"] — digit runs kept as segments. */
private fun digitRuns(s: String): List<String> =
    Regex("\\d+|\\D+").findAll(s).map { it.value }.toList()
