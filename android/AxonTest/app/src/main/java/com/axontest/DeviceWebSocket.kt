package com.vectr

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID

object DeviceWebSocket {
    private val featureSessions = mutableMapOf<String, String>()
    data class Endpoint(val host: String, val port: Int)

    private const val DEFAULT_PORT = 4101
    private var deviceId = "android-uninitialized"

    private val client = OkHttpClient()
    private var statusSocket: WebSocket? = null
    private val connectionListeners = mutableSetOf<(Boolean) -> Unit>()
    private val messageListeners = mutableSetOf<(String, JSONObject) -> Unit>()
    private var isConnected = false
    @Volatile private var endpoint: Endpoint? = null

    fun initializeDeviceId(context: Context) {
        val prefs = context.getSharedPreferences("vectr_device", Context.MODE_PRIVATE)
        deviceId = prefs.getString("id", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("id", it).apply() }
    }
    fun deviceIdentity() = deviceId

    fun setEndpoint(host: String, port: Int) {
        endpoint = Endpoint(host.trim(), port)
    }

    fun currentEndpoint() = endpoint

    fun observeConnection(listener: (Boolean) -> Unit) {
        connectionListeners += listener
        listener(isConnected)
    }

    fun isConnected() = isConnected

    fun connectForStatus() {
        val endpoint = endpoint ?: run {
            updateConnection(false)
            return
        }
        statusSocket?.close(1000, "Replacing connection")
        val request = Request.Builder().url(webSocketUrl(endpoint)).build()
        lateinit var socket: WebSocket
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (statusSocket === webSocket) updateConnection(true)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (statusSocket === webSocket) {
                    statusSocket = null
                    updateConnection(false)
                }
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                if (statusSocket === webSocket) {
                    statusSocket = null
                    updateConnection(false)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val message = JSONObject(text)
                    val type = message.optString("type")
                    val payload = message.optJSONObject("payload") ?: JSONObject()
                    if (type == "macro.result") Log.d("VectrMacro", "result received requestId=${payload.optString("requestId", "missing")} buttonId=${payload.optInt("buttonId", -1)}")
                    messageListeners.toList().forEach { it(type, payload) }
                } catch (_: Exception) {
                    // Ignore malformed messages; connection status remains valid.
                }
            }
        })
        statusSocket = socket
    }

    fun observeMessages(listener: (String, JSONObject) -> Unit) {
        messageListeners += listener
    }

    fun sendScreenSubscription(screen: String, subscribe: Boolean): Boolean {
        val socket = statusSocket
        if (socket == null || !isConnected) return false
        val action = if (subscribe) "subscribe" else "unsubscribe"
        return socket.send(
            JSONObject()
                .put("type", "$screen.$action")
                .put("payload", JSONObject().put("sessionId", featureSessionId(if (screen == "macro") "macros" else screen)))
                .put("deviceId", deviceId)
                .put("timestamp", System.currentTimeMillis())
                .toString(),
        )
    }

    fun beginFeatureSession(feature: String) { featureSessions[feature] = UUID.randomUUID().toString() }
    fun endFeatureSession(feature: String) { featureSessions.remove(feature) }
    fun featureSessionId(feature: String): String = featureSessions.getOrPut(feature) { UUID.randomUUID().toString() }

    fun sendMacroTrigger(buttonId: Int, requestId: String, onError: (String) -> Unit) {
        val socket = statusSocket
        if (socket == null || !isConnected) {
            onError("WebSocket is disconnected")
            return
        }
        val message = JSONObject()
            .put("type", "macro.trigger")
            .put("payload", JSONObject().put("buttonId", buttonId).put("requestId", requestId))
            .put("deviceId", deviceId)
            .put("timestamp", System.currentTimeMillis())
        if (socket.send(message.toString())) Log.d("VectrMacro", "trigger sent requestId=$requestId buttonId=$buttonId")
        else onError("Could not send macro trigger")
    }

    fun sendTouchpadMove(dx: Float, dy: Float) = sendTouchpadEvent("touchpad.move", JSONObject().put("dx", dx).put("dy", dy))

    fun sendTouchpadScroll(dy: Float) = sendTouchpadEvent("touchpad.scroll", JSONObject().put("dy", dy))

    fun sendTouchpadClick(rightButton: Boolean = false) {
        val payload = JSONObject()
        if (rightButton) payload.put("button", "right")
        sendTouchpadEvent("touchpad.click", payload)
    }

    fun sendClipboardUpdate(text: String, onSent: () -> Unit, onError: (String) -> Unit) {
        val socket = statusSocket
        if (socket == null || !isConnected) {
            onError("WebSocket is disconnected")
            return
        }
        val message = JSONObject()
            .put("type", "clipboard.update")
            .put("payload", JSONObject().put("text", text).put("source", "phone"))
            .put("deviceId", deviceId)
            .put("timestamp", System.currentTimeMillis())
        if (socket.send(message.toString())) onSent()
        else onError("Could not send clipboard")
    }

    private fun sendTouchpadEvent(type: String, payload: JSONObject) {
        val socket = statusSocket
        if (socket == null || !isConnected) {
            Log.w("VectrTouchpad", "$type dropped: WebSocket is disconnected")
            return
        }
        val message = JSONObject()
            .put("type", type)
            .put("payload", payload)
            .put("deviceId", deviceId)
            .put("timestamp", System.currentTimeMillis())
        if (!socket.send(message.toString())) Log.w("VectrTouchpad", "$type could not be sent")
    }

    fun apiUrl(path: String): String {
        val endpoint = requireNotNull(endpoint) { "No axon-core endpoint is configured" }
        return "http://${hostForUrl(endpoint.host)}:${endpoint.port}$path"
    }

    private fun send(type: String, payload: JSONObject, onSent: () -> Unit, onError: (String) -> Unit) {
        val endpoint = endpoint ?: run {
            onError("No axon-core endpoint is configured")
            return
        }
        val request = Request.Builder().url(webSocketUrl(endpoint)).build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val message = JSONObject()
                    .put("type", type)
                    .put("payload", payload)
                    .put("deviceId", deviceId)
                    .put("timestamp", System.currentTimeMillis())

                if (webSocket.send(message.toString())) {
                    onSent()
                    webSocket.close(1000, "Sent")
                } else {
                    onError("Could not send message")
                    webSocket.close(1011, "Send failed")
                }
            }

            override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                onError(throwable.message ?: "WebSocket connection failed")
            }
        })
    }

    private fun webSocketUrl(endpoint: Endpoint) = "ws://${hostForUrl(endpoint.host)}:${endpoint.port}/ws?deviceId=$deviceId"

    private fun hostForUrl(host: String) = if (host.contains(":")) "[$host]" else host

    private fun updateConnection(connected: Boolean) {
        isConnected = connected
        connectionListeners.toList().forEach { it(connected) }
    }

    fun sendTestPing(onSent: () -> Unit, onError: (String) -> Unit) {
        send(
            type = "test.ping",
            payload = JSONObject().put("message", "hello from android"),
            onSent = onSent,
            onError = onError,
        )
    }

    fun sendDeviceCapture(heading: String, tag: String, body: String, imageFilename: String? = null, onSent: () -> Unit, onError: (String) -> Unit) {
        if (!isConnected) {
            AppContextHolder.context?.let { OfflineCaptureQueue.enqueue(it, heading, tag, body, remoteImageFilename = imageFilename) }
            onError("Capture queued and will sync when reconnected")
            return
        }
        val payload = JSONObject().put("heading", heading).put("tag", tag).put("body", body)
        if (imageFilename != null) payload.put("imageFilename", imageFilename)
        send(
            type = "capture.new_from_device",
            payload = payload,
            onSent = onSent,
            onError = onError,
        )
    }

    fun sendQueuedCapture(item: JSONObject, imageFilename: String? = null): Boolean {
        val socket = statusSocket ?: return false
        val payload = JSONObject().put("heading", item.optString("heading")).put("tag", item.optString("tag")).put("body", item.optString("body"))
        imageFilename?.takeIf { it.isNotBlank() }?.let { payload.put("imageFilename", it) }
        return socket.send(JSONObject().put("type", "capture.new_from_device").put("payload", payload).put("deviceId", deviceId).put("timestamp", System.currentTimeMillis()).toString())
    }
}
