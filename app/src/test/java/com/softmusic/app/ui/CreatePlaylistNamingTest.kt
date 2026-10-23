package com.softmusic.app.ui

import com.softmusic.app.data.MAX_PLAYLIST_NAME_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CreatePlaylistNamingTest {
    @Test
    fun suggestionStartsAfterTheCurrentPlaylistCount() {
        assertEquals("Mi playlist #1", suggestedPlaylistName(emptyList()))
        assertEquals("Mi playlist #3", suggestedPlaylistName(listOf("Favoritas", "Viajes")))
    }

    @Test
    fun suggestionSkipsNamesThatMatchAfterNormalization() {
        val existing = listOf("Favoritas", "  MÍ PLAYLIST #4  ", "Mi playlist #5")
        assertEquals("Mi playlist #6", suggestedPlaylistName(existing))
    }

    @Test
    fun validationRejectsBlankAndNormalizedDuplicates() {
        assertEquals("Escribe un nombre para la playlist", playlistNameValidationError(" \t ", emptyList()))
        assertEquals(
            "Ya existe una playlist con ese nombre",
            playlistNameValidationError("  CANCIONES PARA MÍ  ", listOf("Canciones para mi")),
        )
    }

    @Test
    fun validationUsesTheTrimmedNameAndSharedLengthLimit() {
        val longestName = "a".repeat(MAX_PLAYLIST_NAME_LENGTH)
        assertNull(playlistNameValidationError("  $longestName  ", emptyList()))
        assertEquals(
            "El nombre es demasiado largo",
            playlistNameValidationError(longestName + "a", emptyList()),
        )
        assertNull(playlistNameValidationError("Canciones nuevas", listOf("Canciones")))
    }
}
