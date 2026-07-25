package com.vectr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class VectrForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var discovery: CoreDiscovery? = null
    private var reconnectScheduled = false

    override fun onCreate() {
        super.onCreate()
        AppContextHolder.context = applicationContext
        createChannel()
        startForeground(NOTIFICATION_ID, notification(false))
        DeviceWebSocket.observeConnection { connected ->
            handler.post {
                updateNotification(connected)
                if (connected) {
                    reconnectScheduled = false
                    OfflineCaptureQueue.flush(applicationContext)
                    SchedWallOfflineQueue.flush(applicationContext)
                    TodoRepository.flush(applicationContext)
                    InventoryOfflineQueue.flush(applicationContext)
                    OfflineFileQueue.flush(applicationContext)
                } else {
                    OfflineCaptureQueue.onConnectionLost()
                    scheduleReconnect()
                }
            }
        }
        DeviceWebSocket.observeMessages { type, payload ->
            if (type == "capture.new") OfflineCaptureQueue.onCaptureSaved(applicationContext, payload)
        }
        connectFromSavedEndpoint()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RECONNECT) connectFromSavedEndpoint(forceDiscovery = false)
        return START_STICKY
    }

    override fun onDestroy() {
        discovery?.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connectFromSavedEndpoint(forceDiscovery: Boolean = false) {
        reconnectScheduled = false
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val host = prefs.getString(PREF_HOST, null)?.trim().orEmpty()
        val port = prefs.getInt(PREF_PORT, DEFAULT_PORT)
        if (host.isNotBlank() && port in 1..65535 && !forceDiscovery) {
            DeviceWebSocket.setEndpoint(host, port)
            DeviceWebSocket.connectForStatus()
            return
        }
        discovery?.cancel()
        discovery = CoreDiscovery(this) { endpoint -> handler.post {
            if (endpoint != null) {
                prefs.edit().putString(PREF_HOST, endpoint.host).putInt(PREF_PORT, endpoint.port).apply()
                DeviceWebSocket.setEndpoint(endpoint.host, endpoint.port)
                DeviceWebSocket.connectForStatus()
            } else scheduleReconnect()
        } }
        discovery?.start()
    }

    private fun scheduleReconnect() {
        if (reconnectScheduled) return
        reconnectScheduled = true
        handler.postDelayed({
            reconnectScheduled = false
            // A saved address may belong to a previous network. Re-run mDNS instead of
            // retrying that stale address forever.
            connectFromSavedEndpoint(forceDiscovery = true)
        }, RECONNECT_DELAY_MS)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.connection_notification_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun notification(connected: Boolean): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(if (connected) R.drawable.ic_status_connected else R.drawable.ic_status_disconnected)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(if (connected) R.string.service_connected else R.string.service_reconnecting))
        .setOngoing(true)
        .build()

    private fun updateNotification(connected: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(connected))
    }

    companion object {
        private const val CHANNEL_ID = "vectr_connection"
        private const val NOTIFICATION_ID = 4101
        private const val RECONNECT_DELAY_MS = 5_000L
        private const val PREFS_NAME = "vectr_connection"
        private const val PREF_HOST = "core_host"
        private const val PREF_PORT = "core_port"
        private const val DEFAULT_PORT = 4101
        private const val ACTION_RECONNECT = "com.vectr.action.RECONNECT"

        fun start(context: Context) {
            val intent = Intent(context, VectrForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun reconnect(context: Context) {
            val intent = Intent(context, VectrForegroundService::class.java).setAction(ACTION_RECONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
