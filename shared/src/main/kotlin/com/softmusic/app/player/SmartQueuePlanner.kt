package com.softmusic.app.player

import kotlin.math.abs
import kotlin.math.ln
import kotlin.random.Random

/** Local measurements only: [rms] describes the decoded opening, not perceived song loudness. */
data class SmartSongProfile(
    val bpm: Float? = null,
    val confidence: Float = 0f,
    val rms: Float? = null,
)

/**
 * Reorders pending IDs without adding, removing or replaying any queue entry.
 *
 * A small sliding window bounds both work and displacement in large queues. Missing or unreliable
 * measurements never count as a musical match. If nothing can be compared, incoming order wins;
 * an entry bypassed too often also gets its original turn so unknown songs cannot starve.
 */
object SmartQueuePlanner {
    fun order(
        anchorId: Long,
        pendingIds: List<Long>,
        profiles: Map<Long, SmartSongProfile>,
        randomized: Boolean,
        random: Random = Random.Default,
    ): List<Long> {
        if (pendingIds.size < 2) return pendingIds.toList()
        val result = ArrayList<Long>(pendingIds.size)
        val candidates = ArrayList<Candidate>(CANDIDATE_WINDOW)
        val incoming = pendingIds.iterator()
        var currentId = anchorId
        while (result.size < pendingIds.size) {
            while (candidates.size < CANDIDATE_WINDOW && incoming.hasNext()) {
                candidates += Candidate(incoming.next())
            }
            val overdueIndex = candidates.indexOfFirst { it.bypasses >= MAX_BYPASSES }
            val nextIndex = if (overdueIndex >= 0) overdueIndex else {
                chooseCandidate(profiles[currentId], candidates, profiles, randomized, random)
            }
            val selected = candidates.removeAt(nextIndex)
            candidates.forEach { it.bypasses++ }
            result += selected.id
            currentId = selected.id
        }
        return result
    }

    private fun chooseCandidate(
        anchor: SmartSongProfile?,
        candidates: List<Candidate>,
        profiles: Map<Long, SmartSongProfile>,
        randomized: Boolean,
        random: Random,
    ): Int {
        val anchorBpm = anchor.reliableBpm()
        val anchorRms = anchor.validRms()
        if (anchorBpm == null && anchorRms == null) return 0
        val ranked = candidates.mapIndexedNotNull { index, candidate ->
            val profile = profiles[candidate.id]
            val bpm = profile.reliableBpm()
            val rms = profile.validRms()
            var evidence = 0
            var cost = 0.0
            if (anchorBpm != null) {
                if (bpm != null) {
                    evidence = evidence or TEMPO_EVIDENCE
                    // A relative difference treats the same percentage change equally at any BPM.
                    cost += TEMPO_WEIGHT * abs(ln(bpm / anchorBpm)) / ln(1.15)
                } else cost += TEMPO_WEIGHT * MISSING_FEATURE_COST
            }
            if (anchorRms != null) {
                if (rms != null) {
                    evidence = evidence or LEVEL_EVIDENCE
                    // RMS is an amplitude measurement: 20 log10(ratio) gives its dB difference.
                    val dbDifference = 20.0 * abs(ln(rms.coerceAtLeast(RMS_FLOOR) / anchorRms.coerceAtLeast(RMS_FLOOR))) / ln(10.0)
                    cost += LEVEL_WEIGHT * dbDifference / LEVEL_SCALE_DB
                } else cost += LEVEL_WEIGHT * MISSING_FEATURE_COST
            }
            if (evidence == 0) null else RankedCandidate(index, cost, evidence)
        }.sortedBy { it.cost }
        val best = ranked.firstOrNull() ?: return 0
        if (!randomized) return best.index
        // Vary only similarly compatible choices with the same measured features. In particular,
        // absent analysis cannot become a bucket that is accidentally preferred by randomization.
        val comparable = ranked.filter {
            it.evidence == best.evidence && it.cost <= best.cost + COMPARABLE_COST_BAND
        }.take(MAX_RANDOM_CHOICES)
        return comparable[random.nextInt(comparable.size)].index
    }

    private fun SmartSongProfile?.reliableBpm(): Double? = this?.bpm?.takeIf {
        it.isFinite() && it in 40f..240f && confidence.isFinite() && confidence in 0.72f..1f
    }?.toDouble()

    private fun SmartSongProfile?.validRms(): Double? = this?.rms?.takeIf {
        it.isFinite() && it in 0f..1f
    }?.toDouble()

    private data class Candidate(val id: Long, var bypasses: Int = 0)
    private data class RankedCandidate(val index: Int, val cost: Double, val evidence: Int)

    private const val CANDIDATE_WINDOW = 12
    private const val MAX_BYPASSES = 8
    private const val MAX_RANDOM_CHOICES = 4
    private const val COMPARABLE_COST_BAND = 0.2
    private const val TEMPO_WEIGHT = 0.7
    private const val LEVEL_WEIGHT = 0.3
    private const val MISSING_FEATURE_COST = 1.5
    private const val LEVEL_SCALE_DB = 12.0
    private const val RMS_FLOOR = 0.0001
    private const val TEMPO_EVIDENCE = 1
    private const val LEVEL_EVIDENCE = 2
}
