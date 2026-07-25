package com.vectr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File

data class InventoryItem(val id: String, val name: String, val quantity: Int, val manufactureDate: String, val expiryDate: String, val photoUrl: String?)

object InventoryRepository {
    private val client = OkHttpClient()
    fun list(ok: (List<InventoryItem>) -> Unit, fail: (String) -> Unit) {
        requestSafely({ Request.Builder().url(DeviceWebSocket.apiUrl("/api/inventory")).build() }, { raw ->
            val data = JSONArray(raw); val items = (0 until data.length()).map { data.getJSONObject(it).let(::parse) }
            AppContextHolder.context?.let { InventoryOfflineQueue.cache(it, items) }; ok(items)
        }, { error -> AppContextHolder.context?.let { context -> if (LocalSyncStore.has(context, "inventory_cache")) ok(InventoryOfflineQueue.cached(context)) else fail(error) } ?: fail(error) })
    }
    fun add(resolver: ContentResolver, name: String, quantity: Int, manufactureDate: String, expiryDate: String, photo: Uri?, ok: (InventoryItem) -> Unit, fail: (String) -> Unit) {
        try {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("name", name).addFormDataPart("quantity", quantity.toString()).addFormDataPart("manufactureDate", manufactureDate).addFormDataPart("expiryDate", expiryDate)
            if (photo != null) {
                val bytes = resolver.openInputStream(photo)?.use { it.readBytes() } ?: return fail("Could not read the selected photo")
                form.addFormDataPart("photo", "item-photo.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
            }
            requestSafely({ Request.Builder().url(DeviceWebSocket.apiUrl("/api/inventory")).post(form.build()).build() }, { ok(parse(JSONObject(it))) }, fail)
        } catch (error: Exception) { fail(error.message ?: "Could not save inventory item") }
    }
    fun addStored(item: JSONObject, ok: (InventoryItem) -> Unit, fail: (String) -> Unit) {
        try {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("name", item.getString("name")).addFormDataPart("quantity", item.getInt("quantity").toString())
                .addFormDataPart("manufactureDate", item.getString("manufacture")).addFormDataPart("expiryDate", item.getString("expiry"))
            item.optString("photoPath").takeIf { it.isNotBlank() }?.let { path -> File(path).takeIf(File::exists)?.let { file -> form.addFormDataPart("photo", file.name, file.readBytes().toRequestBody("image/jpeg".toMediaType())) } }
            requestSafely({ Request.Builder().url(DeviceWebSocket.apiUrl("/api/inventory")).post(form.build()).build() }, { ok(parse(JSONObject(it))) }, fail)
        } catch (error: Exception) { fail(error.message ?: "Could not sync inventory item") }
    }
    fun delete(id: String, ok: () -> Unit, fail: (String) -> Unit) = requestSafely({ Request.Builder().url(DeviceWebSocket.apiUrl("/api/inventory/$id")).delete().build() }, { ok() }, fail)
    fun photo(url: String, ok: (Bitmap) -> Unit, fail: (String) -> Unit) {
        val request = try { Request.Builder().url(DeviceWebSocket.apiUrl(url)).build() } catch (error: Exception) { fail(error.message ?: "VeCTR core is not connected"); return }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Could not load photo")
            override fun onResponse(call: Call, response: Response) { response.use { result -> if (!result.isSuccessful) fail("Could not load photo") else result.body?.byteStream()?.use { stream -> BitmapFactory.decodeStream(stream)?.let(ok) ?: fail("Invalid photo") } ?: fail("Empty photo") } }
        })
    }
    private fun requestSafely(create: () -> Request, ok: (String) -> Unit, fail: (String) -> Unit) {
        try { request(create(), ok, fail) } catch (error: Exception) { fail(error.message ?: "VeCTR core is not connected") }
    }
    private fun request(request: Request, ok: (String) -> Unit, fail: (String) -> Unit) = client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) = fail(e.message ?: "Request failed")
        override fun onResponse(call: Call, response: Response) = response.use { val raw = it.body?.string().orEmpty(); if (it.isSuccessful) ok(raw) else fail(runCatching { JSONObject(raw).optString("error") }.getOrDefault("Request failed (${it.code})")) }
    })
    private fun parse(data: JSONObject) = InventoryItem(data.getString("id"), data.getString("name"), data.optInt("quantity", 1), data.getString("manufacture_date"), data.getString("expiry_date"), data.optString("photo_url").takeIf { it.isNotBlank() })
}
