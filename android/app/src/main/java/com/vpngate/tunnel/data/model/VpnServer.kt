package com.vpngate.tunnel.data.model

data class VpnServer(
    val hostName: String,
    val ip: String,
    val score: Long,
    val pingMs: Long,
    val speedBps: Long,
    val countryLong: String,
    val countryShort: String,
    val numVpnSessions: Long,
    val uptimeMs: Long,
    val totalUsers: Long,
    val totalTrafficBytes: Long,
    val logType: String,
    val operator: String,
    val message: String,
    val openVpnConfigBase64: String,
    // Parsed protocol details
    val port: Int = 1194,
    val proto: String = "udp",
    val cipher: String = "AES-128-CBC",
    // Computed ranking metrics
    val rankScore: Double = 0.0,
    val lastDiscoveredAt: Long = System.currentTimeMillis()
) {
    val speedMbps: Double
        get() = (speedBps.toDouble() / 1_000_000.0)

    val country: ServerCountry?
        get() = ServerCountry.fromCode(countryShort)

    val displayLocation: String
        get() = when (countryShort.uppercase()) {
            "JP" -> "Japan • Tokyo"
            "US" -> "United States • California"
            else -> "$countryLong ($countryShort)"
        }
}
