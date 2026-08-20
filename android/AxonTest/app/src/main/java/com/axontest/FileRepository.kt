package com.vectr

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MediaType.Companion.toMediaType
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.File

data class TransferFile(val filename: String, val size: Long)

object FileRepository {
    private val client = OkHttpClient()

    fun upload(contentResolver: ContentResolver, uri: Uri, onProgress: (Int) -> Unit, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val filename = runCatching { displayName(contentResolver, uri) }.getOrNull() ?: "upload"
        val size = runCatching { contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L }.getOrDefault(-1L)
        val mediaType = runCatching { contentResolver.getType(uri)?.substringBefore(";")?.toMediaTypeOrNull() }.getOrNull() ?: "application/octet-stream".toMediaType()
        val body = object : RequestBody() {
            override fun contentType(): MediaType = mediaType
            override fun contentLength() = size
            override fun writeTo(sink: BufferedSink) {
                contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        sink.write(buffer, 0, count)
                        copied += count
                        if (size > 0) onProgress(((copied * 100) / size).toInt())
                    }
                } ?: throw IOException("Could not open selected file")
            }
        }
        val request = try {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", filename, body).build()
            Request.Builder().url(DeviceWebSocket.apiUrl("/api/files/upload")).post(form).build()
        } catch (error: Exception) {
            onError(error.message ?: "Could not prepare file upload")
            return
        }
        try { client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not upload file")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not upload file (${it.code})")
                try { onSuccess(JSONObject(it.body?.string() ?: "{}").getString("filename")) }
                catch (error: Exception) { onError(error.message ?: "Could not read upload response") }
            }
        }) } catch (error: Exception) { onError(error.message ?: "Could not start file upload") }
    }

    fun uploadFile(file: File, filename: String = file.name, onProgress: (Int) -> Unit = {}, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val mediaType = "application/octet-stream".toMediaType()
        val body = object : RequestBody() {
            override fun contentType() = mediaType
            override fun contentLength() = file.length()
            override fun writeTo(sink: BufferedSink) {
                file.inputStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var copied = 0L
                    while (true) { val count = input.read(buffer); if (count < 0) break; sink.write(buffer, 0, count); copied += count; if (file.length() > 0) onProgress(((copied * 100) / file.length()).toInt()) }
                }
            }
        }
        try {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", filename, body).build()
            client.newCall(Request.Builder().url(DeviceWebSocket.apiUrl("/api/files/upload")).post(form).build()).enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not upload file")
                override fun onResponse(call: Call, response: Response) = response.use { if (!it.isSuccessful) onError("Could not upload file (${it.code})") else onSuccess(JSONObject(it.body?.string() ?: "{}").optString("filename", filename)) }
            })
        } catch (error: Exception) { onError(error.message ?: "Could not start file upload") }
    }

    fun fetchFiles(onSuccess: (List<TransferFile>) -> Unit, onError: (String) -> Unit) {
        val request = try { Request.Builder().url(DeviceWebSocket.apiUrl("/api/files/list")).build() }
        catch (error: Exception) { return cachedFiles(onSuccess, onError, error.message ?: "Could not load files") }
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = cachedFiles(onSuccess, onError, error.message ?: "Could not load files")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return cachedFiles(onSuccess, onError, "Could not load files (${it.code})")
                try {
                    val files = JSONArray(it.body?.string() ?: "[]")
                    onSuccess((0 until files.length()).map { index ->
                        files.getJSONObject(index).let { TransferFile(it.getString("filename"), it.getLong("size")) }
                    }.also { result -> AppContextHolder.context?.let { LocalSyncStore.writeArray(it, "files_cache", files) } })
                } catch (error: Exception) { onError(error.message ?: "Could not read file list") }
            }
        })
    }

    private fun cachedFiles(onSuccess: (List<TransferFile>) -> Unit, onError: (String) -> Unit, error: String) {
        val context = AppContextHolder.context
        if (context == null || !LocalSyncStore.has(context, "files_cache")) return onError(error)
        val cache = LocalSyncStore.readArray(context, "files_cache")
        onSuccess((0 until cache.length()).map { index -> cache.getJSONObject(index).let { TransferFile(it.getString("filename"), it.getLong("size")) } })
    }

    fun download(contentResolver: ContentResolver, file: TransferFile, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val url = DeviceWebSocket.apiUrl("/api/files/download/${Uri.encode(file.filename)}")
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not download file")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not download file (${it.code})")
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, file.filename)
                    put(MediaStore.Downloads.MIME_TYPE, response.header("Content-Type") ?: "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val downloadUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return onError("Could not create Downloads file")
                try {
                    response.body?.byteStream()?.use { input ->
                        contentResolver.openOutputStream(downloadUri)?.use { output -> input.copyTo(output) }
                            ?: throw IOException("Could not write Downloads file")
                    } ?: throw IOException("Empty download response")
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(downloadUri, values, null, null)
                    onSuccess()
                } catch (error: Exception) {
                    contentResolver.delete(downloadUri, null, null)
                    onError(error.message ?: "Could not save download")
                }
            }
        })
    }

    fun displayName(contentResolver: ContentResolver, uri: Uri): String? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}
