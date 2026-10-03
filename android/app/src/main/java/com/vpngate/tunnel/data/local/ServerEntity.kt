package com.vpngate.tunnel.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.vpngate.tunnel.data.model.VpnServer

@Entity(tableName = "vpn_servers")
data class ServerEntity(
    @PrimaryKey
    val ip: String,
    val hostName: String,
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
    val port: Int,
    val proto: String,
    val cipher: String,
    val rankScore: Double,
    val lastDiscoveredAt: Long
) {
    fun toDomain(): VpnServer = VpnServer(
        hostName = hostName,
        ip = ip,
        score = score,
        pingMs = pingMs,
        speedBps = speedBps,
        countryLong = countryLong,
        countryShort = countryShort,
        numVpnSessions = numVpnSessions,
        uptimeMs = uptimeMs,
        totalUsers = totalUsers,
        totalTrafficBytes = totalTrafficBytes,
        logType = logType,
        operator = operator,
        message = message,
        openVpnConfigBase64 = openVpnConfigBase64,
        port = port,
        proto = proto,
        cipher = cipher,
        rankScore = rankScore,
        lastDiscoveredAt = lastDiscoveredAt
    )

    companion object {
        fun fromDomain(server: VpnServer): ServerEntity = ServerEntity(
            ip = server.ip,
            hostName = server.hostName,
            score = server.score,
            pingMs = server.pingMs,
            speedBps = server.speedBps,
            countryLong = server.countryLong,
            countryShort = server.countryShort,
            numVpnSessions = server.numVpnSessions,
            uptimeMs = server.uptimeMs,
            totalUsers = server.totalUsers,
            totalTrafficBytes = server.totalTrafficBytes,
            logType = server.logType,
            operator = server.operator,
            message = server.message,
            openVpnConfigBase64 = server.openVpnConfigBase64,
            port = server.port,
            proto = server.proto,
            cipher = server.cipher,
            rankScore = server.rankScore,
            lastDiscoveredAt = server.lastDiscoveredAt
        )
    }
}
