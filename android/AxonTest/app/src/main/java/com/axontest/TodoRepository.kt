package com.vectr

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class TodoItem(val id: Int, val text: String, val checked: Boolean)
object TodoRepository {
    private val client = OkHttpClient(); private val json = "application/json".toMediaType()
    fun fetch(ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = request("/api/todos", "GET", null, ok, fail)
    fun add(text: String, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = request("/api/todos", "POST", JSONObject().put("text", text), ok, fail)
    fun update(id: Int, checked: Boolean, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = request("/api/todos/$id", "PATCH", JSONObject().put("checked", checked), ok, fail)
    fun delete(id: Int, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = request("/api/todos/$id", "DELETE", null, ok, fail)
    fun clear(ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) = request("/api/todos/clear", "POST", null, ok, fail)
    fun parse(items: JSONArray) = (0 until items.length()).map { items.getJSONObject(it).let { item -> TodoItem(item.getInt("id"), item.getString("text"), item.optBoolean("checked")) } }
    private fun request(path: String, method: String, body: JSONObject?, ok: (List<TodoItem>) -> Unit, fail: (String) -> Unit) {
        val requestBody = body?.toString()?.toRequestBody(json) ?: if (method == "POST") "{}".toRequestBody(json) else null
        val request = Request.Builder().url(DeviceWebSocket.apiUrl(path)).method(method, requestBody).build()
        client.newCall(request).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Todo request failed"); override fun onResponse(call: Call, response: Response) = response.use { if (!it.isSuccessful) return fail("Todo request failed (${it.code})"); try { ok(parse(JSONArray(it.body?.string() ?: "[]"))) } catch (e: Exception) { fail(e.message ?: "Invalid todo response") } } })
    }
}
