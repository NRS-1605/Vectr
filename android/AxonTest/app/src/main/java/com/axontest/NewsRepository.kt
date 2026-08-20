package com.vectr

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

data class NewsEntry(val title: String, val link: String, val source: String, val publishedAt: String?)

object NewsRepository {
    private val client = OkHttpClient()

    fun fetch(onSuccess: (List<NewsEntry>) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/news")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not load news")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not load news (${it.code})")
                try {
                    val entries = JSONArray(it.body?.string() ?: "[]")
                    onSuccess((0 until entries.length()).map { index -> entries.getJSONObject(index).let { item ->
                        NewsEntry(item.optString("title", "Untitled"), item.optString("link"), item.optString("source", "Unknown source"), item.optString("publishedAt").takeIf(String::isNotBlank))
                    } })
                } catch (error: Exception) { onError(error.message ?: "Invalid news response") }
            }
        })
    }
}
