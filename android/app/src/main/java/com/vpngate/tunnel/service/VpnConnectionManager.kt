package com.vpngate.tunnel.service

import android.content.Context
import android.util.Log
import com.vpngate.tunnel.data.model.ConnectionState
import com.vpngate.tunnel.data.model.ServerCountry
import com.vpngate.tunnel.data.model.VpnServer
import com.vpngate.tunnel.domain.repository.VpnServerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class VpnConnectionManager(
    private val context: Context,
    private val repository: VpnServerRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + Job())
) {
    companion object {
        private const val TAG = "VpnConnectionManager"
        private const val MAX_FALLBACK_CANDIDATES = 5
        private const val CONNECTION_TIMEOUT_MS = 10_000L
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var connectJob: Job? = null

    init {
        // Observe internal service state and forward to UI
        scope.launch {
            GateTunnelVpnService.serviceConnectionState.collect { serviceState ->
                _connectionState.value = serviceState
            }
        }
    }

    /**
     * Connects to the fastest server in Japan or the US, automatically
     * cascading through fallback servers if a server fails or times out.
     */
    fun connectFastest(preferredCountry: ServerCountry? = null) {
        connectJob?.cancel()
        connectJob = scope.launch {
            try {
                Log.d(TAG, "Initiating fastest connection sequence (Country: ${preferredCountry?.displayName ?: "Fastest JP/US"})")
                val candidates = repository.getRankedCandidates(preferredCountry)

                if (candidates.isEmpty()) {
                    _connectionState.value = ConnectionState.ConnectionFailed(
                        "No available servers found. Please check internet connection."
                    )
                    return@launch
                }

                val topCandidates = candidates.take(MAX_FALLBACK_CANDIDATES)
                var connectedSuccessfully = false

                for ((index, server) in topCandidates.withIndex()) {
                    val candidateNum = index + 1
                    Log.i(TAG, "Trying candidate $candidateNum of ${topCandidates.size}: ${server.ip} (${server.displayLocation}, Ping: ${server.pingMs}ms, Speed: ${server.speedMbps.toInt()}Mbps)")

                    _connectionState.value = ConnectionState.Connecting(
                        candidateIndex = candidateNum,
                        totalCandidates = topCandidates.size,
                        server = server
                    )

                    // Start VPN service tunnel
                    GateTunnelVpnService.startVpn(context, server)

                    // Monitor connection success within timeout
                    val outcome = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
                        waitForConnectionSuccess()
                    }

                    if (outcome == true) {
                        connectedSuccessfully = true
                        Log.i(TAG, "Successfully connected to candidate $candidateNum: ${server.ip}")
                        break
                    } else {
                        Log.w(TAG, "Candidate $candidateNum (${server.ip}) timed out or failed. Falling back to next...")
                        GateTunnelVpnService.stopVpn(context)
                        delay(600) // Brief pause before fallback attempt
                    }
                }

                if (!connectedSuccessfully) {
                    _connectionState.value = ConnectionState.ConnectionFailed(
                        "All $MAX_FALLBACK_CANDIDATES best servers in JP/US were unreachable. Please retry.",
                        lastAttemptedServer = topCandidates.lastOrNull()
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in connection manager: ${e.message}", e)
                _connectionState.value = ConnectionState.ConnectionFailed(e.message ?: "Connection error")
            }
        }
    }

    fun connectSpecific(server: VpnServer) {
        connectJob?.cancel()
        connectJob = scope.launch {
            _connectionState.value = ConnectionState.Connecting(
                candidateIndex = 1,
                totalCandidates = 1,
                server = server
            )
            GateTunnelVpnService.startVpn(context, server)
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        GateTunnelVpnService.stopVpn(context)
        _connectionState.value = ConnectionState.Disconnected
    }

    private suspend fun waitForConnectionSuccess(): Boolean {
        while (true) {
            when (val state = GateTunnelVpnService.serviceConnectionState.value) {
                is ConnectionState.Connected -> return true
                is ConnectionState.ConnectionFailed -> return false
                else -> delay(250)
            }
        }
    }
}
