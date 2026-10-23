package com.baca.shared.domain

import kotlin.test.Test
import kotlin.test.assertFailsWith

class AnnotationEntryTest {

    @Test
    fun annotationEntryRejectsUnknownKindAndEmptyAnchor() {
        val validAnchor = ReflowAnchorDto("h", PointDto(0, 0), PointDto(0, 1), "q")
        assertFailsWith<IllegalArgumentException> {
            AnnotationEntry("a", "banana", reflowAnchor = validAnchor, createdAt = 1, lamport = 1)
        }
        assertFailsWith<IllegalArgumentException> {
            AnnotationEntry("a", "note", createdAt = 1, lamport = 1)
        }
    }
}
