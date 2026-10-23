package com.softmusic.app.player

import android.content.Context
import android.os.Build
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/** Keep decoded PCM on the processor chain, before Sonic's software playback-speed adjustment. */
@UnstableApi
class DjRenderersFactory(
    context: Context,
    private val processor: DjEffectsAudioProcessor,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setAudioProcessors(arrayOf(processor))
        .setEnableFloatOutput(false)
        .setEnableAudioTrackPlaybackParams(false)
        .setAudioOffloadSupportProvider { _, _ -> AudioOffloadSupport.DEFAULT_UNSUPPORTED }
        .build()
        .also {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                it.setOffloadMode(AudioSink.OFFLOAD_MODE_DISABLED)
            }
        }
}
