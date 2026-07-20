package com.vectr

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import java.io.IOException

data class MacroConfig(val id: Int, val label: String)

object MacroRepository {
    private val client = OkHttpClient()

    fun fetchConfig(onSuccess: (List<MacroConfig>) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/macro/config")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not load macros")

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("Could not load macros (${it.code})")
                        return
                    }
                    try {
                        val macros = JSONArray(it.body?.string() ?: "[]")
                        val items = (0 until macros.length()).map { index ->
                            val macro = macros.getJSONObject(index)
                            MacroConfig(macro.getInt("id"), macro.getString("label"))
                        }.sortedBy { macro -> macro.id }
                        onSuccess(items)
                    } catch (error: Exception) {
                        onError(error.message ?: "Could not read macros")
                    }
                }
            }
        })
    }
}
