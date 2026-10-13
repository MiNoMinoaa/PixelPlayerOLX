package com.minoppol.music.data.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object EncryptedCredentialPrefsFactory {

    private val degradedKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val _degraded = MutableStateFlow<Set<String>>(emptySet())

    val degraded: StateFlow<Set<String>> = _degraded.asStateFlow()

    fun inMemory(prefsName: String): SharedPreferences {
        degradedKeys.add(prefsName)
        _degraded.value = degradedKeys.toSet()
        return InMemorySharedPreferences()
    }

    private class InMemorySharedPreferences : SharedPreferences {

        private val values = ConcurrentHashMap<String, Any?>()
        private val listeners = ConcurrentHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Boolean>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? =
            values[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? {
            @Suppress("UNCHECKED_CAST")
            return (values[key] as? MutableSet<String>) ?: defValues
        }

        override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            values[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = values.containsKey(key)

        override fun edit(): SharedPreferences.Editor = Editor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener != null) listeners[listener] = true
        }

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?
        ) {
            if (listener != null) listeners.remove(listener)
        }

        private inner class Editor : SharedPreferences.Editor {

            private val pending = LinkedHashMap<String, Any?>()
            private val removals = LinkedHashSet<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply {
                removals.remove(key)
                pending[key] = value
            }

            override fun putStringSet(
                key: String,
                values: MutableSet<String>?
            ): SharedPreferences.Editor = apply {
                removals.remove(key)
                pending[key] = values?.toHashSet()
            }

            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply {
                removals.remove(key)
                pending[key] = value
            }

            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply {
                removals.remove(key)
                pending[key] = value
            }

            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply {
                removals.remove(key)
                pending[key] = value
            }

            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply {
                removals.remove(key)
                pending[key] = value
            }

            override fun remove(key: String): SharedPreferences.Editor = apply {
                pending.remove(key)
                removals.add(key)
            }

            override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }

            override fun commit(): Boolean {
                applyChanges()
                return true
            }

            override fun apply() {
                applyChanges()
            }

            private fun applyChanges() {
                val changedKeys = mutableListOf<String>()
                if (clearRequested) {
                    changedKeys.addAll(values.keys)
                    values.clear()
                    clearRequested = false
                }
                removals.forEach { key ->
                    if (values.remove(key) != null) changedKeys.add(key)
                }
                removals.clear()
                pending.forEach { (key, value) ->
                    if (values[key] != value) changedKeys.add(key)
                    if (value == null) values.remove(key) else values[key] = value
                }
                pending.clear()
                listeners.keys.forEach { listener ->
                    changedKeys.forEach { key ->
                        listener.onSharedPreferenceChanged(
                            this@InMemorySharedPreferences,
                            key
                        )
                    }
                }
            }
        }
    }
}
