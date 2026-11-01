package com.argon.hub.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

sealed class ConnectionState {
    data object Checking : ConnectionState()
    data class Connected(
        val host: String,
        val mode: String,
        val latencyMs: Long,
        val lastSeenMs: Long = System.currentTimeMillis()
    ) : ConnectionState()

    data class Degraded(
        val host: String,
        val mode: String,
        val lastSeenMs: Long,
        val elapsedSec: Long
    ) : ConnectionState()

    data class Offline(
        val lastSeenMs: Long? = null,
        val reason: String = "Pi Unreachable"
    ) : ConnectionState()
}

class NetworkManager {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Checking)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val lanHost = "192.168.1.10"
    private val tailscaleHost = "100.123.87.122"
    private val dnsHost = "argon-host"

    private var lastSuccessfulPingMs: Long? = null
    private var lastConnectedMode: String = "Tailscale Mesh"
    private var lastConnectedHost: String = tailscaleHost
    private var heartbeatJob: Job? = null

    fun getActiveHost(): String {
        return when (val state = _connectionState.value) {
            is ConnectionState.Connected -> state.host
            is ConnectionState.Degraded -> state.host
            else -> lastConnectedHost
        }
    }

    fun startHeartbeat(scope: CoroutineScope) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                checkConnectivity(isHeartbeat = true)
                delay(10_000) // 10-second active heartbeat
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    suspend fun checkConnectivity(isHeartbeat: Boolean = false) = withContext(Dispatchers.IO) {
        if (!isHeartbeat && _connectionState.value !is ConnectionState.Connected) {
            _connectionState.value = ConnectionState.Checking
        }

        // Concurrently check LAN (400ms timeout) and Tailscale (1500ms timeout)
        val lanJob = async { testHost(lanHost, 8089, 400) ?: testHost(lanHost, 80, 400) }
        val tsJob = async { testHost(tailscaleHost, 8089, 1500) ?: testHost(tailscaleHost, 80, 1500) }

        val lanPing = lanJob.await()
        if (lanPing != null) {
            val now = System.currentTimeMillis()
            lastSuccessfulPingMs = now
            lastConnectedHost = lanHost
            lastConnectedMode = "Home Wi-Fi"
            _connectionState.value = ConnectionState.Connected(lanHost, "Home Wi-Fi", lanPing, now)
            return@withContext
        }

        val tsPing = tsJob.await()
        if (tsPing != null) {
            val now = System.currentTimeMillis()
            lastSuccessfulPingMs = now
            lastConnectedHost = tailscaleHost
            lastConnectedMode = "Tailscale Mesh"
            _connectionState.value = ConnectionState.Connected(tailscaleHost, "Tailscale Mesh", tsPing, now)
            return@withContext
        }

        // 3. Test MagicDNS
        val dnsPing = testHost(dnsHost, 80, 600)
        if (dnsPing != null) {
            val now = System.currentTimeMillis()
            lastSuccessfulPingMs = now
            lastConnectedHost = dnsHost
            lastConnectedMode = "Tailscale DNS"
            _connectionState.value = ConnectionState.Connected(dnsHost, "Tailscale DNS", dnsPing, now)
            return@withContext
        }

        // Ping failed! Calculate elapsed downtime since last successful pulse
        val lastSeen = lastSuccessfulPingMs
        if (lastSeen != null) {
            val elapsedSec = (System.currentTimeMillis() - lastSeen) / 1000
            if (elapsedSec < 30) {
                _connectionState.value = ConnectionState.Degraded(lastConnectedHost, lastConnectedMode, lastSeen, elapsedSec)
                return@withContext
            }
        }

        // Neither reached for > 30s or never connected
        _connectionState.value = ConnectionState.Offline(lastSeen, "Pi Disconnected")
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
