package com.vectr

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class SchedWallOverlay(val id: String, val date: String, val start: Int, val end: Int, val title: String)

object SchedWallRepository {
    private val client = OkHttpClient()
    private val json = "application/json".toMediaType()

    fun addOverlay(date: String, start: Int, end: Int, title: String, success: () -> Unit, failure: (String) -> Unit) {
        val body = JSONObject().put("layer", "overlay").put("date", date).put("start", start).put("end", end).put("title", title)
        request("/api/schedwall/task", "POST", body, success, failure)
    }

    fun fetchOverlays(success: (List<SchedWallOverlay>) -> Unit, failure: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/schedwall/state")).get().build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = failure(e.message ?: "Could not load SchedWall")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return failure("Could not load SchedWall (${it.code})")
                try {
                    val overlays = JSONObject(it.body?.string() ?: "{}").optJSONArray("overlay") ?: JSONArray()
                    success((0 until overlays.length()).map { index -> overlays.getJSONObject(index).let { item -> SchedWallOverlay(item.getString("id"), item.getString("date"), item.getInt("start"), item.getInt("end"), item.getString("title")) } })
                } catch (error: Exception) { failure(error.message ?: "Invalid SchedWall response") }
            }
        })
    }

    private fun request(path: String, method: String, body: JSONObject, success: () -> Unit, failure: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl(path)).method(method, body.toString().toRequestBody(json)).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = failure(e.message ?: "SchedWall request failed")
            override fun onResponse(call: Call, response: Response) = response.use { if (it.isSuccessful) success() else failure("SchedWall request failed (${it.code})") }
        })
    }
}
