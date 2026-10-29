package com.softmusic.app.backup

import android.content.SharedPreferences

/** Models Android's memory-first commit, including a failed durable write. */
internal class InMemorySharedPreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {
    private val values = initial.toMutableMap()
    private var durableValues = values.toMap()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    val commitResults = ArrayDeque<Boolean>()
    var commitCount = 0
        private set
    var afterNextMemoryUpdate: (() -> Unit)? = null

    fun reopened() = InMemorySharedPreferences(durableValues)

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String?, defValue: String?): String? = values[key]?.let { it as String } ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key]?.let { @Suppress("UNCHECKED_CAST") (it as Set<String>).toMutableSet() } ?: defValues
    override fun getInt(key: String?, defValue: Int): Int = values[key]?.let { it as Int } ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key]?.let { it as Long } ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key]?.let { it as Float } ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key]?.let { it as Boolean } ?: defValue
    override fun contains(key: String?): Boolean = key in values
    override fun edit(): SharedPreferences.Editor = Editor()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        if (listener != null) listeners.add(listener)
    }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        listeners.remove(listener)
    }

    private inner class Editor : SharedPreferences.Editor {
        private val changes = linkedMapOf<String, Any?>()
        private var clearRequested = false
        override fun putString(key: String?, value: String?) = apply { changes[requireNotNull(key)] = value }
        override fun putStringSet(key: String?, value: MutableSet<String>?) = apply { changes[requireNotNull(key)] = value?.toSet() }
        override fun putInt(key: String?, value: Int) = apply { changes[requireNotNull(key)] = value }
        override fun putLong(key: String?, value: Long) = apply { changes[requireNotNull(key)] = value }
        override fun putFloat(key: String?, value: Float) = apply { changes[requireNotNull(key)] = value }
        override fun putBoolean(key: String?, value: Boolean) = apply { changes[requireNotNull(key)] = value }
        override fun remove(key: String?) = apply { changes[requireNotNull(key)] = null }
        override fun clear() = apply { clearRequested = true }
        override fun commit(): Boolean {
            commitCount++
            if (clearRequested) values.clear()
            changes.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
            val callback = afterNextMemoryUpdate
            afterNextMemoryUpdate = null
            callback?.invoke()
            listeners.toList().forEach { listener -> changes.keys.forEach { listener.onSharedPreferenceChanged(this@InMemorySharedPreferences, it) } }
            val saved = commitResults.removeFirstOrNull() ?: true
            if (saved) durableValues = values.toMap()
            return saved
        }
        override fun apply() { commit() }
    }
}
