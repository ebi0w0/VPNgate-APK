package com.vpngate.tunnel.data.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data class Connecting(
        val candidateIndex: Int = 1,
        val totalCandidates: Int = 5,
        val server: VpnServer
    ) : ConnectionState

    data class Connected(
        val server: VpnServer,
        val connectedAt: Long = System.currentTimeMillis(),
        val bytesIn: Long = 0L,
        val bytesOut: Long = 0L,
        val currentLatencyMs: Long = 0L
    ) : ConnectionState

    data class Reconnecting(
        val attempt: Int = 1,
        val maxAttempts: Int = 3,
        val server: VpnServer
    ) : ConnectionState

    data class ConnectionFailed(
        val message: String,
        val lastAttemptedServer: VpnServer? = null
    ) : ConnectionState
}
