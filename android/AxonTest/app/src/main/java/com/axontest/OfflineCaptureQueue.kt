package com.vectr

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object OfflineCaptureQueue {
    private const val PREFS = "vectr_offline_captures"
    private const val KEY = "captures"
    private var active: JSONObject? = null
    private var flushing = false
    private val observers = mutableSetOf<(Int) -> Unit>()

    fun observe(context: Context, observer: (Int) -> Unit) {
        observers += observer
        observer(read(context).length())
    }

    fun count(context: Context) = read(context).length()

    fun enqueue(context: Context, heading: String, tag: String, body: String, photo: Bitmap? = null, voicePath: String? = null, remoteImageFilename: String? = null) {
        val attachmentPath = photo?.let { savePhoto(context, it) } ?: voicePath
        val type = when { attachmentPath == null -> "text"; voicePath != null -> "voice"; else -> "photo" }
        val items = read(context)
        items.put(JSONObject()
            .put("id", UUID.randomUUID().toString())
            .put("type", type)
            .put("heading", heading)
            .put("tag", tag)
            .put("body", body)
            .put("attachmentPath", attachmentPath)
            .put("remoteImageFilename", remoteImageFilename)
            .put("timestamp", System.currentTimeMillis()))
        write(context, items)
    }

    fun flush(context: Context) {
        if (!DeviceWebSocket.isConnected() || flushing) return
        val item = read(context).optJSONObject(0) ?: return
        flushing = true
        active = item
        val attachment = item.optString("attachmentPath").takeIf { it.isNotBlank() }?.let(::File)
        if (item.optString("type") == "voice" && attachment != null && attachment.exists()) {
            CaptureRepository.uploadVoiceFile(attachment,
                onSuccess = { completeVoice(context, item) },
                onError = { stopFlush() },
            )
        } else if (attachment != null && attachment.exists()) {
            CaptureRepository.uploadImageFile(attachment,
                onSuccess = { filename -> dispatch(context, item, filename) },
                onError = { stopFlush() },
            )
        } else dispatch(context, item, item.optString("remoteImageFilename").takeIf { it.isNotBlank() })
    }

    fun onConnectionLost() { flushing = false; active = null }

    fun onCaptureSaved(context: Context, payload: JSONObject) {
        val item = active ?: return
        val matches = payload.optString("heading") == item.optString("heading") &&
            payload.optString("tag") == item.optString("tag") &&
            payload.optString("preview") == item.optString("body").take(100)
        if (!matches) return
        val id = item.optString("id")
        val remaining = JSONArray()
        val items = read(context)
        for (index in 0 until items.length()) {
            val queued = items.getJSONObject(index)
            if (queued.optString("id") != id) remaining.put(queued)
            else queued.optString("attachmentPath").takeIf { it.isNotBlank() }?.let { File(it).delete() }
        }
        active = null
        flushing = false
        write(context, remaining)
        flush(context)
    }

    private fun dispatch(context: Context, item: JSONObject, imageFilename: String?) {
        if (!DeviceWebSocket.sendQueuedCapture(item, imageFilename)) stopFlush()
    }

    private fun stopFlush() { flushing = false; active = null }

    private fun completeVoice(context: Context, item: JSONObject) {
        val id = item.optString("id")
        val remaining = JSONArray()
        val items = read(context)
        for (index in 0 until items.length()) {
            val queued = items.getJSONObject(index)
            if (queued.optString("id") != id) remaining.put(queued)
            else queued.optString("attachmentPath").takeIf { it.isNotBlank() }?.let { File(it).delete() }
        }
        active = null
        flushing = false
        write(context, remaining)
        flush(context)
    }

    private fun savePhoto(context: Context, photo: Bitmap): String {
        val directory = File(context.filesDir, "queued-captures").apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}.jpg").also { file -> file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) } }.absolutePath
    }

    private fun read(context: Context): JSONArray = try { JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
    private fun write(context: Context, items: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, items.toString()).apply()
        observers.toList().forEach { it(items.length()) }
    }
}
