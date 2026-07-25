package com.vectr

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import android.content.Context
import java.util.UUID

data class TodoItem(val id: Int, val text: String, val checked: Boolean)
object TodoRepository {
    private val client = OkHttpClient(); private val json = "application/json".toMediaType()
    private const val CACHE = "todos_cache"
    private const val QUEUE = "todos_queue"

    fun fetch(ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) {
        val context = AppContextHolder.context
        if (!DeviceWebSocket.isConnected() && context != null && LocalSyncStore.has(context, CACHE)) {
            ok(parse(LocalSyncStore.readArray(context, CACHE))); return
        }
        request("/api/todos", "GET", null, { items -> cache(items); ok(items) }, { error ->
            if (context != null && LocalSyncStore.has(context, CACHE)) ok(parse(LocalSyncStore.readArray(context, CACHE))) else fail(error)
        })
    }
    fun add(text: String, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = mutate("add", JSONObject().put("text", text), ok, fail)
    fun update(id: Int, checked: Boolean, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = mutate("update", JSONObject().put("id", id).put("checked", checked), ok, fail)
    fun delete(id: Int, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = mutate("delete", JSONObject().put("id", id), ok, fail)
    fun clear(ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = mutate("clear", JSONObject(), ok, fail)
    fun parse(items: JSONArray) = (0 until items.length()).map { items.getJSONObject(it).let { item -> TodoItem(item.getInt("id"), item.getString("text"), item.optBoolean("checked")) } }
    private fun request(path: String, method: String, body: JSONObject?, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) {
        val requestBody = body?.toString()?.toRequestBody(json) ?: if (method == "POST") "{}".toRequestBody(json) else null
        val request = Request.Builder().url(DeviceWebSocket.apiUrl(path)).method(method, requestBody).build()
        client.newCall(request).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Todo request failed"); override fun onResponse(call: Call, response: Response) = response.use { if (!it.isSuccessful) return fail("Todo request failed (${it.code})"); try { ok(parse(JSONArray(it.body?.string() ?: "[]"))) } catch (e: Exception) { fail(e.message ?: "Invalid todo response") } } })
    }

    private fun mutate(type: String, payload: JSONObject, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) {
        val context = AppContextHolder.context
        if (!DeviceWebSocket.isConnected() && context != null) {
            val localPayload = withLocalId(type, payload)
            val next = applyLocal(parse(LocalSyncStore.readArray(context, CACHE)), type, localPayload)
            cache(next); enqueue(context, type, localPayload); ok(next); return
        }
        val (path, method, body) = remoteRequest(type, payload)
        request(path, method, body, { items -> cache(items); ok(items) }, { error ->
            if (context == null) { fail(error); return@request }
            val localPayload = withLocalId(type, payload)
            val next = applyLocal(parse(LocalSyncStore.readArray(context, CACHE)), type, localPayload)
            cache(next); enqueue(context, type, localPayload); ok(next)
        })
    }

    fun flush(context: Context) {
        if (!DeviceWebSocket.isConnected()) return
        val entry = LocalSyncStore.readArray(context, QUEUE).optJSONObject(0) ?: return
        val type = entry.optString("type"); val payload = entry.optJSONObject("payload") ?: JSONObject()
        val (path, method, body) = remoteRequest(type, payload)
        request(path, method, body, { remote ->
            if (type == "add") {
                val remoteId = remote.lastOrNull { it.text == payload.optString("text") }?.id
                if (remoteId != null) replaceQueuedId(context, payload.optInt("localId"), remoteId)
            }
            cache(remote)
            val queue = LocalSyncStore.readArray(context, QUEUE); val rest = JSONArray()
            for (i in 1 until queue.length()) rest.put(queue.getJSONObject(i))
            LocalSyncStore.writeArray(context, QUEUE, rest)
            flush(context)
        }, { /* retain in order and retry after the next connection */ })
    }

    private fun remoteRequest(type: String, p: JSONObject): Triple<String, String, JSONObject?> = when (type) {
        "add" -> Triple("/api/todos", "POST", JSONObject().put("text", p.optString("text")))
        "update" -> Triple("/api/todos/${p.optInt("id")}", "PATCH", JSONObject().put("checked", p.optBoolean("checked")))
        "delete" -> Triple("/api/todos/${p.optInt("id")}", "DELETE", null)
        else -> Triple("/api/todos/clear", "POST", null)
    }
    private fun applyLocal(items: List<TodoItem>, type: String, p: JSONObject): List<TodoItem> = when (type) {
        "add" -> items + TodoItem(p.optInt("localId"), p.optString("text"), false)
        "update" -> items.map { if (it.id == p.optInt("id")) it.copy(checked = p.optBoolean("checked")) else it }
        "delete" -> items.filterNot { it.id == p.optInt("id") }
        else -> emptyList()
    }
    private fun enqueue(context: Context, type: String, payload: JSONObject) {
        LocalSyncStore.readArray(context, QUEUE).also { it.put(JSONObject().put("type", type).put("payload", payload)) }.let { LocalSyncStore.writeArray(context, QUEUE, it) }
    }
    private fun withLocalId(type: String, payload: JSONObject): JSONObject {
        if (type != "add") return payload
        return JSONObject(payload.toString()).put("localId", -kotlin.math.abs(UUID.randomUUID().hashCode()))
    }
    private fun replaceQueuedId(context: Context, localId: Int, remoteId: Int) {
        if (localId >= 0) return
        val queue = LocalSyncStore.readArray(context, QUEUE)
        for (i in 0 until queue.length()) {
            val payload = queue.getJSONObject(i).optJSONObject("payload") ?: continue
            if (payload.optInt("id", Int.MIN_VALUE) == localId) payload.put("id", remoteId)
        }
        LocalSyncStore.writeArray(context, QUEUE, queue)
    }
    private fun cache(items: List<TodoItem>) {
        AppContextHolder.context?.let { context ->
            LocalSyncStore.writeArray(context, CACHE, JSONArray().apply { items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked)) } })
        }
    }
}
