package com.vectr

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

object TelemetryRepository {
    private val client = OkHttpClient()
    fun fetch(onSuccess: (JSONObject) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/telemetry")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not load telemetry")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not load telemetry (${it.code})")
                try { onSuccess(JSONObject(it.body?.string() ?: "{}")) } catch (error: Exception) { onError(error.message ?: "Invalid telemetry response") }
            }
        })
    }
}
