package com.vpngate.tunnel.domain.ranking

import com.vpngate.tunnel.data.model.VpnServer
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class ServerRankingEngine(
    private val latencyWeight: Double = 35.0,
    private val throughputWeight: Double = 30.0,
    private val uptimeWeight: Double = 15.0,
    private val availabilityWeight: Double = 10.0,
    private val loadPenaltyWeight: Double = 10.0
) {

    /**
     * Ranks servers using a composite scoring function:
     * score = latency_score + throughput_score + uptime_score + availability_score - load_penalty
     *
     * Ensures low latency and high throughput are favored while penalizing congested servers.
     */
    fun rankServers(servers: List<VpnServer>): List<VpnServer> {
        if (servers.isEmpty()) return emptyList()

        return servers.map { server ->
            val score = calculateScore(server)
            server.copy(rankScore = score)
        }.sortedByDescending { it.rankScore }
    }

    fun calculateScore(server: VpnServer): Double {
        // 1. Latency Score (0 to 1): Lower ping is better.
        // Clamp ping between 5ms and 350ms.
        val pingClamped = server.pingMs.coerceIn(5L, 350L).toDouble()
        val latencyScore = (1.0 - ((pingClamped - 5.0) / 345.0)).coerceIn(0.0, 1.0)

        // 2. Throughput Score (0 to 1): Logarithmic scale between 1 Mbps and 500 Mbps
        // Logarithmic scaling prevents a 800Mbps server from overshadowing a rock-solid 150Mbps server.
        val speedMbps = max(1.0, server.speedMbps)
        val throughputScore = (ln(min(speedMbps, 500.0)) / ln(500.0)).coerceIn(0.0, 1.0)

        // 3. Uptime Score (0 to 1): Measured in hours, capped at 30 days (720 hours)
        val uptimeHours = (server.uptimeMs / (1000.0 * 60.0 * 60.0)).coerceAtLeast(0.0)
        val uptimeScore = (uptimeHours / 720.0).coerceIn(0.0, 1.0)

        // 4. Availability Score (0 to 1): Based on reported total traffic & session stability
        val trafficScore = if (server.totalTrafficBytes > 0) {
            (ln(max(1.0, server.totalTrafficBytes.toDouble() / 1_000_000_000.0)) / ln(10000.0)).coerceIn(0.0, 1.0)
        } else {
            0.5
        }

        // 5. Load Penalty (0 to 1): High concurrent VPN sessions cause latency spikes & packet drops
        val activeSessions = server.numVpnSessions.coerceAtLeast(0L).toDouble()
        val loadRatio = (activeSessions / 150.0).coerceIn(0.0, 1.0)

        // Composite calculation
        val total = (latencyScore * latencyWeight) +
                (throughputScore * throughputWeight) +
                (uptimeScore * uptimeWeight) +
                (trafficScore * availabilityWeight) -
                (loadRatio * loadPenaltyWeight)

        return max(0.0, total)
    }
}
