package com.baca.shared.domain

/** Progress from which a book counts as read through; the last pages are often notes or adverts. */
const val READ_THROUGH = 0.98f

/** Whether a book is finished: marked so by the reader, or read through. */
fun isFinished(markedFinished: Boolean, progress: Float?): Boolean =
    markedFinished || (progress ?: 0f) >= READ_THROUGH

/** The progress a book shows: all of it once finished (marked, or read through), else how far it is read. */
fun shownProgress(markedFinished: Boolean, progress: Float?): Float =
    if (isFinished(markedFinished, progress)) 1f else progress ?: 0f
