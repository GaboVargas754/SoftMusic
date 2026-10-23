package com.softmusic.app.player

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CoalescingWriteQueueTest {
    @Test
    fun `pending requests coalesce without writing on the submitting thread`() {
        val executor = ManualExecutor()
        val written = mutableListOf<Int>()
        val queue = CoalescingWriteQueue(executor, written::add)

        queue.submit(1)
        queue.submit(2)
        queue.submit(3)

        assertEquals(emptyList(), written)
        assertEquals(1, executor.pendingCount)
        executor.runAll()
        assertEquals(listOf(3), written)

        queue.submit(4)
        assertEquals(1, executor.pendingCount)
        executor.runAll()
        assertEquals(listOf(3, 4), written)
    }

    @Test
    fun `requests arriving during a write drain in order with only the latest pending value`() {
        val executor = ManualExecutor()
        val written = mutableListOf<Int>()
        lateinit var queue: CoalescingWriteQueue<Int>
        queue = CoalescingWriteQueue(executor, { value ->
            written += value
            if (value == 1) {
                queue.submit(2)
                queue.submit(3)
            }
        })

        queue.submit(1)
        executor.runAll()
        queue.submit(4)
        queue.submit(5)
        executor.runAll()

        assertEquals(listOf(1, 3, 5), written)
        assertEquals(0, executor.pendingCount)
    }

    @Test
    fun `a submission inside a write cannot start another callback concurrently`() {
        val executor = ManualExecutor()
        val written = mutableListOf<Int>()
        var activeWrites = 0
        var maxActiveWrites = 0
        lateinit var queue: CoalescingWriteQueue<Int>
        queue = CoalescingWriteQueue(executor, { value ->
            activeWrites++
            maxActiveWrites = maxOf(maxActiveWrites, activeWrites)
            try {
                written += value
                if (value == 1) {
                    queue.submit(2)
                    // Pump any incorrectly scheduled second worker before the first write returns.
                    executor.runAll()
                    assertEquals(listOf(1), written)
                }
            } finally {
                activeWrites--
            }
        })

        queue.submit(1)
        executor.runAll()

        assertEquals(listOf(1, 2), written)
        assertEquals(1, maxActiveWrites)
    }

    @Test
    fun `a failed write reports its error and continues draining newer requests`() {
        val executor = ManualExecutor()
        val failure = IllegalStateException("write failed")
        val failures = mutableListOf<Throwable>()
        val written = mutableListOf<Int>()
        lateinit var queue: CoalescingWriteQueue<Int>
        queue = CoalescingWriteQueue(executor, { value ->
            written += value
            if (value == 1) {
                queue.submit(2)
                throw failure
            }
        }, failures::add)

        queue.submit(1)
        executor.runAll()
        queue.submit(3)
        executor.runAll()

        assertEquals(listOf(1, 2, 3), written)
        assertEquals(1, failures.size)
        assertSame(failure, failures.single())
    }

    @Test
    fun `a failed failure observer cannot strand a pending write`() {
        val executor = ManualExecutor()
        val written = mutableListOf<Int>()
        lateinit var queue: CoalescingWriteQueue<Int>
        queue = CoalescingWriteQueue(executor, { value ->
            written += value
            if (value == 1) {
                queue.submit(2)
                error("write failed")
            }
        }, { error("observer failed") })

        queue.submit(1)
        executor.runAll()

        assertEquals(listOf(1, 2), written)
    }

    @Test
    fun `clear replaces pending saves and a newer save replaces pending clear`() {
        val executor = ManualExecutor()
        val written = mutableListOf<SessionRequest>()
        val queue = CoalescingWriteQueue(executor, written::add)

        queue.submit(SessionRequest.Save(1))
        queue.submit(SessionRequest.Clear)
        executor.runAll()
        assertEquals(listOf<SessionRequest>(SessionRequest.Clear), written)

        queue.submit(SessionRequest.Save(2))
        queue.submit(SessionRequest.Clear)
        queue.submit(SessionRequest.Save(3))
        executor.runAll()

        assertEquals(listOf(SessionRequest.Clear, SessionRequest.Save(3)), written)
    }

    @Test
    fun `clear submitted during an older save becomes the final stored value`() {
        val executor = ManualExecutor()
        var stored: Int? = null
        val written = mutableListOf<SessionRequest>()
        lateinit var queue: CoalescingWriteQueue<SessionRequest>
        queue = CoalescingWriteQueue(executor, { request ->
            written += request
            when (request) {
                is SessionRequest.Save -> {
                    queue.submit(SessionRequest.Clear)
                    stored = request.value
                }
                SessionRequest.Clear -> stored = null
            }
        })

        queue.submit(SessionRequest.Save(1))
        executor.runAll()

        assertEquals(listOf(SessionRequest.Save(1), SessionRequest.Clear), written)
        assertNull(stored)
    }

    @Test
    fun `nullable requests are written instead of being mistaken for an empty queue`() {
        val executor = ManualExecutor()
        val written = mutableListOf<Int?>()
        val queue = CoalescingWriteQueue(executor, written::add)

        queue.submit(1)
        queue.submit(null)
        executor.runAll()

        assertEquals(listOf<Int?>(null), written)
    }

    @Test
    fun `a rejected executor does not leave later submissions stuck`() {
        val executor = ManualExecutor()
        var reject = true
        val failures = mutableListOf<Throwable>()
        val written = mutableListOf<Int>()
        val queue = CoalescingWriteQueue(Executor { command ->
            if (reject) throw RejectedExecutionException("executor unavailable")
            executor.execute(command)
        }, written::add, failures::add)

        assertFailsWith<RejectedExecutionException> { queue.submit(1) }
        reject = false
        queue.submit(2)
        executor.runAll()

        assertEquals(listOf(2), written)
        assertEquals(1, failures.size)
    }

    private sealed interface SessionRequest {
        data class Save(val value: Int) : SessionRequest
        data object Clear : SessionRequest
    }

    private class ManualExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        val pendingCount: Int get() = tasks.size

        override fun execute(command: Runnable) {
            tasks.addLast(command)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
