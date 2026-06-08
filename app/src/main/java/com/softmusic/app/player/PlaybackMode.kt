package com.softmusic.app.player

enum class PlaybackMode(val label: String) {
    Ordered("Reproducir en orden"),
    RepeatList("Repetir lista"),
    RepeatCurrent("Repetir canción actual"),
    Shuffle("Aleatorio"),
}
