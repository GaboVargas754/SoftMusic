package com.softmusic.app.desktop.integration

import com.softmusic.app.data.Song
import com.softmusic.app.player.PlaybackMode
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

class DesktopMprisService {
    private val actions = AtomicReference(DesktopMprisActions())
    private val state = AtomicReference(DesktopMprisState())
    @Volatile private var connection: DBusConnection? = null
    private val exportedObject = MprisObject()

    fun start() {
        if (!isLinux()) return
        if (connection != null) return

        runCatching {
            val dbusConnection = DBusConnectionBuilder.forSessionBus().build()
            dbusConnection.requestBusName(MPRIS_BUS_NAME)
            dbusConnection.exportObject(MPRIS_OBJECT_PATH, exportedObject)
            connection = dbusConnection
        }.onFailure { throwable ->
            System.err.println("SoftMusic MPRIS disabled: ${throwable.message ?: throwable::class.simpleName}")
        }
    }

    fun updateActions(nextActions: DesktopMprisActions) {
        actions.set(nextActions)
    }

    fun updateState(nextState: DesktopMprisState) {
        val previousState = state.getAndSet(nextState)
        if (previousState.song?.id != nextState.song?.id ||
            previousState.isPlaying != nextState.isPlaying ||
            previousState.playbackMode != nextState.playbackMode ||
            previousState.canGoNext != nextState.canGoNext ||
            previousState.canGoPrevious != nextState.canGoPrevious ||
            previousState.canPlay != nextState.canPlay
        ) {
            emitPlayerPropertiesChanged()
        }
    }

    fun close() {
        val dbusConnection = connection ?: return
        connection = null
        runCatching { dbusConnection.unExportObject(MPRIS_OBJECT_PATH) }
        runCatching { dbusConnection.releaseBusName(MPRIS_BUS_NAME) }
        runCatching { dbusConnection.disconnect() }
    }

    private fun emitPlayerPropertiesChanged() {
        val dbusConnection = connection ?: return
        val changedProperties = exportedObject.playerProperties(includePosition = false)
        runCatching {
            dbusConnection.sendMessage(
                Properties.PropertiesChanged(
                    MPRIS_OBJECT_PATH,
                    MPRIS_PLAYER_INTERFACE,
                    changedProperties,
                    emptyList(),
                ),
            )
        }
    }

    private fun isLinux(): Boolean {
        return System.getProperty("os.name")
            .orEmpty()
            .lowercase(Locale.ROOT)
            .contains("linux")
    }

    inner class MprisObject : MprisRoot, MprisPlayer, Properties {
        override fun Raise() = Unit

        override fun Quit() = Unit

        override fun Next() {
            actions.get().onNext()
        }

        override fun Previous() {
            actions.get().onPrevious()
        }

        override fun Pause() {
            actions.get().onPause()
        }

        override fun PlayPause() {
            actions.get().onPlayPause()
        }

        override fun Stop() {
            actions.get().onStop()
        }

        override fun Play() {
            actions.get().onPlay()
        }

        override fun Seek(offset: Long) {
            actions.get().onSeekBy(offset / MICROSECONDS_PER_MILLISECOND)
        }

        override fun SetPosition(trackId: DBusPath, position: Long) {
            val current = state.get().song ?: return
            if (trackId.path != current.trackObjectPath()) return
            actions.get().onSeekTo(position / MICROSECONDS_PER_MILLISECOND)
        }

        override fun OpenUri(uri: String) = Unit

        @Suppress("UNCHECKED_CAST")
        override fun <A> Get(interfaceName: String, propertyName: String): A {
            return propertiesFor(interfaceName)[propertyName]
                ?.let { it.value as A }
                ?: throw DBusExecutionException("Unknown MPRIS property: $interfaceName.$propertyName")
        }

        override fun GetAll(interfaceName: String): Map<String, Variant<*>> {
            return propertiesFor(interfaceName)
        }

        override fun <A> Set(interfaceName: String, propertyName: String, value: A) = Unit

        override fun getObjectPath(): String = MPRIS_OBJECT_PATH

        override fun isRemote(): Boolean = false

        fun playerProperties(includePosition: Boolean): Map<String, Variant<*>> {
            val currentState = state.get()
            val properties = linkedMapOf(
                "PlaybackStatus" to variant(currentState.playbackStatus()),
                "LoopStatus" to variant(currentState.playbackMode.loopStatus()),
                "Rate" to variant(1.0),
                "Shuffle" to variant(currentState.playbackMode == PlaybackMode.Shuffle),
                "Metadata" to variant(currentState.metadata(), "a{sv}"),
                "Volume" to variant(1.0),
                "MinimumRate" to variant(1.0),
                "MaximumRate" to variant(1.0),
                "CanGoNext" to variant(currentState.canGoNext),
                "CanGoPrevious" to variant(currentState.canGoPrevious),
                "CanPlay" to variant(currentState.canPlay),
                "CanPause" to variant(currentState.song != null),
                "CanSeek" to variant(currentState.song != null && currentState.durationMs > 0L),
                "CanControl" to variant(true),
            )
            if (includePosition) {
                properties["Position"] = variant(currentState.positionMs.toMicroseconds())
            }
            return properties
        }

        private fun propertiesFor(interfaceName: String): Map<String, Variant<*>> {
            return when (interfaceName) {
                MPRIS_ROOT_INTERFACE -> rootProperties()
                MPRIS_PLAYER_INTERFACE -> playerProperties(includePosition = true)
                else -> throw DBusExecutionException("Unknown MPRIS interface: $interfaceName")
            }
        }

        private fun rootProperties(): Map<String, Variant<*>> {
            return linkedMapOf(
                "CanQuit" to variant(false),
                "CanRaise" to variant(false),
                "HasTrackList" to variant(false),
                "Identity" to variant("SoftMusic"),
                "DesktopEntry" to variant("softmusic"),
                "SupportedUriSchemes" to variant(arrayOf("file"), "as"),
                "SupportedMimeTypes" to variant(SUPPORTED_MIME_TYPES, "as"),
            )
        }
    }

    private fun DesktopMprisState.playbackStatus(): String {
        return when {
            song == null -> "Stopped"
            isPlaying -> "Playing"
            else -> "Paused"
        }
    }

    private fun DesktopMprisState.metadata(): Map<String, Variant<*>> {
        val currentSong = song ?: return mapOf("mpris:trackid" to variant(DBusPath(NO_TRACK_OBJECT_PATH), "o"))
        return linkedMapOf(
            "mpris:trackid" to variant(DBusPath(currentSong.trackObjectPath()), "o"),
            "xesam:title" to variant(currentSong.title),
            "xesam:artist" to variant(arrayOf(currentSong.artist), "as"),
            "xesam:album" to variant(currentSong.album),
            "xesam:url" to variant(currentSong.uri),
            "mpris:length" to variant((durationMs.takeIf { it > 0L } ?: currentSong.durationMs).toMicroseconds(), "x"),
        ).apply {
            currentSong.artworkUri?.let { artworkUri -> put("mpris:artUrl", variant(artworkUri)) }
        }
    }

    private fun PlaybackMode.loopStatus(): String = when (this) {
        PlaybackMode.RepeatCurrent -> "Track"
        PlaybackMode.RepeatList -> "Playlist"
        PlaybackMode.Ordered,
        PlaybackMode.Shuffle -> "None"
    }

    private fun Song.trackObjectPath(): String = "/com/softmusic/Track/$id"

    private fun Long.toMicroseconds(): Long = coerceAtLeast(0L) * MICROSECONDS_PER_MILLISECOND

    private fun variant(value: Any, signature: String? = null): Variant<*> {
        return if (signature == null) Variant(value) else Variant(value, signature)
    }

    private companion object {
        const val MPRIS_BUS_NAME = "org.mpris.MediaPlayer2.softmusic"
        const val MPRIS_OBJECT_PATH = "/org/mpris/MediaPlayer2"
        const val MPRIS_ROOT_INTERFACE = "org.mpris.MediaPlayer2"
        const val MPRIS_PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
        const val NO_TRACK_OBJECT_PATH = "/org/mpris/MediaPlayer2/TrackList/NoTrack"
        const val MICROSECONDS_PER_MILLISECOND = 1_000L
        val SUPPORTED_MIME_TYPES = arrayOf(
            "audio/mpeg",
            "audio/flac",
            "audio/wav",
            "audio/ogg",
            "audio/opus",
            "audio/mp4",
            "audio/aac",
            "audio/x-aiff",
            "audio/x-ms-wma",
        )
    }
}

data class DesktopMprisActions(
    val onPlay: () -> Unit = {},
    val onPause: () -> Unit = {},
    val onPlayPause: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onPrevious: () -> Unit = {},
    val onSeekTo: (Long) -> Unit = {},
    val onSeekBy: (Long) -> Unit = {},
)

data class DesktopMprisState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackMode: PlaybackMode = PlaybackMode.Ordered,
    val canGoNext: Boolean = false,
    val canGoPrevious: Boolean = false,
    val canPlay: Boolean = false,
)

@DBusInterfaceName("org.mpris.MediaPlayer2")
interface MprisRoot : DBusInterface {
    fun Raise()
    fun Quit()
}

@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
interface MprisPlayer : DBusInterface {
    fun Next()
    fun Previous()
    fun Pause()
    fun PlayPause()
    fun Stop()
    fun Play()
    fun Seek(offset: Long)
    fun SetPosition(trackId: DBusPath, position: Long)
    fun OpenUri(uri: String)
}
