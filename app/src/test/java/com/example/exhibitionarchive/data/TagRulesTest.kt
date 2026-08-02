package com.example.exhibitionarchive.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagRulesTest {
    @Test
    fun normalizedName_trimsAndLowercases() {
        val tag = TagEntity(name = "  Kinetic Art  ")
        assertEquals("kinetic art", tag.normalizedName)
    }

    @Test
    fun artistNormalizedName_trimsAndLowercases() {
        val artist = ArtistEntity(name = "  Nam June PAIK  ")
        assertEquals("nam june paik", artist.normalizedName)
    }
}
