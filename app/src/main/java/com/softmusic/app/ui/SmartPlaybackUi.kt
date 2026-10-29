package com.softmusic.app.ui

import com.softmusic.app.player.PlayerUiState

/** Smart shuffle follows the active playlist independently of the DJ mix configuration. */
internal val PlayerUiState.smartShuffleAvailable: Boolean
    get() = smartShuffleEnabled
