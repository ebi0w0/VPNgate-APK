package com.vpngate.tunnel.data.network

import android.util.Base64
import android.util.Log
import com.vpngate.tunnel.data.model.VpnServer
import java.util.regex.Pattern

object VpnGateCsvParser {
    private const val TAG = "VpnGateCsvParser"
    private val IP_PATTERN = Pattern.compile("^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.([01]?\\d\\d?|2[0-4]\\d|25[0-5])$")
    private val REMOTE_REGEX = Pattern.compile("(?i)^remote\\s+([\\w.-]+)\\s+(\\d+)(?:\\s+(\\w+))?", Pattern.MULTILINE)
    private val PROTO_REGEX = Pattern.compile("(?i)^proto\\s+(\\w+)", Pattern.MULTILINE)
    private val CIPHER_REGEX = Pattern.compile("(?i)^cipher\\s+([\\w-]+)", Pattern.MULTILINE)

    /**
     * Parses the raw CSV string returned from VPN Gate API.
     * Filters exclusively for servers in Japan ("JP") and United States ("US").
     */
    fun parseCsv(rawCsv: String): List<VpnServer> {
        val servers = mutableListOf<VpnServer>()
        val lines = rawCsv.lineSequence()

        var headerFound = false
        var headers: List<String> = emptyList()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed == "*vpn_servers") continue

            if (trimmed.startsWith("*") && !trimmed.startsWith("*vpn_servers")) {
                // End marker or comment
                continue
            }

            if (trimmed.startsWith("#")) {
                // Header row
                val headerRow = trimmed.removePrefix("#")
                headers = splitCsvLine(headerRow)
                headerFound = true
                continue
            }

            if (!headerFound) continue

            try {
                val values = splitCsvLine(trimmed)
                if (values.size < 15) {
                    continue
                }

                val hostName = values.getOrNull(0)?.trim() ?: continue
                val ip = values.getOrNull(1)?.trim() ?: continue

                // Validate IP address format
                if (!IP_PATTERN.matcher(ip).matches()) {
                    continue
                }

                val score = values.getOrNull(2)?.toLongOrNull() ?: 0L
                val pingMs = values.getOrNull(3)?.toLongOrNull() ?: 999L
                val speedBps = values.getOrNull(4)?.toLongOrNull() ?: 0L
                val countryLong = values.getOrNull(5)?.trim() ?: ""
                val countryShort = values.getOrNull(6)?.trim()?.uppercase() ?: ""

                // Filter strictly to Japan and United States
                if (countryShort != "JP" && countryShort != "US") {
                    continue
                }

                // Reject servers reporting 0 speed or invalid ping
                if (speedBps <= 0L || pingMs <= 0L) {
                    continue
                }

                val numVpnSessions = values.getOrNull(7)?.toLongOrNull() ?: 0L
                val uptimeMs = values.getOrNull(8)?.toLongOrNull() ?: 0L
                val totalUsers = values.getOrNull(9)?.toLongOrNull() ?: 0L
                val totalTraffic = values.getOrNull(10)?.toLongOrNull() ?: 0L
                val logType = values.getOrNull(11)?.trim() ?: ""
                val operator = values.getOrNull(12)?.trim() ?: ""
                val message = values.getOrNull(13)?.trim() ?: ""
                val openVpnConfigBase64 = values.getOrNull(14)?.trim() ?: ""

                // Validate base64 presence
                if (openVpnConfigBase64.length < 50) {
                    continue
                }

                // Extract protocol & port from config if possible
                val (port, proto, cipher) = inspectOpenVpnConfig(openVpnConfigBase64)

                val server = VpnServer(
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
                    totalTrafficBytes = totalTraffic,
                    logType = logType,
                    operator = operator,
                    message = message,
                    openVpnConfigBase64 = openVpnConfigBase64,
                    port = port,
                    proto = proto,
                    cipher = cipher
                )
                servers.add(server)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed CSV line: ${e.message}")
            }
        }

        return servers
    }

    private fun inspectOpenVpnConfig(base64Config: String): Triple<Int, String, String> {
        var port = 1194
        var proto = "udp"
        var cipher = "AES-128-CBC"

        try {
            val decodedBytes = Base64.decode(base64Config, Base64.DEFAULT)
            val decodedText = String(decodedBytes, Charsets.UTF_8)

            val remoteMatcher = REMOTE_REGEX.matcher(decodedText)
            if (remoteMatcher.find()) {
                val parsedPort = remoteMatcher.group(2)?.toIntOrNull()
                if (parsedPort != null && parsedPort in 1..65535) {
                    port = parsedPort
                }
                val parsedProto = remoteMatcher.group(3)
                if (!parsedProto.isNullOrBlank()) {
                    proto = parsedProto.lowercase()
                }
            }

            val protoMatcher = PROTO_REGEX.matcher(decodedText)
            if (protoMatcher.find()) {
                val p = protoMatcher.group(1)
                if (!p.isNullOrBlank()) {
                    proto = p.lowercase()
                }
            }

            val cipherMatcher = CIPHER_REGEX.matcher(decodedText)
            if (cipherMatcher.find()) {
                val c = cipherMatcher.group(1)
                if (!c.isNullOrBlank()) {
                    cipher = c
                }
            }
        } catch (_: Exception) {
            // Default to 1194 UDP
        }

        return Triple(port, proto, cipher)
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()

        for (c in line) {
            when {
                c == '\"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }
}
