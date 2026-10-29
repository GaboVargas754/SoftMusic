package com.softmusic.app.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class AppUpdateCheckerTest {
    @Test
    fun aNewVersionKeepsManifestDetailsAndClosesTheConnectionAndBody() = runBlocking {
        val connection = FakeConnection(body = """{
            "versionCode": 30,
            "versionName": "3.1.0",
            "releaseNotes": "Nuevas funciones",
            "updateUrl": "https://example.invalid/new.apk",
            "required": true
        }""")
        val result = client(connection).checkForUpdate()
        assertEquals(AppUpdateCheckResult.Available(AppUpdateInfo(30L, "3.1.0", "Nuevas funciones", "https://example.invalid/new.apk", true)), result)
        assertEquals("GET", connection.requestMethod)
        assertEquals(5_000, connection.connectTimeout)
        assertEquals(5_000, connection.readTimeout)
        assertFalse(connection.useCaches)
        assertEquals("application/json", connection.getRequestProperty("Accept"))
        assertEquals(1, connection.disconnects)
        assertTrue(connection.stream.closed)
    }

    @Test
    fun optionalManifestFieldsRetainTheirFallbacks() = runBlocking {
        for (body in listOf("""{"versionCode":30}""", """{"versionCode":30,"versionName":"  ","updateUrl":""}""")) {
            val result = client(FakeConnection(body = body)).checkForUpdate()
            assertEquals(AppUpdateCheckResult.Available(AppUpdateInfo(30L, "30", "", AppUpdateChecker.DEFAULT_UPDATE_URL, false)), result)
        }
    }

    @Test
    fun onlyValidCurrentOrOlderPositiveIntegerVersionsAreUpToDate() = runBlocking {
        for (version in listOf(1L, 28L, 29L)) {
            assertEquals(AppUpdateCheckResult.UpToDate, client(FakeConnection(body = """{"versionCode":$version}""")).checkForUpdate())
        }
        val largestVersion = client(FakeConnection(body = """{"versionCode":9223372036854775807}""")).checkForUpdate()
        assertEquals(Long.MAX_VALUE, (largestVersion as AppUpdateCheckResult.Available).info.versionCode)
    }

    @Test
    fun invalidManifestsNeverBecomeUpToDate() = runBlocking {
        val invalidBodies = listOf(
            "", "not json", "[]", "{", "{}", """{"versionCode":null}""",
            """{"versionCode":0}""", """{"versionCode":-1}""", """{"versionCode":29.5}""",
            """{"versionCode":30.0}""", """{"versionCode":"30"}""", """{"versionCode":true}""",
            """{"versionCode":{}}""", """{"versionCode":9223372036854775808}""",
            """{"versionCode":29} trailing""",
        )
        for (body in invalidBodies) {
            val connection = FakeConnection(body = body)
            assertEquals(body, AppUpdateCheckResult.Failure(AppUpdateFailureReason.InvalidResponse), client(connection).checkForUpdate())
            assertEquals(body, 1, connection.disconnects)
            assertTrue(body, connection.stream.closed)
        }
    }

    @Test
    fun unsuccessfulHttpResponsesAreReportedWithoutReadingTheirBodies() = runBlocking {
        for (status in listOf(301, 404, 503)) {
            val connection = FakeConnection(status = status, body = """{"versionCode":29}""")
            assertEquals(AppUpdateCheckResult.Failure(AppUpdateFailureReason.Http(status)), client(connection).checkForUpdate())
            assertEquals(0, connection.bodyRequests)
            assertEquals(1, connection.disconnects)
        }
    }

    @Test
    fun timeoutNetworkAndUnexpectedTransportErrorsStayDistinct() = runBlocking {
        val errors = listOf(
            SocketTimeoutException("timeout") to AppUpdateFailureReason.Timeout,
            IOException("offline") to AppUpdateFailureReason.Network,
            IllegalStateException("unexpected") to AppUpdateFailureReason.Unexpected,
        )
        for ((error, reason) in errors) {
            val connection = FakeConnection(responseFailure = error)
            assertEquals(AppUpdateCheckResult.Failure(reason), client(connection).checkForUpdate())
            assertEquals(1, connection.disconnects)
            val opening = AppUpdateCheckClient(29L) { throw error }
            assertEquals(AppUpdateCheckResult.Failure(reason), opening.checkForUpdate())
        }
    }

    @Test
    fun failureWhileReadingTheBodyStillClosesAndDisconnects() = runBlocking {
        val connection = FakeConnection(readFailure = IOException("interrupted body"))
        assertEquals(AppUpdateCheckResult.Failure(AppUpdateFailureReason.Network), client(connection).checkForUpdate())
        assertTrue(connection.stream.closed)
        assertEquals(1, connection.disconnects)
    }

    @Test
    fun cancellationPropagatesAndStillCleansUpAcquiredResources() = runBlocking {
        for (duringBody in listOf(false, true)) {
            val cancellation = CancellationException("cancel update check")
            val connection = if (duringBody) FakeConnection(readFailure = cancellation) else FakeConnection(responseFailure = cancellation)
            var caught: CancellationException? = null
            try {
                client(connection).checkForUpdate()
            } catch (failure: CancellationException) {
                caught = failure
            }
            assertNotNull(caught)
            assertEquals(cancellation.message, caught?.message)
            assertEquals(1, connection.disconnects)
            if (duringBody) assertTrue(connection.stream.closed)
        }
    }

    private fun client(connection: FakeConnection) = AppUpdateCheckClient(29L) { connection }

    private class FakeConnection(
        private val status: Int = 200,
        body: String = """{"versionCode":29}""",
        private val responseFailure: Exception? = null,
        readFailure: Exception? = null,
    ) : HttpURLConnection(URL("https://example.invalid/update.json")) {
        var disconnects = 0
        var bodyRequests = 0
        val stream = TrackedInputStream(body, readFailure)
        override fun connect() = Unit
        override fun usingProxy(): Boolean = false
        override fun disconnect() { disconnects++ }
        override fun getResponseCode(): Int { responseFailure?.let { throw it }; return status }
        override fun getInputStream(): InputStream { bodyRequests++; return stream }
    }

    private class TrackedInputStream(body: String, private val failure: Exception?) : InputStream() {
        private val contents = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        var closed = false
        override fun read(): Int { failure?.let { throw it }; return contents.read() }
        override fun read(bytes: ByteArray, offset: Int, length: Int): Int { failure?.let { throw it }; return contents.read(bytes, offset, length) }
        override fun close() { closed = true; contents.close() }
    }
}
