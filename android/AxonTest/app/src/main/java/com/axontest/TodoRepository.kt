package com.vectr

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import android.content.Context
import java.util.UUID

data class TodoItem(val id: Int, val text: String, val checked: Boolean, val listId: Int? = null)
data class TodoList(val id: Int, val name: String)
data class TodoState(val lists: List<TodoList>, val items: List<TodoItem>)
object TodoRepository {
    private val client = OkHttpClient(); private val json = "application/json".toMediaType()
    private const val CACHE = "todos_cache"
    private const val LISTS_CACHE = "todo_lists_cache"
    private const val QUEUE = "todos_queue"

    fun fetch(ok: (TodoState) -> Unit, fail: (String) -> Unit) {
        val context = AppContextHolder.context
        if (!DeviceWebSocket.isConnected() && context != null && LocalSyncStore.has(context, CACHE)) {
            ok(readCached(context)); return
        }
        request("/api/todos", "GET", null, { state -> cache(state); ok(state) }, { error ->
            if (context != null && LocalSyncStore.has(context, CACHE)) ok(readCached(context)) else fail(error)
        })
    }
    fun add(text: String, listId: Int?, ok: (TodoState) -> Unit, fail: (String) -> Unit) = mutate("add", JSONObject().put("text", text).put("listId", listId ?: JSONObject.NULL), ok, fail)
    fun update(id: Int, checked: Boolean, ok: (TodoState) -> Unit, fail: (String) -> Unit) = mutate("update", JSONObject().put("id", id).put("checked", checked), ok, fail)
    fun delete(id: Int, ok: (TodoState) -> Unit, fail: (String) -> Unit) = mutate("delete", JSONObject().put("id", id), ok, fail)
    fun clear(ok: (TodoState) -> Unit, fail: (String) -> Unit) = mutate("clear", JSONObject(), ok, fail)
    fun addList(name: String, ok: (TodoState) -> Unit, fail: (String) -> Unit) = mutate("addList", JSONObject().put("name", name), ok, fail)
    fun deleteList(id: Int, ok: (TodoState) -> Unit, fail: (String) -> Unit) = mutate("deleteList", JSONObject().put("id", id), ok, fail)
    fun parse(state: JSONObject): TodoState {
        val lists = (0 until (state.optJSONArray("lists")?.length() ?: 0)).map { state.optJSONArray("lists").getJSONObject(it).let { l -> TodoList(l.getInt("id"), l.getString("name")) } }
        val items = (0 until (state.optJSONArray("items")?.length() ?: 0)).map { state.optJSONArray("items").getJSONObject(it).let { item -> TodoItem(item.getInt("id"), item.getString("text"), item.optBoolean("checked"), if (item.isNull("listId")) null else item.optInt("listId")) } }
        return TodoState(lists, items)
    }
    fun parseItems(items: JSONArray): List<TodoItem> = (0 until items.length()).map { items.getJSONObject(it).let { item -> TodoItem(item.getInt("id"), item.getString("text"), item.optBoolean("checked"), if (item.isNull("listId")) null else item.optInt("listId")) } }
    private fun request(path: String, method: String, body: JSONObject?, ok: (TodoState) -> Unit, fail: (String) -> Unit) {
        val requestBody = body?.toString()?.toRequestBody(json) ?: if (method == "POST") "{}".toRequestBody(json) else null
        val request = Request.Builder().url(DeviceWebSocket.apiUrl(path)).method(method, requestBody).build()
        client.newCall(request).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Todo request failed"); override fun onResponse(call: Call, response: Response) = response.use { if (!it.isSuccessful) return fail("Todo request failed (${it.code})"); try { ok(parse(JSONObject(it.body?.string() ?: "{}"))) } catch (e: Exception) { fail(e.message ?: "Invalid todo response") } } })
    }

    private fun mutate(type: String, payload: JSONObject, ok: (TodoState) -> Unit, fail: (String) -> Unit) {
        val context = AppContextHolder.context
        if (!DeviceWebSocket.isConnected() && context != null) {
            val localPayload = withLocalId(type, payload)
            val next = applyLocal(readCached(context), type, localPayload)
            cache(next); enqueue(context, type, localPayload); ok(next); return
        }
        val (path, method, body) = remoteRequest(type, payload)
        request(path, method, body, { state -> cache(state); ok(state) }, { error ->
            if (context == null) { fail(error); return@request }
            val localPayload = withLocalId(type, payload)
            val next = applyLocal(readCached(context), type, localPayload)
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
                val remoteId = remote.items.lastOrNull { it.text == payload.optString("text") }?.id
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
        "add" -> Triple("/api/todos", "POST", JSONObject().put("text", p.optString("text")).apply { if (!p.isNull("listId")) put("listId", p.optInt("listId")) })
        "update" -> Triple("/api/todos/${p.optInt("id")}", "PATCH", JSONObject().put("checked", p.optBoolean("checked")))
        "delete" -> Triple("/api/todos/${p.optInt("id")}", "DELETE", null)
        "addList" -> Triple("/api/todos/lists", "POST", JSONObject().put("name", p.optString("name")))
        "deleteList" -> Triple("/api/todos/lists/${p.optInt("id")}", "DELETE", null)
        else -> Triple("/api/todos/clear", "POST", null)
    }
    private fun applyLocal(items: TodoState, type: String, p: JSONObject): TodoState = when (type) {
        "add" -> items.copy(items = items.items + TodoItem(p.optInt("localId"), p.optString("text"), false, if (p.isNull("listId")) null else p.optInt("listId")))
        "update" -> items.copy(items = items.items.map { if (it.id == p.optInt("id")) it.copy(checked = p.optBoolean("checked")) else it })
        "delete" -> items.copy(items = items.items.filterNot { it.id == p.optInt("id") })
        "addList" -> items.copy(lists = items.lists + TodoList(items.lists.maxOfOrNull { it.id }?.plus(1) ?: 1, p.optString("name")))
        "deleteList" -> items.copy(lists = items.lists.filterNot { it.id == p.optInt("id") }, items = items.items.filterNot { it.listId == p.optInt("id") })
        else -> items.copy(items = emptyList())
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
    private fun cache(state: TodoState) {
        AppContextHolder.context?.let { context ->
            LocalSyncStore.writeArray(context, LISTS_CACHE, JSONArray().apply { state.lists.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
            LocalSyncStore.writeArray(context, CACHE, JSONArray().apply { state.items.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked).apply { if (it.listId != null) put("listId", it.listId) }) } })
        }
    }
    private fun readCached(context: Context): TodoState {
        val lists = (0 until LocalSyncStore.readArray(context, LISTS_CACHE).length()).map { LocalSyncStore.readArray(context, LISTS_CACHE).getJSONObject(it).let { l -> TodoList(l.getInt("id"), l.getString("name")) } }
        val items = (0 until LocalSyncStore.readArray(context, CACHE).length()).map { LocalSyncStore.readArray(context, CACHE).getJSONObject(it).let { item -> TodoItem(item.getInt("id"), item.getString("text"), item.optBoolean("checked"), if (item.isNull("listId")) null else item.optInt("listId")) } }
        return TodoState(lists, items)
    }
}