package com.softmusic.app.player

/** Tracks decoded time, independently of amplitude: an absent PCM frame is never silence. */
internal class DjDecodedCoverage(
    private val startUs: Long,
    private val endUs: Long,
    private val startToleranceUs: Long = 1_000L,
    private val endToleranceUs: Long = 1_000L,
) {
    private var firstUs = Long.MAX_VALUE
    private var throughUs = Long.MIN_VALUE
    private var hasGap = false
    private var missingUs = 0L

    fun record(frameStartUs: Long, frameEndUs: Long) {
        val start = maxOf(startUs, frameStartUs)
        val end = minOf(endUs, frameEndUs)
        if (end <= start) return
        if (throughUs != Long.MIN_VALUE && start > throughUs) {
            missingUs += start - throughUs
            if (missingUs > TIMESTAMP_ROUNDING_US) hasGap = true
        }
        // Reordered PCM cannot establish coverage safely; overlapping packet boundaries can.
        if (firstUs != Long.MAX_VALUE && start < firstUs - TIMESTAMP_ROUNDING_US) hasGap = true
        firstUs = minOf(firstUs, start)
        throughUs = maxOf(throughUs, end)
    }

    fun isComplete(): Boolean = endUs > startUs && !hasGap && firstUs != Long.MAX_VALUE &&
        firstUs <= startUs + startToleranceUs && throughUs >= endUs - endToleranceUs

    private companion object {
        const val TIMESTAMP_ROUNDING_US = 1_000L
    }
}

/** A progressing decoder still has a total budget; queued input alone is not decoded progress. */
internal class DjDecodeWatchdog(
    private val startedMs: Long,
    private val totalBudgetMs: Long,
    private val stallBudgetMs: Long,
) {
    private var lastOutputMs = startedMs

    fun check(nowMs: Long) {
        if (nowMs - startedMs >= totalBudgetMs || nowMs - lastOutputMs >= stallBudgetMs) {
            throw DjAnalysisTimeoutException()
        }
    }

    fun decodedOutput(nowMs: Long) {
        check(nowMs)
        lastOutputMs = nowMs
    }
}

internal class DjAnalysisTimeoutException : RuntimeException()

internal data class DjAnalysisSourceRevision(
    val sizeBytes: Long,
    val modifiedMs: Long,
    val generation: Long = -1L,
) {
    val isCacheable: Boolean get() = sizeBytes >= 0L && (modifiedMs > 0L || generation >= 0L)
}

/** Unknown revisions can be analyzed but never retained, and invalidate older URI entries. */
internal class DjAnalysisCache<T>(private val capacity: Int) {
    private data class Key(val uri: String, val revision: DjAnalysisSourceRevision, val rangeEndMs: Long?)

    private val entries = object : LinkedHashMap<Key, T>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, T>?): Boolean = size > capacity
    }

    @Synchronized
    fun get(uri: String, revision: DjAnalysisSourceRevision?, rangeEndMs: Long? = null): T? {
        invalidateChanged(uri, revision)
        return revision?.takeIf { it.isCacheable }?.let { entries[Key(uri, it, rangeEndMs)] }
    }

    @Synchronized
    fun put(uri: String, revision: DjAnalysisSourceRevision?, value: T, rangeEndMs: Long? = null) {
        invalidateChanged(uri, revision)
        if (revision?.isCacheable == true) entries[Key(uri, revision, rangeEndMs)] = value
    }

    private fun invalidateChanged(uri: String, revision: DjAnalysisSourceRevision?) {
        entries.keys.removeAll { it.uri == uri && (revision?.isCacheable != true || it.revision != revision) }
    }
}
