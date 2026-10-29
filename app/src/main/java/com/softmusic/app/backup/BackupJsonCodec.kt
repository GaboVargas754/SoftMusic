package com.softmusic.app.backup

import com.softmusic.app.player.AutoDjSettings
import com.softmusic.app.player.DjMixMode
import com.softmusic.app.player.PlaybackMode
import com.softmusic.app.player.SortMode
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** JSON is deliberately kept in the Android module; the portable model has no JSON dependency. */
internal object BackupJsonCodec {
    fun encode(document: BackupDocument): String {
        validateBackupDocument(document)
        val root = JSONObject()
            .put("format", BACKUP_FORMAT)
            .put("formatVersion", document.formatVersion)
            .put("createdAtEpochMs", document.createdAtEpochMs)
            .put("appVersion", document.appVersion)
            .put("songs", JSONArray().apply {
                document.songs.forEach { song ->
                    put(JSONObject().put("key", song.key).putNullable("fileName", song.fileName)
                        .put("title", song.title).put("artist", song.artist).put("album", song.album)
                        .putNullable("durationMs", song.durationMs).putNullable("fileSizeBytes", song.fileSizeBytes)
                        .putNullable("folderPath", song.folderPath))
                }
            })
            .put("playlists", JSONArray().apply {
                document.playlists.forEach { playlist ->
                    put(JSONObject().put("name", playlist.name).put("songKeys", JSONArray(playlist.songKeys))
                        .putNullable("coverKey", playlist.coverKey))
                }
            })
            .put("favoriteKeys", JSONArray(document.favoriteKeys))
            .putNullable("preferences", document.preferences?.let(::encodePreferences))
        return root.toString().also { requireByteLimit(it) }
    }

    fun decode(json: String): BackupDocument {
        requireByteLimit(json)
        try {
            // Android's JSONTokener accepts several non-JSON extensions. Check actual JSON syntax first.
            StrictJsonSyntax(json).validate()
            val root = JSONObject(json)
            require(root.string("format") == BACKUP_FORMAT) { "El archivo no es un respaldo de SoftMusic." }
            val version = root.int("formatVersion")
            require(version == BACKUP_FORMAT_VERSION) { "La versión del respaldo no es compatible con esta versión de SoftMusic." }
            val songsJson = root.array("songs", MAX_BACKUP_SONGS)
            val playlistsJson = root.array("playlists", MAX_BACKUP_PLAYLISTS)
            val favorites = root.stringList("favoriteKeys", MAX_BACKUP_SONGS)
            var entryCount = songsJson.length().toLong() + favorites.size
            val songs = (0 until songsJson.length()).map { index ->
                songsJson.objectAt(index).let { song ->
                    BackupSong(song.string("key"), song.nullableString("fileName"), song.string("title"),
                        song.string("artist"), song.string("album"), song.nullableLong("durationMs"),
                        song.nullableLong("fileSizeBytes"), song.nullableString("folderPath"))
                }
            }
            val playlists = (0 until playlistsJson.length()).map { index ->
                playlistsJson.objectAt(index).let { playlist ->
                    val songKeys = playlist.stringList("songKeys", MAX_BACKUP_ENTRIES)
                    entryCount += songKeys.size
                    require(entryCount <= MAX_BACKUP_ENTRIES) { "El respaldo contiene demasiadas entradas." }
                    BackupPlaylist(playlist.string("name"), songKeys, playlist.nullableString("coverKey"))
                }
            }
            val preferences = if (!root.has("preferences") || root.isNull("preferences")) null
                else decodePreferences(root.objectValue("preferences"))
            return BackupDocument(version, root.long("createdAtEpochMs"), root.string("appVersion"), songs,
                playlists, favorites, preferences).also(::validateBackupDocument)
        } catch (error: JSONException) {
            throw IllegalArgumentException("El archivo de respaldo está dañado o incompleto.", error)
        }
    }

    private fun encodePreferences(preferences: BackupPreferences): JSONObject = JSONObject()
        .put("highPerformanceMode", preferences.highPerformanceMode)
        .put("fontScale", preferences.fontScale.toDouble())
        .put("playbackMode", preferences.playbackMode.name)
        .put("sortMode", preferences.sortMode.name)
        .put("excludeSmallAudios", preferences.excludeSmallAudios)
        .putNullable("defaultFolderPath", preferences.defaultFolderPath)
        .put("hiddenFolderPaths", JSONArray(preferences.hiddenFolderPaths.sorted()))
        .put("djModeEnabled", preferences.djModeEnabled)
        .put("djMixMode", preferences.djMixMode.name)
        .put("djMixDurationSeconds", preferences.djMixDurationSeconds)
        .put("autoDjSettings", JSONObject().put("beats", preferences.autoDjSettings.beats)
            .put("effect", preferences.autoDjSettings.effect.name)
            .put("intensity", preferences.autoDjSettings.intensity.toDouble())
            .put("syncEnabled", preferences.autoDjSettings.syncEnabled))

    private fun decodePreferences(json: JSONObject): BackupPreferences {
        val defaults = BackupPreferences()
        val hiddenPaths = if (json.has("hiddenFolderPaths")) json.stringList("hiddenFolderPaths", MAX_BACKUP_SONGS) else emptyList()
        require(hiddenPaths.distinct().size == hiddenPaths.size) { "El respaldo contiene carpetas ocultas duplicadas." }
        val autoDj = if (json.has("autoDjSettings")) {
            json.objectValue("autoDjSettings").let { settings ->
                AutoDjSettings(
                    beats = settings.optionalInt("beats", defaults.autoDjSettings.beats),
                    effect = settings.optionalEnum("effect", defaults.autoDjSettings.effect),
                    intensity = settings.optionalFloat("intensity", defaults.autoDjSettings.intensity),
                    syncEnabled = settings.optionalBoolean("syncEnabled", defaults.autoDjSettings.syncEnabled),
                )
            }
        } else defaults.autoDjSettings
        return BackupPreferences(
            highPerformanceMode = json.optionalBoolean("highPerformanceMode", defaults.highPerformanceMode),
            fontScale = json.optionalFloat("fontScale", defaults.fontScale),
            playbackMode = json.optionalEnum<PlaybackMode>("playbackMode", defaults.playbackMode),
            sortMode = json.optionalEnum<SortMode>("sortMode", defaults.sortMode),
            excludeSmallAudios = json.optionalBoolean("excludeSmallAudios", defaults.excludeSmallAudios),
            defaultFolderPath = json.nullableString("defaultFolderPath"),
            hiddenFolderPaths = hiddenPaths.toSet(),
            djModeEnabled = json.optionalBoolean("djModeEnabled", defaults.djModeEnabled),
            djMixMode = json.optionalEnum<DjMixMode>("djMixMode", defaults.djMixMode),
            djMixDurationSeconds = json.optionalInt("djMixDurationSeconds", defaults.djMixDurationSeconds),
            autoDjSettings = autoDj,
        )
    }

    private fun requireByteLimit(json: String) {
        require(json.length <= MAX_BACKUP_BYTES && json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
            "El respaldo supera el límite de 10 MiB."
        }
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.string(key: String): String = get(key) as? String ?: invalidType(key)
    private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else string(key)
    private fun JSONObject.long(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> invalidType(key)
    }
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else long(key)
    private fun JSONObject.int(key: String): Int = long(key).let { value ->
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "El valor de '$key' está fuera de rango." }
        value.toInt()
    }
    private fun JSONObject.objectValue(key: String): JSONObject = get(key) as? JSONObject ?: invalidType(key)
    private fun JSONObject.array(key: String, limit: Int): JSONArray = (get(key) as? JSONArray ?: invalidType(key)).also {
        require(it.length() <= limit) { "El respaldo contiene demasiadas entradas en '$key'." }
    }
    private fun JSONObject.stringList(key: String, limit: Int): List<String> = array(key, limit).let { array ->
        (0 until array.length()).map { index -> array.get(index) as? String ?: invalidType(key) }
    }
    private fun JSONArray.objectAt(index: Int): JSONObject = get(index) as? JSONObject ?: invalidType("entrada $index")
    private fun JSONObject.optionalBoolean(key: String, default: Boolean): Boolean =
        if (has(key)) get(key) as? Boolean ?: invalidType(key) else default
    private fun JSONObject.optionalInt(key: String, default: Int): Int = if (has(key)) int(key) else default
    private fun JSONObject.optionalFloat(key: String, default: Float): Float {
        if (!has(key)) return default
        val value = get(key) as? Number ?: invalidType(key)
        return value.toFloat().also { require(it.isFinite()) { "El valor de '$key' no es un número finito." } }
    }
    private inline fun <reified T : Enum<T>> JSONObject.optionalEnum(key: String, default: T): T {
        if (!has(key)) return default
        val name = string(key)
        return enumValues<T>().firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("El valor de '$key' no es compatible con SoftMusic.")
    }
    private fun invalidType(key: String): Nothing = throw IllegalArgumentException("El campo '$key' tiene un tipo de dato no válido.")
}

/** A bounded grammar pass also rejects duplicate keys consistently on Android and the JVM. */
private class StrictJsonSyntax(private val input: String) {
    private var index = 0

    fun validate() {
        whitespace()
        require(index < input.length && input[index] == '{') { "El archivo no es un respaldo JSON de SoftMusic." }
        value(0)
        whitespace()
        check(index == input.length)
    }

    private fun value(depth: Int) {
        check(depth <= 32)
        whitespace()
        check(index < input.length)
        when (input[index]) {
            '{' -> objectValue(depth + 1)
            '[' -> arrayValue(depth + 1)
            '"' -> string()
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> number()
            else -> malformed()
        }
    }

    private fun objectValue(depth: Int) {
        index++
        whitespace()
        if (consume('}')) return
        val keys = HashSet<String>()
        do {
            whitespace()
            check(index < input.length && input[index] == '"')
            check(keys.add(string()))
            whitespace()
            check(consume(':'))
            value(depth)
            whitespace()
            if (consume('}')) return
            check(consume(','))
        } while (true)
    }

    private fun arrayValue(depth: Int) {
        index++
        whitespace()
        if (consume(']')) return
        do {
            value(depth)
            whitespace()
            if (consume(']')) return
            check(consume(','))
        } while (true)
    }

    private fun string(): String {
        check(consume('"'))
        val result = StringBuilder()
        while (index < input.length) {
            val char = input[index++]
            when {
                char == '"' -> return result.toString()
                char == '\\' -> {
                    check(index < input.length)
                    result.append(when (val escaped = input[index++]) {
                        '"', '\\', '/' -> escaped
                        'b' -> '\b'
                        'f' -> '\u000c'
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        'u' -> {
                            check(index + 4 <= input.length)
                            val code = input.substring(index, index + 4)
                            check(code.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })
                            index += 4
                            code.toInt(16).toChar()
                        }
                        else -> malformed()
                    })
                }
                char < ' ' -> malformed()
                else -> result.append(char)
            }
        }
        malformed()
    }

    private fun number() {
        val start = index
        consume('-')
        check(index < input.length)
        if (!consume('0')) {
            check(index < input.length && input[index] in '1'..'9')
            while (index < input.length && input[index] in '0'..'9') index++
        }
        if (consume('.')) digits()
        if (consume('e') || consume('E')) {
            if (!consume('+')) consume('-')
            digits()
        }
        check(index - start <= 128)
    }

    private fun digits() {
        val start = index
        while (index < input.length && input[index] in '0'..'9') index++
        check(index > start)
    }

    private fun literal(value: String) {
        check(input.regionMatches(index, value, 0, value.length))
        index += value.length
    }

    private fun whitespace() {
        while (index < input.length && input[index] in " \t\r\n") index++
    }

    private fun consume(char: Char): Boolean = if (index < input.length && input[index] == char) {
        index++
        true
    } else false

    private fun check(condition: Boolean) { if (!condition) malformed() }
    private fun malformed(): Nothing = throw IllegalArgumentException("El archivo de respaldo está dañado o incompleto.")
}
