package com.vectr

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Voyage(val id: String, val tier: String, val duration: Int, val start: String, val status: String, val berries: Int?)
object VoyageRepository {
    private val client = OkHttpClient(); private val json = "application/json".toMediaType()
    fun balance(ok: (Int) -> Unit, fail: (String) -> Unit) = get("/api/points/balance", {
        val balance = it.getInt("balance"); AppContextHolder.context?.getSharedPreferences("vectr_local_sync", android.content.Context.MODE_PRIVATE)?.edit()?.putInt("berries_balance", balance)?.apply(); ok(balance)
    }, { error ->
        val context = AppContextHolder.context
        if (context?.getSharedPreferences("vectr_local_sync", android.content.Context.MODE_PRIVATE)?.contains("berries_balance") == true) ok(context.getSharedPreferences("vectr_local_sync", android.content.Context.MODE_PRIVATE).getInt("berries_balance", 0)) else fail(error)
    })
    fun history(ok: (List<Voyage>) -> Unit, fail: (String) -> Unit) { val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/voyages/history")).build(); client.newCall(request).enqueue(object: Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Could not load voyages"); override fun onResponse(call: Call, response: Response) = response.use { try { val list = JSONArray(it.body?.string() ?: "[]"); ok((0 until list.length()).map { i -> list.getJSONObject(i).let { v -> Voyage(v.getString("id"), v.getString("duration_tier"), v.getInt("duration_seconds"), v.getString("start_time"), v.getString("status"), if (v.isNull("berries_awarded")) null else v.getInt("berries_awarded")) } }) } catch (e: Exception) { fail(e.message ?: "Invalid voyage response") } } }) }
    fun start(tier: String, customDurationSeconds: Int? = null, ok: (JSONObject) -> Unit, fail: (String) -> Unit) {
        val body = JSONObject().put("durationTier", tier)
        customDurationSeconds?.let { body.put("customDurationSeconds", it) }
        post("/api/voyages/start", body, ok, fail)
    }
    fun complete(id: String, ok: (JSONObject) -> Unit, fail: (String) -> Unit) = post("/api/voyages/$id/complete", JSONObject(), ok, fail)
    fun abandon(id: String, ok: (JSONObject) -> Unit, fail: (String) -> Unit) = post("/api/voyages/$id/abandon", JSONObject(), ok, fail)
    private fun get(path: String, ok: (JSONObject) -> Unit, fail: (String) -> Unit) { try { val request = Request.Builder().url(DeviceWebSocket.apiUrl(path)).build(); client.newCall(request).enqueue(object: Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Request failed"); override fun onResponse(call: Call, response: Response) = response.use { try { if (!it.isSuccessful) fail("Request failed (${it.code})") else ok(JSONObject(it.body?.string() ?: "{}")) } catch(e:Exception){ fail(e.message ?: "Invalid response") } } }) } catch(e:Exception){ fail(e.message ?: "Not connected") } }
    private fun post(path: String, body: JSONObject, ok: (JSONObject) -> Unit, fail: (String) -> Unit) {
        try {
            val request = Request.Builder().url(DeviceWebSocket.apiUrl(path)).post(body.toString().toRequestBody(json)).build()
            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Request failed")
                override fun onResponse(call: Call, response: Response) = response.use {
                    try {
                        val raw = it.body?.string().orEmpty()
                        val data = JSONObject(raw.ifBlank { "{}" })
                        if (it.isSuccessful) ok(data) else fail(data.optString("error", "Request failed (${it.code})"))
                    } catch (error: Exception) {
                        fail("The server returned an invalid voyage response.")
                    }
                }
            })
        } catch (error: Exception) { fail(error.message ?: "Not connected") }
    }
}
