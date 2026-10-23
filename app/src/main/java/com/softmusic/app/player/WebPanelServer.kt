package com.softmusic.app.player

import android.content.Context
import com.softmusic.app.data.MusicPlaylist
import com.softmusic.app.data.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale

class WebPanelServer(
    private val context: Context,
    private val scope: CoroutineScope,
    private val snapshotProvider: () -> WebPanelSnapshot,
    private val commandHandler: suspend (String) -> Boolean,
) {
    private val serverScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
    private val random = SecureRandom()
    private val _state = MutableStateFlow(WebPanelServerState())
    val state: StateFlow<WebPanelServerState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var sessionToken: String? = null

    fun start() {
        if (_state.value.isRunning) return

        val token = newSessionToken()
        val socket = ServerSocket(0)
        serverSocket = socket
        sessionToken = token

        val host = localIpv4Address() ?: "127.0.0.1"
        val port = socket.localPort
        val url = "http://$host:$port/?token=$token"
        _state.value = WebPanelServerState(
            isRunning = true,
            url = url,
            host = "$host:$port",
            token = token,
        )

        acceptJob = serverScope.launch(Dispatchers.IO) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { handleClient(client) }
            }
        }
    }

    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        sessionToken = null
        _state.value = WebPanelServerState()
    }

    private suspend fun handleClient(socket: Socket) {
        socket.use { client ->
            val input = BufferedInputStream(client.getInputStream())
            val output = BufferedOutputStream(client.getOutputStream())
            val requestLine = input.readHttpLine() ?: return
            val requestParts = requestLine.split(' ', limit = 3)
            if (requestParts.size < 2) return

            val method = requestParts[0].uppercase(Locale.ROOT)
            val target = requestParts[1]
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = input.readHttpLine() ?: return
                if (line.isEmpty()) break
                val key = line.substringBefore(':', missingDelimiterValue = "").trim().lowercase(Locale.ROOT)
                val value = line.substringAfter(':', missingDelimiterValue = "").trim()
                if (key.isNotBlank()) headers[key] = value
            }

            val body = input.readHttpBody(headers["content-length"]?.toIntOrNull() ?: 0)
            val response = routeRequest(method, target, body)
            output.writeHttpResponse(response)
        }
    }

    private suspend fun routeRequest(method: String, target: String, body: String): HttpResponse {
        if (method == "OPTIONS") return HttpResponse.noContent()

        val uri = runCatching { URI("http://localhost$target") }.getOrNull()
            ?: return HttpResponse.badRequest("Ruta invalida")
        val path = uri.path.ifBlank { "/" }

        return when {
            method == "GET" && path == "/api/state" -> {
                if (!hasValidToken(uri)) return HttpResponse.unauthorized()
                HttpResponse.json(snapshotProvider().toJson())
            }
            method == "POST" && path == "/api/command" -> {
                if (!hasValidToken(uri)) return HttpResponse.unauthorized()
                val command = runCatching { JSONObject(body).optString("command") }.getOrNull().orEmpty()
                val accepted = if (command.isNotBlank()) commandHandler(command) else false
                HttpResponse.json(JSONObject().put("ok", accepted))
            }
            method == "GET" -> serveAsset(path)
            else -> HttpResponse.notFound()
        }
    }

    private fun serveAsset(path: String): HttpResponse {
        val assetPath = when (path) {
            "/", "/index.html" -> "index.html"
            "/styles.css" -> "styles.css"
            "/app.js" -> "app.js"
            else -> return HttpResponse.notFound()
        }
        val bytes = runCatching {
            context.assets.open(assetPath).use { it.readBytes() }
        }.getOrNull() ?: return HttpResponse.notFound()
        return HttpResponse(
            statusCode = 200,
            statusText = "OK",
            contentType = assetPath.contentType(),
            body = bytes,
        )
    }

    private fun hasValidToken(uri: URI): Boolean {
        val expected = sessionToken ?: return false
        val token = uri.rawQuery
            ?.split('&')
            ?.firstNotNullOfOrNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "")
                if (key == "token") {
                    URLDecoder.decode(part.substringAfter('=', missingDelimiterValue = ""), StandardCharsets.UTF_8.name())
                } else {
                    null
                }
            }
        return token == expected
    }

    private fun localIpv4Address(): String? {
        return Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface -> Collections.list(networkInterface.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }

    private fun newSessionToken(): String = (100_000 + random.nextInt(900_000)).toString()

    private fun String.contentType(): String = when {
        endsWith(".html") -> "text/html; charset=utf-8"
        endsWith(".css") -> "text/css; charset=utf-8"
        endsWith(".js") -> "application/javascript; charset=utf-8"
        else -> "application/octet-stream"
    }
}

data class WebPanelServerState(
    val isRunning: Boolean = false,
    val url: String? = null,
    val host: String? = null,
    val token: String? = null,
)

data class WebPanelSnapshot(
    val isPlaying: Boolean,
    val currentSong: Song?,
    val positionMs: Long,
    val durationMs: Long,
    val playbackMode: PlaybackMode,
    val queue: List<Song>,
    val playlists: List<MusicPlaylist>,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("isPlaying", isPlaying)
            .put("positionMs", positionMs)
            .put("durationMs", durationMs)
            .put("progressPercent", if (durationMs > 0L) (positionMs * 100L / durationMs).coerceIn(0L, 100L) else 0L)
            .put("playbackMode", playbackMode.name)
            .put("currentSong", currentSong?.toJson())
            .put("queue", JSONArray().apply { queue.forEach { put(it.toJson()) } })
            .put("playlists", JSONArray().apply { playlists.forEach { put(it.toJson()) } })
    }
}

private fun Song.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("artist", artist)
    .put("album", album)
    .put("durationMs", durationMs)
    .put("folderName", folderName)

private fun MusicPlaylist.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("name", name)
    .put("songCount", songIds.size)

private data class HttpResponse(
    val statusCode: Int,
    val statusText: String,
    val contentType: String,
    val body: ByteArray,
) {
    companion object {
        fun json(json: JSONObject): HttpResponse = HttpResponse(
            statusCode = 200,
            statusText = "OK",
            contentType = "application/json; charset=utf-8",
            body = json.toString().toByteArray(StandardCharsets.UTF_8),
        )

        fun noContent(): HttpResponse = HttpResponse(204, "No Content", "text/plain; charset=utf-8", ByteArray(0))

        fun badRequest(message: String): HttpResponse = text(400, "Bad Request", message)

        fun unauthorized(): HttpResponse = text(401, "Unauthorized", "Token invalido")

        fun notFound(): HttpResponse = text(404, "Not Found", "No encontrado")

        private fun text(statusCode: Int, statusText: String, message: String): HttpResponse = HttpResponse(
            statusCode = statusCode,
            statusText = statusText,
            contentType = "text/plain; charset=utf-8",
            body = message.toByteArray(StandardCharsets.UTF_8),
        )
    }
}

private fun BufferedInputStream.readHttpLine(): String? {
    val bytes = mutableListOf<Byte>()
    while (true) {
        val value = read()
        if (value < 0) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(StandardCharsets.UTF_8)
        if (value == '\n'.code) break
        if (value != '\r'.code) bytes += value.toByte()
    }
    return bytes.toByteArray().toString(StandardCharsets.UTF_8)
}

private fun BufferedInputStream.readHttpBody(contentLength: Int): String {
    if (contentLength <= 0) return ""
    val bytes = ByteArray(contentLength)
    var offset = 0
    while (offset < contentLength) {
        val readCount = read(bytes, offset, contentLength - offset)
        if (readCount < 0) break
        offset += readCount
    }
    return bytes.copyOf(offset).toString(StandardCharsets.UTF_8)
}

private fun BufferedOutputStream.writeHttpResponse(response: HttpResponse) {
    val header = buildString {
        append("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n")
        append("Content-Type: ${response.contentType}\r\n")
        append("Content-Length: ${response.body.size}\r\n")
        append("Access-Control-Allow-Origin: *\r\n")
        append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
        append("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
        append("Connection: close\r\n")
        append("\r\n")
    }
    write(header.toByteArray(StandardCharsets.UTF_8))
    write(response.body)
    flush()
}
