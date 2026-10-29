package com.softmusic.app.player

/** Distinguishes a replacement/replay from edits that preserve the current playlist window. */
class ListeningHistoryVisitIdentity {
    private var previousSongId: Long? = null
    private var previousWindowUid: Any? = null
    private var previousPositionMs: Long? = null

    fun observe(
        songId: Long?,
        windowUid: Any?,
        positionMs: Long,
        playlistChanged: Boolean = false,
        repeated: Boolean = false,
    ): Boolean {
        val restartedAtBeginning = positionMs in 0L..1_000L &&
            previousPositionMs?.let { it - positionMs >= 500L } == true
        val sameSongNewWindow = playlistChanged && songId != null && songId == previousSongId &&
            previousWindowUid != null && windowUid != null && windowUid != previousWindowUid && restartedAtBeginning
        previousSongId = songId
        previousWindowUid = windowUid
        previousPositionMs = positionMs.takeIf { songId != null && it >= 0L }
        return repeated || sameSongNewWindow
    }
}
