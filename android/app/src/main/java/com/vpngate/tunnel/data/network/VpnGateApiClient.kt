package com.vpngate.tunnel.data.network

import android.util.Log
import com.vpngate.tunnel.data.model.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class VpnGateApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    companion object {
        private const val TAG = "VpnGateApiClient"
        private val API_ENDPOINTS = listOf(
            "http://www.vpngate.net/api/iphone/",
            "https://www.vpngate.net/api/iphone/"
        )
    }

    suspend fun fetchJapanAndUsServers(): Result<List<VpnServer>> = withContext(Dispatchers.IO) {
        var lastException: Exception? = null

        for (endpoint in API_ENDPOINTS) {
            try {
                Log.d(TAG, "Fetching VPN Gate server list from $endpoint")
                val request = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "GateTunnel-Android/1.0")
                    .header("Accept-Encoding", "gzip, deflate")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("VPN Gate returned HTTP ${response.code}")
                    }

                    val bodyString = response.body?.string()
                        ?: throw IOException("Empty response body from VPN Gate")

                    val parsedServers = VpnGateCsvParser.parseCsv(bodyString)
                    if (parsedServers.isNotEmpty()) {
                        Log.i(TAG, "Successfully parsed ${parsedServers.size} JP/US servers from $endpoint")
                        return@withContext Result.success(parsedServers)
                    } else {
                        Log.w(TAG, "No valid JP/US servers found in response from $endpoint")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch from $endpoint: ${e.message}")
                lastException = e
            }
        }

        Result.failure(lastException ?: IOException("Failed to retrieve VPN Gate servers"))
    }
}
