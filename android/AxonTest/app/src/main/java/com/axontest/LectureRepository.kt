package com.vectr

import android.net.Uri
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class LectureSubject(val slug: String, val count: Int)
data class LectureSummary(
    val subject: String, val date: String, val time: String,
    val title: String, val filename: String, val preview: String, val audio: String?
)
data class LectureDetail(
    val subject: String, val date: String, val time: String,
    val title: String, val filename: String, val transcript: String,
    val preview: String, val audio: String?
)

object LectureRepository {
    private val client = OkHttpClient()

    fun fetchSubjects(onSuccess: (List<LectureSubject>) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/lectures/subjects")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Could not load subjects")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not load subjects (${it.code})")
                try {
                    val arr = JSONArray(it.body?.string() ?: "[]")
                    onSuccess((0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        LectureSubject(obj.getString("slug"), obj.optInt("count", 0))
                    })
                } catch (e: Exception) { onError(e.message ?: "Could not parse subjects") }
            }
        })
    }

    fun fetchLectures(subjectSlug: String, onSuccess: (List<LectureSummary>) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/lectures/${Uri.encode(subjectSlug)}")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Could not load lectures")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not load lectures (${it.code})")
                try {
                    val body = JSONObject(it.body?.string() ?: "{}")
                    val arr = body.optJSONArray("lectures") ?: JSONArray()
                    onSuccess((0 until arr.length()).map { i -> parseSummary(arr.getJSONObject(i)) })
                } catch (e: Exception) { onError(e.message ?: "Could not parse lectures") }
            }
        })
    }

    fun fetchLecture(subjectSlug: String, filename: String, onSuccess: (LectureDetail) -> Unit, onError: (String) -> Unit) {
        val request = Request.Builder().url(DeviceWebSocket.apiUrl("/api/lectures/${Uri.encode(subjectSlug)}")).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = onError(e.message ?: "Could not load lecture")
            override fun onResponse(call: Call, response: Response) = response.use {
                if (!it.isSuccessful) return onError("Could not load lecture (${it.code})")
                try {
                    val body = JSONObject(it.body?.string() ?: "{}")
                    val arr = body.optJSONArray("lectures") ?: JSONArray()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        if (obj.optString("filename") == filename) {
                            return onSuccess(LectureDetail(
                                subject = obj.optString("subject", ""),
                                date = obj.optString("date", ""),
                                time = obj.optString("time", ""),
                                title = obj.optString("title", ""),
                                filename = obj.optString("filename", ""),
                                transcript = obj.optString("transcript", ""),
                                preview = obj.optString("preview", ""),
                                audio = obj.optString("audio").takeIf { it.isNotBlank() },
                            ))
                        }
                    }
                    onError("Lecture not found")
                } catch (e: Exception) { onError(e.message ?: "Could not parse lecture") }
            }
        })
    }

    private fun parseSummary(obj: JSONObject) = LectureSummary(
        subject = obj.optString("subject", ""),
        date = obj.optString("date", ""),
        time = obj.optString("time", ""),
        title = obj.optString("title", ""),
        filename = obj.optString("filename", ""),
        preview = obj.optString("preview", ""),
        audio = obj.optString("audio").takeIf { it.isNotBlank() },
    )
}