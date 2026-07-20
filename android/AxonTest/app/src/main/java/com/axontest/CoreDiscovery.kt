package com.vectr

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.util.Log

class CoreDiscovery(context: Context, private val onComplete: (DeviceWebSocket.Endpoint?) -> Unit) {
    private val nsdManager = context.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var complete = false

    fun start() {
        val timeout = Runnable { finish(null) }
        handler.postDelayed(timeout, DISCOVERY_TIMEOUT_MS)
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (complete || serviceInfo.serviceType != SERVICE_TYPE) return
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Could not resolve ${serviceInfo.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val host = serviceInfo.host?.hostAddress
                        if (!host.isNullOrBlank() && serviceInfo.port > 0) finish(DeviceWebSocket.Endpoint(host, serviceInfo.port))
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS discovery could not start: $errorCode")
                finish(null)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "mDNS discovery could not stop: $errorCode")
            }
        }
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (error: Exception) {
            Log.w(TAG, "mDNS discovery failed", error)
            finish(null)
        }
    }

    fun cancel() = finish(null, notify = false)

    private fun finish(endpoint: DeviceWebSocket.Endpoint?, notify: Boolean = true) {
        if (complete) return
        complete = true
        handler.removeCallbacksAndMessages(null)
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: Exception) {
                // Discovery may already have failed or been stopped by Android.
            }
        }
        if (notify) onComplete(endpoint)
    }

    private companion object {
        const val TAG = "VectrDiscovery"
        const val SERVICE_TYPE = "_vectr._tcp."
        const val DISCOVERY_TIMEOUT_MS = 5_000L
    }
}
