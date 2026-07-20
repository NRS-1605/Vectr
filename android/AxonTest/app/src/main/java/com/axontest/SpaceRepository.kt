package com.vectr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class SpaceNote(val filename: String, val heading: String, val tag: String, val timestamp: String, val preview: String, val body: String = "", val imageFilename: String? = null)
object SpaceRepository {
    private val client = OkHttpClient()
    fun fetchNotes(ok: (List<SpaceNote>) -> Unit, fail: (String) -> Unit) = request("/api/space/notes", { json -> ok(JSONArray(json).let(::parseList)) }, fail)
    fun fetchNote(filename: String, ok: (SpaceNote) -> Unit, fail: (String) -> Unit) = request("/api/space/notes/${Uri.encode(filename)}", { json -> ok(parse(JSONObject(json))) }, fail)
    fun deleteNote(filename: String, ok: () -> Unit, fail: (String) -> Unit) { client.newCall(Request.Builder().url(DeviceWebSocket.apiUrl("/api/space/notes/${Uri.encode(filename)}")).delete().build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Could not delete note"); override fun onResponse(call: Call, response: Response) = response.use { if (it.isSuccessful) ok() else fail("Could not delete note (${it.code})") } }) }
    fun fetchImage(filename: String, ok: (Bitmap) -> Unit, fail: (String) -> Unit) { client.newCall(Request.Builder().url(DeviceWebSocket.apiUrl("/api/space/attachments/${Uri.encode(filename)}")).build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Could not load image"); override fun onResponse(call: Call, response: Response) = response.use { if (!it.isSuccessful) return fail("Could not load image"); it.body?.byteStream()?.use { stream -> BitmapFactory.decodeStream(stream)?.let(ok) ?: fail("Invalid image") } ?: fail("Empty image") } }) }
    private fun request(path: String, ok: (String) -> Unit, fail: (String) -> Unit) { client.newCall(Request.Builder().url(DeviceWebSocket.apiUrl(path)).build()).enqueue(object : Callback { override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Could not load Space"); override fun onResponse(call: Call, response: Response) = response.use { if (!it.isSuccessful) return fail("Could not load Space (${it.code})"); ok(it.body?.string() ?: "") } }) }
    private fun parseList(array: JSONArray) = (0 until array.length()).map { parse(array.getJSONObject(it)) }
    private fun parse(item: JSONObject) = SpaceNote(item.getString("filename"), item.optString("heading", "Untitled"), item.optString("tag", "untagged"), item.optString("timestamp"), item.optString("preview"), item.optString("body"), item.optString("imageFilename").takeIf(String::isNotBlank))
}
