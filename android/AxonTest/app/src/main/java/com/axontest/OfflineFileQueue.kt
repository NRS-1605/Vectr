package com.vectr

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object OfflineFileQueue {
    private const val QUEUE = "file_upload_queue"

    fun enqueue(context: Context, uri: Uri): String {
        val name = FileRepository.displayName(context.contentResolver, uri) ?: "upload"
        val directory = File(context.filesDir, "queued-files").apply { mkdirs() }
        val local = File(directory, "${UUID.randomUUID()}-$name")
        context.contentResolver.openInputStream(uri)?.use { input -> local.outputStream().use(input::copyTo) }
            ?: throw IllegalArgumentException("Could not read selected file")
        LocalSyncStore.readArray(context, QUEUE).also { it.put(JSONObject().put("path", local.absolutePath).put("name", name)) }
            .let { LocalSyncStore.writeArray(context, QUEUE, it) }
        return name
    }

    fun count(context: Context) = LocalSyncStore.readArray(context, QUEUE).length()

    fun flush(context: Context) {
        if (!DeviceWebSocket.isConnected()) return
        val entry = LocalSyncStore.readArray(context, QUEUE).optJSONObject(0) ?: return
        val file = File(entry.optString("path"))
        if (!file.exists()) { removeFirst(context); flush(context); return }
        FileRepository.uploadFile(file, entry.optString("name"), {}, {
            file.delete(); removeFirst(context); flush(context)
        }, { /* preserve the file and retry later */ })
    }

    private fun removeFirst(context: Context) {
        val source = LocalSyncStore.readArray(context, QUEUE); val remaining = JSONArray()
        for (i in 1 until source.length()) remaining.put(source.getJSONObject(i))
        LocalSyncStore.writeArray(context, QUEUE, remaining)
    }
}
