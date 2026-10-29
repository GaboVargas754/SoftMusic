package com.softmusic.app.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.softmusic.app.R

@UnstableApi
internal class CompactMediaNotificationProvider(
    context: Context,
    notificationId: Int,
    channelId: String,
) : DefaultMediaNotificationProvider(
    context,
    { notificationId },
    channelId,
    R.string.notification_channel_media_playback,
) {
    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val buttons = ImmutableList.builder<CommandButton>()

        if (
            playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ||
            playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)
        ) {
            val previousCommand = if (playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS)) {
                Player.COMMAND_SEEK_TO_PREVIOUS
            } else {
                Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
            }
            buttons.add(
                compactButton(
                    icon = CommandButton.ICON_PREVIOUS,
                    playerCommand = previousCommand,
                    displayName = "Anterior",
                    compactIndex = 0,
                ),
            )
        }

        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            buttons.add(
                compactButton(
                    icon = if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY,
                    playerCommand = Player.COMMAND_PLAY_PAUSE,
                    displayName = if (showPauseButton) "Pausar" else "Reproducir",
                    compactIndex = 1,
                ),
            )
        }

        if (
            playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ||
            playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT)
        ) {
            val nextCommand = if (playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT)) {
                Player.COMMAND_SEEK_TO_NEXT
            } else {
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            }
            buttons.add(
                compactButton(
                    icon = CommandButton.ICON_NEXT,
                    playerCommand = nextCommand,
                    displayName = "Siguiente",
                    compactIndex = 2,
                ),
            )
        }

        return buttons.build()
    }

    private fun compactButton(
        icon: Int,
        playerCommand: Int,
        displayName: String,
        compactIndex: Int,
    ): CommandButton {
        val extras = Bundle().apply {
            putInt(COMMAND_KEY_COMPACT_VIEW_INDEX, compactIndex)
        }
        return CommandButton.Builder(icon)
            .setPlayerCommand(playerCommand)
            .setDisplayName(displayName)
            .setExtras(extras)
            .build()
    }
}
