package com.softmusic.app.player

import java.util.concurrent.Executor

/** Runs writes sequentially, replacing work that has not started with the newest request. */
class CoalescingWriteQueue<T>(
    private val executor: Executor,
    private val write: (T) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
) {
    private val lock = Any()
    private var pending: Request<T>? = null
    private var running = false

    fun submit(value: T) {
        val startWorker = synchronized(lock) {
            pending = Request(value)
            if (running) {
                false
            } else {
                running = true
                true
            }
        }
        if (!startWorker) return

        try {
            executor.execute(::drain)
        } catch (failure: RuntimeException) {
            // Retain the latest request so a later submission can retry a rejected worker.
            synchronized(lock) { running = false }
            reportFailure(failure)
            throw failure
        }
    }

    private fun drain() {
        while (true) {
            val request = synchronized(lock) {
                val next = pending
                if (next == null) {
                    running = false
                    return
                }
                pending = null
                next
            }
            try {
                write(request.value)
            } catch (failure: Throwable) {
                reportFailure(failure)
            }
        }
    }

    private fun reportFailure(failure: Throwable) {
        // Reporting must not strand a newer request or leave the queue marked as running.
        try {
            onFailure(failure)
        } catch (_: Throwable) {
        }
    }

    private class Request<T>(val value: T)
}
