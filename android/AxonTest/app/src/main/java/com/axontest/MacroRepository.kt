package com.vectr

import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class MacroConfig(val id: Int, val label: String)

data class MacroPresets(val names: List<String>, val active: String)

object MacroRepository {
    private val client = OkHttpClient()

    fun fetchPresets(onSuccess: (MacroPresets) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/macro/presets")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not load presets")

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("Could not load presets (${it.code})")
                        return
                    }
                    try {
                        val body = JSONObject(it.body?.string() ?: "{}")
                        val names = (0 until body.getJSONArray("presets").length())
                            .map { index -> body.getJSONArray("presets").getString(index) }
                        onSuccess(MacroPresets(names, body.getString("active")))
                    } catch (error: Exception) {
                        onError(error.message ?: "Could not read presets")
                    }
                }
            }
        })
    }

    fun fetchConfig(preset: String? = null, onSuccess: (List<MacroConfig>) -> Unit, onError: (String) -> Unit) {
        val url = if (preset.isNullOrEmpty()) {
            DeviceWebSocket.apiUrl("/api/macro/config")
        } else {
            DeviceWebSocket.apiUrl("/api/macro/config?preset=${java.net.URLEncoder.encode(preset, "UTF-8")}")
        }
        val request = Request.Builder().url(url).build()
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

    fun activatePreset(name: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val url = DeviceWebSocket.apiUrl("/api/macro/presets/${java.net.URLEncoder.encode(name, "UTF-8")}/activate")
        val request = Request.Builder().url(url).post(okhttp3.RequestBody.create(null, ByteArray(0))).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not activate preset")

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onError("Could not activate preset (${it.code})")
                        return
                    }
                    try {
                        onSuccess(JSONObject(it.body?.string() ?: "{}").getString("active"))
                    } catch (error: Exception) {
                        onError(error.message ?: "Could not read response")
                    }
                }
            }
        })
    }
}