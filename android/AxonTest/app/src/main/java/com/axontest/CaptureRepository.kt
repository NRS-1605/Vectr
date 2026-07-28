package com.vectr

import android.graphics.Bitmap
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.File

data class VoiceTranscription(val transcript: String, val heading: String)
data class LectureTranscription(val transcript: String, val lectureSubject: String, val lectureDate: String, val lectureTime: String, val lectureFilename: String)

object CaptureRepository {
    private val client = OkHttpClient()

    fun fetchTags(onSuccess: (List<String>) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/capture/tags")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not load tags")

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return onError("Could not load tags (${it.code})")
                    try {
                        val tags = JSONArray(it.body?.string() ?: "[]")
                        onSuccess((0 until tags.length()).map { index -> tags.getString(index) })
                    } catch (error: Exception) {
                        onError(error.message ?: "Could not read tags")
                    }
                }
            }
        })
    }

    fun uploadImage(bitmap: Bitmap, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            output.toByteArray()
        }
        val imageBody = bytes.toRequestBody("image/jpeg".toMediaType())
        val form = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("image", "capture.jpg", imageBody)
            .build()
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/capture/upload-image")).post(form).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not upload image")

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) return onError("Could not upload image (${it.code})")
                    try {
                        onSuccess(JSONObject(it.body?.string() ?: "{}").getString("filename"))
                    } catch (error: Exception) {
                        onError(error.message ?: "Could not read upload response")
                    }
                }
            }
        })
    }

    fun uploadImageFile(file: File, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", file.name, file.readBytes().toRequestBody("image/jpeg".toMediaType())).build()
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/capture/upload-image")).post(form).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not upload image")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not upload image (${it.code})")
                try { onSuccess(JSONObject(it.body?.string() ?: "{}").getString("filename")) }
                catch (error: Exception) { onError(error.message ?: "Could not read upload response") }
            }
        })
    }

    fun uploadVoiceFile(file: File, saveCapture: Boolean = true, onSuccess: (VoiceTranscription) -> Unit, onError: (String) -> Unit) {
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("save", saveCapture.toString())
            .addFormDataPart("audio", file.name, file.readBytes().toRequestBody("audio/wav".toMediaType())).build()
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/capture/upload-voice")).post(form).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not upload recording")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) {
                    val message = try { JSONObject(it.body?.string() ?: "{}").optString("error") } catch (_: Exception) { "" }
                    return onError(message.ifBlank { "Could not transcribe recording (${it.code})" })
                }
                try {
                    val body = JSONObject(it.body?.string() ?: "{}")
                    onSuccess(VoiceTranscription(body.getString("transcript"), body.optJSONObject("capture")?.optString("heading") ?: "Voice"))
                } catch (error: Exception) { onError(error.message ?: "Could not read transcription") }
            }
        })
    }

    fun uploadLectureVoice(file: File, subject: String, date: String, slot: String? = null, heading: String? = null, onSuccess: (LectureTranscription) -> Unit, onError: (String) -> Unit) {
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("lecture", "true")
            .addFormDataPart("subject", subject)
            .addFormDataPart("date", date)
            .addFormDataPart("save", "false")
        if (slot != null) builder.addFormDataPart("slot", slot)
        if (heading != null) builder.addFormDataPart("heading", heading)
        builder.addFormDataPart("audio", file.name, file.readBytes().toRequestBody("audio/wav".toMediaType()))
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/capture/upload-voice")).post(builder.build()).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) = onError(error.message ?: "Could not upload lecture recording")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) {
                    val message = try { JSONObject(it.body?.string() ?: "{}").optString("error") } catch (_: Exception) { "" }
                    return onError(message.ifBlank { "Could not transcribe lecture (${it.code})" })
                }
                try {
                    val body = JSONObject(it.body?.string() ?: "{}")
                    val lecture = body.optJSONObject("lecture")
                    onSuccess(LectureTranscription(
                        transcript = body.getString("transcript"),
                        lectureSubject = lecture?.optString("subject") ?: subject,
                        lectureDate = lecture?.optString("date") ?: date,
                        lectureTime = lecture?.optString("time") ?: (slot ?: ""),
                        lectureFilename = lecture?.optString("lectureFilename") ?: "",
                    ))
                } catch (error: Exception) { onError(error.message ?: "Could not read lecture transcription") }
            }
        })
    }
}
