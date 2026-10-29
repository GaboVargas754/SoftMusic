package com.softmusic.app.icons

internal interface LauncherIconStore {
    val supportsAtomicChanges: Boolean
    fun enabledIcons(): Set<SeasonalIcon>
    fun replaceEnabledIcon(icon: SeasonalIcon)
    fun setEnabled(icon: SeasonalIcon, enabled: Boolean)
}

/** Older Android versions must enable the replacement before disabling an existing entry. */
internal class LauncherIconSwitcher(private val store: LauncherIconStore) {
    fun apply(icon: SeasonalIcon, appIsVisible: Boolean = false): Boolean {
        // Disabling the alias that launched a visible activity can dismiss that activity.
        if (appIsVisible) return false
        val enabled = store.enabledIcons()
        if (enabled == setOf(icon)) return false
        if (store.supportsAtomicChanges) {
            store.replaceEnabledIcon(icon)
        } else {
            if (icon !in enabled) store.setEnabled(icon, true)
            enabled.filterNot { it == icon }.forEach { store.setEnabled(it, false) }
        }
        return true
    }
}
