package com.vectr

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Keeps inventory entries (including a selected photo) on the phone until core is available. */
object InventoryOfflineQueue {
    private const val CACHE = "inventory_cache"
    private const val QUEUE = "inventory_queue"

    fun cached(context: Context): List<InventoryItem> = parse(LocalSyncStore.readArray(context, CACHE))
    fun cache(context: Context, items: List<InventoryItem>) = LocalSyncStore.writeArray(context, CACHE, JSONArray().apply {
        items.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("quantity", it.quantity).put("manufactureDate", it.manufactureDate).put("expiryDate", it.expiryDate).put("photoUrl", it.photoUrl)) }
    })

    fun enqueue(context: Context, name: String, quantity: Int, manufacture: String, expiry: String, photo: Uri?): InventoryItem {
        val id = "local-${UUID.randomUUID()}"
        val photoPath = photo?.let { uri ->
            File(context.filesDir, "offline-inventory").apply { mkdirs() }.let { dir ->
                File(dir, "$id.jpg").also { output -> context.contentResolver.openInputStream(uri)?.use { input -> output.outputStream().use(input::copyTo) } }
            }.absolutePath
        }
        val queued = JSONObject().put("id", id).put("name", name).put("quantity", quantity).put("manufacture", manufacture).put("expiry", expiry).put("photoPath", photoPath)
        LocalSyncStore.readArray(context, QUEUE).also { it.put(queued) }.let { LocalSyncStore.writeArray(context, QUEUE, it) }
        val item = InventoryItem(id, name, quantity, manufacture, expiry, null)
        cache(context, cached(context) + item)
        return item
    }

    fun flush(context: Context) {
        if (!DeviceWebSocket.isConnected()) return
        val queued = LocalSyncStore.readArray(context, QUEUE).optJSONObject(0) ?: return
        InventoryRepository.addStored(queued, { remote ->
            val rest = LocalSyncStore.readArray(context, QUEUE); val next = JSONArray()
            for (i in 1 until rest.length()) next.put(rest.getJSONObject(i))
            LocalSyncStore.writeArray(context, QUEUE, next)
            queued.optString("photoPath").takeIf { it.isNotBlank() }?.let { File(it).delete() }
            cache(context, cached(context).map { if (it.id == queued.optString("id")) remote else it })
            flush(context)
        }, { /* keep it until a later reconnection */ })
    }

    private fun parse(array: JSONArray) = (0 until array.length()).map { index -> array.getJSONObject(index).let {
        InventoryItem(it.getString("id"), it.getString("name"), it.optInt("quantity", 1), it.getString("manufactureDate"), it.getString("expiryDate"), it.optString("photoUrl").takeIf(String::isNotBlank))
    } }
}
