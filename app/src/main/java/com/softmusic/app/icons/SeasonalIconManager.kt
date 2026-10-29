package com.softmusic.app.icons

import android.content.Context
import android.util.Log
import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class SeasonalIconManager private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val switcher = LauncherIconSwitcher(AndroidLauncherIconStore(context.applicationContext))
    private val scheduler = SeasonalIconScheduler(context.applicationContext)
    private val _icon = MutableStateFlow(SeasonalIconCalendar.iconFor(LocalDate.now()))
    val icon: StateFlow<SeasonalIcon> = _icon.asStateFlow()
    private val visibleActivities = mutableSetOf<Any>()

    init {
        scope.launch(Dispatchers.Main.immediate) {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    refresh()
                }
            })
        }
    }

    @MainThread
    fun setActivityVisible(token: Any, visible: Boolean) {
        if (visible) visibleActivities.add(token) else visibleActivities.remove(token)
        refresh()
    }

    fun refresh(onFinished: () -> Unit = {}) {
        scope.launch {
            try {
                mutex.withLock {
                    val now = Instant.now()
                    val zone = ZoneId.systemDefault()
                    val selected = SeasonalIconCalendar.iconFor(now.atZone(zone).toLocalDate())
                    _icon.value = selected
                    try {
                        // Lifecycle callbacks and alias changes share the main thread, so a
                        // newly visible activity cannot race a pending background refresh.
                        withContext(Dispatchers.Main.immediate) {
                            val processIsVisible = ProcessLifecycleOwner.get().lifecycle.currentState
                                .isAtLeast(Lifecycle.State.STARTED)
                            switcher.apply(selected, visibleActivities.isNotEmpty() || processIsVisible)
                        }
                    } catch (failure: RuntimeException) {
                        Log.w(TAG, "Unable to refresh the launcher icon", failure)
                    }
                    try {
                        scheduler.schedule(nextSeasonalIconRefresh(now, zone))
                    } catch (failure: RuntimeException) {
                        Log.w(TAG, "Unable to schedule the next seasonal icon", failure)
                    }
                }
            } finally {
                onFinished()
            }
        }
    }

    companion object {
        private const val TAG = "SeasonalIcon"
        @Volatile private var instance: SeasonalIconManager? = null
        fun get(context: Context): SeasonalIconManager = instance ?: synchronized(this) {
            instance ?: SeasonalIconManager(context.applicationContext).also { instance = it }
        }
    }
}
