package com.example.exhibitionarchive.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagRulesTest {
    @Test
    fun normalizedName_trimsAndLowercases() {
        val tag = TagEntity(name = "  Kinetic Art  ")
        assertEquals("kinetic art", tag.normalizedName)
    }
}
