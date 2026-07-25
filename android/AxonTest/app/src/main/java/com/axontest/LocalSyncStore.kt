package com.vectr

import android.content.Context
import org.json.JSONArray

/** Small durable cache used while the phone cannot reach axon-core. */
object LocalSyncStore {
    private const val PREFS = "vectr_local_sync"

    fun readArray(context: Context, key: String): JSONArray = try {
        JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]"))
    } catch (_: Exception) { JSONArray() }

    fun writeArray(context: Context, key: String, value: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, value.toString()).apply()
    }

    fun has(context: Context, key: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(key)
}
