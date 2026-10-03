package com.vpngate.tunnel.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {
    @Query("SELECT * FROM vpn_servers ORDER BY rankScore DESC")
    fun getAllServersFlow(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM vpn_servers ORDER BY rankScore DESC")
    suspend fun getAllServers(): List<ServerEntity>

    @Query("SELECT * FROM vpn_servers WHERE countryShort = :countryCode ORDER BY rankScore DESC")
    fun getServersByCountryFlow(countryCode: String): Flow<List<ServerEntity>>

    @Query("SELECT * FROM vpn_servers WHERE countryShort = :countryCode ORDER BY rankScore DESC")
    suspend fun getServersByCountry(countryCode: String): List<ServerEntity>

    @Query("SELECT * FROM vpn_servers WHERE countryShort IN ('JP', 'US') ORDER BY rankScore DESC")
    suspend fun getJapanAndUsServers(): List<ServerEntity>

    @Query("SELECT * FROM vpn_servers WHERE ip = :ip LIMIT 1")
    suspend fun getServerByIp(ip: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServers(servers: List<ServerEntity>)

    @Query("DELETE FROM vpn_servers")
    suspend fun clearAll()

    @Query("DELETE FROM vpn_servers WHERE lastDiscoveredAt < :thresholdTimestamp")
    suspend fun pruneStaleServers(thresholdTimestamp: Long)

    @Transaction
    suspend fun replaceAllServers(servers: List<ServerEntity>) {
        clearAll()
        insertServers(servers)
    }
}
