package com.softmusic.app.backup

import com.softmusic.app.player.AutoDjEffect
import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun roundTripPreservesCollectionsOrderUnicodeNullableFieldsAndPreferences() {
        val document = document().copy(
            songs = document().songs + BackupSong("song-2", null, "Otra canción 🎵", "", "", null, null, null),
            playlists = listOf(BackupPlaylist("Favoritas", listOf("song-2", "song-1"), "song-1"), BackupPlaylist("Vacía", emptyList())),
            favoriteKeys = listOf("song-2", "song-1"),
            preferences = BackupPreferences(true, 1.2f, PlaybackMode.Shuffle, SortMode.Title, false,
                "/storage/emulated/0/Música", setOf("/hidden/b", "/hidden/a"), true, DjMixMode.AutoDj, 5,
                AutoDjSettings(32, AutoDjEffect.EchoOut, 0.8f, false)),
        )

        val encoded = BackupJsonCodec.encode(document)
        assertEquals(document, BackupJsonCodec.decode(encoded))
        assertTrue(encoded.contains(BACKUP_FORMAT))
        assertEquals(document().copy(preferences = null), BackupJsonCodec.decode(BackupJsonCodec.encode(document())))
    }

    @Test
    fun foreignAndUnsupportedBackupsAreRejected() {
        rejected { json().put("format", "OtherPlayerBackup").toString() }
        rejected { json().put("formatVersion", 2).toString() }
        rejected { json().put("formatVersion", 0).toString() }
        rejected { JSONObject().put("songs", JSONArray()).toString() }
    }

    @Test
    fun damagedTruncatedAndNonJsonSyntaxAreRejected() {
        val encoded = BackupJsonCodec.encode(document())
        listOf("", "[]", encoded.dropLast(1), encoded + "{}", encoded + " trailing", "/*comment*/$encoded",
            encoded.replaceFirst("\"format\"", "format"), encoded.replaceFirst("\"format\"", "'format'"),
            encoded.dropLast(1) + ",}", encoded.replaceFirst("\"formatVersion\":1", "\"formatVersion\":01"),
            encoded.replaceFirst("\"formatVersion\":1", "\"formatVersion\":1,\"formatVersion\":1"),
            encoded.replaceFirst("\"formatVersion\":1", "\"formatVersion\":NaN"),
        ).forEach { candidate -> rejected { candidate } }
    }

    @Test
    fun stringBooleanIntegerObjectAndArrayTypesAreNeverCoerced() {
        listOf(
            json().put("formatVersion", "1"),
            json().put("createdAtEpochMs", "1790000000000"),
            json().put("createdAtEpochMs", 1.5),
            json().put("appVersion", 320),
            json().put("songs", JSONObject()),
            json().put("playlists", "[]"),
            json().put("favoriteKeys", JSONArray().put(1)),
            json().put("preferences", "{}"),
            json().put("preferences", JSONObject().put("highPerformanceMode", "true")),
            json().put("preferences", JSONObject().put("excludeSmallAudios", 1)),
            json().put("preferences", JSONObject().put("fontScale", "1.0")),
            json().put("preferences", JSONObject().put("djMixDurationSeconds", "8")),
            json().put("preferences", JSONObject().put("sortMode", 0)),
            json().put("preferences", JSONObject().put("autoDjSettings", JSONObject().put("syncEnabled", "false"))),
            json().put("preferences", JSONObject().put("autoDjSettings", JSONObject().put("beats", 16.5))),
        ).forEach { candidate -> rejected { candidate.toString() } }
        rejected { json().apply { getJSONArray("songs").getJSONObject(0).put("durationMs", "180000") }.toString() }
        rejected { json().apply { getJSONArray("songs").getJSONObject(0).put("fileName", false) }.toString() }
    }

    @Test
    fun duplicateAndDanglingReferencesAreRejectedBeforeImport() {
        rejected { json().put("favoriteKeys", JSONArray(listOf("missing"))).toString() }
        rejected { json().put("favoriteKeys", JSONArray(listOf("song-1", "song-1"))).toString() }
        rejected { json().apply { getJSONArray("songs").put(getJSONArray("songs").getJSONObject(0)) }.toString() }
        rejected { json().apply { getJSONArray("playlists").getJSONObject(0).put("songKeys", JSONArray(listOf("missing"))) }.toString() }
        rejected { json().apply { getJSONArray("playlists").getJSONObject(0).put("coverKey", "missing") }.toString() }
        rejected { json().put("preferences", JSONObject().put("hiddenFolderPaths", JSONArray(listOf("/same", "/same")))).toString() }
    }

    @Test
    fun fileCollectionAndStringLimitsAreEnforced() {
        rejected { " ".repeat(MAX_BACKUP_BYTES + 1) }
        rejected { "ñ".repeat(MAX_BACKUP_BYTES / 2 + 1) }
        rejected { json().put("favoriteKeys", JSONArray(List(MAX_BACKUP_SONGS + 1) { "song-1" })).toString() }
        rejected { json().put("playlists", JSONArray(List(MAX_BACKUP_PLAYLISTS + 1) { JSONObject().put("name", "Lista $it").put("songKeys", JSONArray()) })).toString() }
        rejected { json().apply { getJSONArray("songs").getJSONObject(0).put("title", "x".repeat(MAX_BACKUP_TEXT_LENGTH + 1)) }.toString() }
        rejected { json().apply { getJSONArray("songs").getJSONObject(0).put("folderPath", "x".repeat(MAX_BACKUP_PATH_LENGTH + 1)) }.toString() }
        rejected { json().put("createdAtEpochMs", -1).toString() }
        rejected { json().put("preferences", JSONObject().put("fontScale", 5)).toString() }
    }

    @Test
    fun optionalPreferencesUseDefaultsButUnknownEnumValuesAreRejected() {
        assertEquals(BackupPreferences(), BackupJsonCodec.decode(json().put("preferences", JSONObject()).toString()).preferences)
        rejected { json().put("preferences", JSONObject().put("playbackMode", "NewerMode")).toString() }
        rejected { json().put("preferences", JSONObject().put("autoDjSettings", JSONObject().put("effect", "FutureEffect"))).toString() }
        rejected { json().put("preferences", JSONObject().put("fontScale", JSONObject.NULL)).toString() }
    }

    @Test
    fun excessiveNestingAndEscapedDuplicateKeysAreRejected() {
        rejected { json().toString().dropLast(1) + ",\"unknown\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}" }
        rejected { "{\"format\":\"SoftMusicBackup\",\"for\\u006dat\":\"SoftMusicBackup\"}" }
        rejected { json().toString().replace("1790000000000", "9223372036854775808") }
    }

    private fun json() = JSONObject(BackupJsonCodec.encode(document()))

    private fun document() = BackupDocument(createdAtEpochMs = 1_790_000_000_000, appVersion = "3.2.0",
        songs = listOf(BackupSong("song-1", "track.mp3", "Título", "Artista", "Álbum", 180_000, 3_000_000, "/music")),
        playlists = listOf(BackupPlaylist("Mi lista", listOf("song-1"), "song-1")), favoriteKeys = listOf("song-1"))

    private fun rejected(json: () -> String) {
        try {
            BackupJsonCodec.decode(json())
            throw AssertionError("Se aceptó un respaldo no válido")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message?.isNotBlank() == true)
        }
    }
}
