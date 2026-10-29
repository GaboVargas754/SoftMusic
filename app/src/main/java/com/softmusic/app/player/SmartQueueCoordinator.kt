package com.softmusic.app.player

import com.softmusic.app.data.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlin.random.Random

internal data class SmartQueueSnapshot(
    val owner: Any,
    val songs: List<Song>,
    val currentSongId: Long,
) {
    val ids = songs.map { it.id }
    val currentIndex = ids.indexOf(currentSongId)
    val pendingSongs get() = songs.drop(currentIndex + 1)
}

internal data class SmartQueueRuntimeState(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

/** Service-owned ordering. Results are valid only for the exact deck, queue and transport revision. */
internal class SmartQueueCoordinator(
    private val scope: CoroutineScope,
    private val snapshot: () -> SmartQueueSnapshot?,
    private val analyze: suspend (Song) -> SmartSongProfile,
    private val applyOrder: (SmartQueueSnapshot, List<Long>) -> Boolean,
    private val publish: (SmartQueueRuntimeState) -> Unit,
    private val random: Random = Random.Default,
    private val candidateLimit: Int = 12,
    private val automaticTimeoutMs: Long = 15_000L,
    private val songTimeoutMs: Long = 2_000L,
    private val canAnalyzeAutomatic: () -> Boolean = { true },
    private val currentIdentity: () -> Pair<Any, Long>? = { snapshot()?.let { it.owner to it.currentSongId } },
) {
    private var revision = 0L
    private var job: Job? = null
    private var enabled = false
    private var automatic = false
    private var manualOrderLocked = false
    private var settledOwner: Any? = null
    private var settledSongId: Long? = null
    private var committedIds: Set<Long> = emptySet()
    private var state = SmartQueueRuntimeState()

    fun configure(enabled: Boolean, automatic: Boolean) {
        if (this.enabled == enabled && this.automatic == automatic) return
        cancel()
        this.enabled = enabled
        this.automatic = automatic
        clearSettled()
        committedIds = emptySet()
        emit(state.copy(enabled = enabled, busy = false, message = null))
    }

    /** Called before capturing a DJ target, so preload and preview use the committed order. */
    fun prepareAutomatic(): Boolean {
        if (job?.isActive == true) return false
        if (!enabled || !automatic || manualOrderLocked) return true
        val identity = currentIdentity() ?: return true
        if (settledOwner === identity.first && settledSongId == identity.second) return true
        if (!canAnalyzeAutomatic()) {
            settledOwner = identity.first
            settledSongId = identity.second
            return true
        }
        val current = validSnapshot() ?: return true
        if (isSettled(current)) return true
        // Consume the committed batch before planning another one. Replanning on every track
        // would reset the planner's fairness limit and could keep bypassing an unknown song.
        if (current.pendingSongs.any { it.id in committedIds }) {
            settle(current)
            return true
        }
        start(current, explicit = false)
        return job?.isActive != true
    }

    suspend fun awaitPrepared() {
        prepareAutomatic()
        job?.join()
    }

    /** A second invocation cancels any active analysis, including automatic lookahead. */
    fun orderRemaining() {
        if (job?.isActive == true) {
            cancelForTransport("Análisis inteligente cancelado.")
            return
        }
        val current = validSnapshot()
        if (current == null || current.pendingSongs.size < 2) {
            emit(state.copy(message = "Se necesitan al menos dos canciones pendientes."))
            return
        }
        manualOrderLocked = false
        committedIds = emptySet()
        start(current, explicit = true)
    }

    /** A user's chosen sequence remains authoritative until a fresh queue or explicit sort. */
    fun markManualEdit() {
        manualOrderLocked = true
        cancelForTransport()
    }

    fun startNewQueue() {
        cancel()
        manualOrderLocked = false
        committedIds = emptySet()
        clearSettled()
        emit(state.copy(busy = false, message = null))
    }

    fun queueChanged() {
        cancel()
        committedIds = emptySet()
        clearSettled()
        emit(state.copy(busy = false, message = null))
    }

    fun currentChanged() {
        cancel()
        clearSettled()
        emit(state.copy(busy = false, message = null))
    }

    fun cancelForTransport(message: String? = null) {
        cancel()
        validSnapshot()?.let(::settle)
        emit(state.copy(busy = false, message = message))
    }

    fun release() = cancel()

    private fun start(captured: SmartQueueSnapshot, explicit: Boolean) {
        settle(captured)
        val pending = captured.pendingSongs
        if (pending.size < 2) return
        val candidates = if (explicit) pending else pending.take(candidateLimit.coerceAtLeast(1))
        val anchor = captured.songs[captured.currentIndex]
        val token = ++revision
        emit(state.copy(busy = true, message = if (explicit) "Analizando 0 de ${candidates.size} canciones…" else "Preparando aleatorio inteligente…"))
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val profiles = mutableMapOf<Long, SmartSongProfile>()
                suspend fun collectProfiles() {
                    for ((index, song) in (listOf(anchor) + candidates).withIndex()) {
                        currentCoroutineContext().ensureActive()
                        val profile = try {
                            withTimeoutOrNull(songTimeoutMs) { analyze(song) }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            null
                        }
                        currentCoroutineContext().ensureActive()
                        if (token != revision) return
                        if (profile != null) profiles[song.id] = profile
                        if (explicit && index > 0) {
                            emit(state.copy(message = "Analizando $index de ${candidates.size} canciones…"))
                        }
                    }
                }
                if (explicit) collectProfiles() else withTimeoutOrNull(automaticTimeoutMs) { collectProfiles() }
                currentCoroutineContext().ensureActive()
                if (!isCurrent(token, captured)) return@launch
                val useful = profiles.values.count { it.bpm?.let { bpm -> bpm.isFinite() && bpm in 40f..240f } == true && it.confidence in 0.72f..1f } >= 2 ||
                    profiles.values.count { it.rms?.let { rms -> rms.isFinite() && rms in 0f..1f } == true } >= 2
                if (!useful) {
                    if (explicit) emit(state.copy(message = "No hay mediciones suficientes; se conserva el orden actual."))
                    else emit(state.copy(message = null))
                    return@launch
                }
                val selected = withContext(Dispatchers.Default) {
                    SmartQueuePlanner.order(captured.currentSongId, candidates.map { it.id }, profiles, !explicit, random)
                }
                currentCoroutineContext().ensureActive()
                if (!isCurrent(token, captured)) return@launch
                val orderedPending = selected + pending.drop(candidates.size).map { it.id }
                // The planner has no authority to insert, omit or duplicate an entry.
                if (orderedPending.size != pending.size || orderedPending.toSet() != pending.map { it.id }.toSet()) return@launch
                if (applyOrder(captured, orderedPending)) {
                    if (explicit) manualOrderLocked = true
                    committedIds = selected.toSet()
                    if (explicit) emit(state.copy(message = "Orden inteligente aplicado a ${pending.size} canciones pendientes."))
                    else emit(state.copy(message = null))
                } else if (explicit) {
                    emit(state.copy(message = "La cola cambió; vuelve a ordenar las canciones pendientes."))
                } else if (!explicit) {
                    emit(state.copy(message = null))
                }
            } finally {
                if (token == revision) {
                    job = null
                    emit(state.copy(busy = false))
                }
            }
        }.also { it.start() }
    }

    private fun validSnapshot(): SmartQueueSnapshot? = snapshot()?.takeIf {
        it.currentIndex >= 0 && it.ids.size == it.ids.toSet().size
    }

    private fun isCurrent(token: Long, captured: SmartQueueSnapshot): Boolean {
        if (token != revision) return false
        val live = validSnapshot() ?: return false
        return live.owner === captured.owner && live.currentSongId == captured.currentSongId && live.ids == captured.ids
    }

    private fun settle(captured: SmartQueueSnapshot) {
        settledOwner = captured.owner
        settledSongId = captured.currentSongId
    }

    private fun isSettled(current: SmartQueueSnapshot) = settledOwner === current.owner && settledSongId == current.currentSongId

    private fun clearSettled() {
        settledOwner = null
        settledSongId = null
    }

    private fun cancel() {
        revision++
        job?.cancel()
        job = null
    }

    private fun emit(updated: SmartQueueRuntimeState) {
        state = updated
        publish(updated)
    }
}
