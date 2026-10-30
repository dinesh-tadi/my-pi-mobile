package com.argon.hub.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

sealed class ConnectionState {
    data object Checking : ConnectionState()
    data class Connected(val host: String, val mode: String, val latencyMs: Long) : ConnectionState()
    data object Offline : ConnectionState()
}

class NetworkManager {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Checking)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val lanHost = "192.168.1.10"
    private val tailscaleHost = "100.123.87.122"
    private val dnsHost = "argon-host"

    fun getActiveHost(): String {
        return when (val state = _connectionState.value) {
            is ConnectionState.Connected -> state.host
            else -> tailscaleHost
        }
    }

    suspend fun checkConnectivity() = withContext(Dispatchers.IO) {
        _connectionState.value = ConnectionState.Checking

        // Concurrently check LAN (400ms timeout) and Tailscale (1500ms timeout)
        val lanJob = async { testHost(lanHost, 8089, 400) ?: testHost(lanHost, 80, 400) }
        val tsJob = async { testHost(tailscaleHost, 8089, 1500) ?: testHost(tailscaleHost, 80, 1500) }

        val lanPing = lanJob.await()
        if (lanPing != null) {
            _connectionState.value = ConnectionState.Connected(lanHost, "Home Wi-Fi", lanPing)
            return@withContext
        }

        val tsPing = tsJob.await()
        if (tsPing != null) {
            _connectionState.value = ConnectionState.Connected(tailscaleHost, "Tailscale Mesh", tsPing)
            return@withContext
        }

        // 3. Test MagicDNS
        val dnsPing = testHost(dnsHost, 80, 600)
        if (dnsPing != null) {
            _connectionState.value = ConnectionState.Connected(dnsHost, "Tailscale DNS", dnsPing)
            return@withContext
        }

        // Neither reached
        _connectionState.value = ConnectionState.Offline
    }

    private fun testHost(host: String, port: Int, timeoutMs: Int = 1200): Long? {
        val start = System.currentTimeMillis()
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                System.currentTimeMillis() - start
            }
        } catch (_: Exception) {
            null
        }
    }

    fun launchTailscale(context: Context) {
        val pkg = "com.tailscale.ipn"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            // Fallback to Google Play Store
            try {
                val storeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                storeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(storeIntent)
            } catch (_: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            }
        }
    }
}
