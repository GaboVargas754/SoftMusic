package com.softmusic.app.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Owns the monitor and the single Classic/Expert transition job. */
internal class DjTransitionJobs(private val scope: CoroutineScope) {
    private var monitor: Job? = null
    private var transition: Transition? = null

    internal inner class Transition {
        internal lateinit var job: Job
        internal var originalVolume: Float? = null

        val isCurrent: Boolean get() = transition === this && job.isActive

        fun rememberVolume(volume: Float): Boolean {
            if (!isCurrent) return false
            originalVolume = volume
            return true
        }

        fun clearVolume() {
            if (isCurrent) originalVolume = null
        }

        fun restoreAfterAbort(isOutgoingActive: () -> Boolean, restoreVolume: (Float) -> Unit, stopStandby: () -> Unit) {
            if (!isCurrent || !isOutgoingActive()) return
            try {
                originalVolume?.let(restoreVolume)
            } finally {
                originalVolume = null
                stopStandby()
            }
        }
    }

    val isTransitionActive: Boolean get() = transition?.job?.isActive == true
    val isTransitionCommitted: Boolean get() = isTransitionActive && transition?.originalVolume != null

    fun startMonitor(intervalMs: () -> Long, tick: () -> Unit) {
        monitor?.cancel()
        monitor = scope.launch {
            while (isActive) {
                tick()
                delay(intervalMs())
            }
        }
    }

    fun startTransition(block: suspend CoroutineScope.(Transition) -> Unit) {
        val owner = Transition()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block(owner)
            } finally {
                if (transition === owner) transition = null
            }
        }
        owner.job = job
        transition = owner
        job.start()
    }

    fun cancelTransition(restoreVolume: (Float) -> Unit, stopStandby: () -> Unit) {
        val owner = transition
        transition = null
        val volume = owner?.originalVolume
        owner?.originalVolume = null
        owner?.job?.cancel()
        try {
            volume?.let(restoreVolume)
        } finally {
            stopStandby()
        }
    }

    fun stopMonitor() {
        monitor?.cancel()
    }
}
