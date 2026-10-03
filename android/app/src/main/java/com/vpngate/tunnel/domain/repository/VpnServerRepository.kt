package com.vpngate.tunnel.domain.repository

import android.util.Log
import com.vpngate.tunnel.data.local.ServerDao
import com.vpngate.tunnel.data.local.ServerEntity
import com.vpngate.tunnel.data.model.ServerCountry
import com.vpngate.tunnel.data.model.VpnServer
import com.vpngate.tunnel.data.network.VpnGateApiClient
import com.vpngate.tunnel.domain.ranking.ServerRankingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class VpnServerRepository(
    private val serverDao: ServerDao,
    private val apiClient: VpnGateApiClient = VpnGateApiClient(),
    private val rankingEngine: ServerRankingEngine = ServerRankingEngine()
) {
    companion object {
        private const val TAG = "VpnServerRepository"
        // Cache freshness threshold: 24 hours (automatic weekly background sync by WorkManager)
        private const val CACHE_MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }

    fun observeAllServers(): Flow<List<VpnServer>> {
        return serverDao.getAllServersFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeServersByCountry(country: ServerCountry): Flow<List<VpnServer>> {
        return serverDao.getServersByCountryFlow(country.code).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getRankedCandidates(preferredCountry: ServerCountry? = null): List<VpnServer> =
        withContext(Dispatchers.IO) {
            val entities = if (preferredCountry != null) {
                serverDao.getServersByCountry(preferredCountry.code)
            } else {
                serverDao.getJapanAndUsServers()
            }

            if (entities.isNotEmpty()) {
                val servers = entities.map { it.toDomain() }
                return@withContext rankingEngine.rankServers(servers)
            }

            // If local cache is empty, fetch fresh from network
            refreshServers().getOrDefault(emptyList()).let { fresh ->
                if (preferredCountry != null) {
                    fresh.filter { it.countryShort.equals(preferredCountry.code, ignoreCase = true) }
                } else {
                    fresh
                }
            }
        }

    suspend fun refreshServers(): Result<List<VpnServer>> = withContext(Dispatchers.IO) {
        val result = apiClient.fetchJapanAndUsServers()
        if (result.isSuccess) {
            val rawServers = result.getOrNull() ?: emptyList()
            val ranked = rankingEngine.rankServers(rawServers)

            // Cache in Room database
            val entities = ranked.map { ServerEntity.fromDomain(it) }
            serverDao.replaceAllServers(entities)
            Log.d(TAG, "Cached ${entities.size} ranked servers into Room database")
            Result.success(ranked)
        } else {
            val cached = serverDao.getAllServers().map { it.toDomain() }
            if (cached.isNotEmpty()) {
                Log.i(TAG, "Network failed; falling back to ${cached.size} cached servers")
                Result.success(cached)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch VPN servers"))
            }
        }
    }

    suspend fun pruneOldServers() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        serverDao.pruneStaleServers(cutoff)
    }
}
