package com.vectr

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object SchedWallOfflineQueue {
    private const val PREFS = "vectr_schedwall_queue"
    private const val KEY = "overlay_events"
    private var flushing = false

    fun count(context: Context) = read(context).length()
    fun enqueue(context: Context, date: String, start: Int, end: Int, title: String) {
        val events = read(context)
        events.put(JSONObject().put("date", date).put("start", start).put("end", end).put("title", title))
        write(context, events)
    }
    fun flush(context: Context) {
        if (!DeviceWebSocket.isConnected() || flushing) return
        val event = read(context).optJSONObject(0) ?: return
        flushing = true
        SchedWallRepository.addOverlay(event.getString("date"), event.getInt("start"), event.getInt("end"), event.getString("title"), {
            val remaining = read(context)
            val next = JSONArray()
            for (index in 1 until remaining.length()) next.put(remaining.getJSONObject(index))
            write(context, next)
            flushing = false
            flush(context)
        }, { flushing = false })
    }
    private fun read(context: Context) = try { JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
    private fun write(context: Context, events: JSONArray) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, events.toString()).apply() }
}
