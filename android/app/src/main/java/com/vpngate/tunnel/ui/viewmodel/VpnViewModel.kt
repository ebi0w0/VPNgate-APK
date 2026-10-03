package com.vpngate.tunnel.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vpngate.tunnel.data.local.VpnDatabase
import com.vpngate.tunnel.data.model.ConnectionState
import com.vpngate.tunnel.data.model.ServerCountry
import com.vpngate.tunnel.data.model.VpnServer
import com.vpngate.tunnel.domain.repository.VpnServerRepository
import com.vpngate.tunnel.service.VpnConnectionManager
import com.vpngate.tunnel.worker.WeeklyVpnSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("gate_tunnel_prefs", Context.MODE_PRIVATE)
    private val database = VpnDatabase.getInstance(application)
    private val repository = VpnServerRepository(database.serverDao())
    private val connectionManager = VpnConnectionManager(application, repository, viewModelScope)

    val connectionState: StateFlow<ConnectionState> = connectionManager.connectionState

    private val _preferredCountry = MutableStateFlow<ServerCountry?>(loadSavedCountry())
    val preferredCountry: StateFlow<ServerCountry?> = _preferredCountry.asStateFlow()

    private val _selectedServer = MutableStateFlow<VpnServer?>(null)
    val selectedServer: StateFlow<VpnServer?> = _selectedServer.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _detailServer = MutableStateFlow<VpnServer?>(null)
    val detailServer: StateFlow<VpnServer?> = _detailServer.asStateFlow()

    // Settings state
    private val _autoConnectOnLaunch = MutableStateFlow(prefs.getBoolean("auto_connect", false))
    val autoConnectOnLaunch: StateFlow<Boolean> = _autoConnectOnLaunch.asStateFlow()

    private val _weeklySyncEnabled = MutableStateFlow(prefs.getBoolean("weekly_sync", true))
    val weeklySyncEnabled: StateFlow<Boolean> = _weeklySyncEnabled.asStateFlow()

    private val _connectionTimeoutSec = MutableStateFlow(prefs.getInt("conn_timeout", 10))
    val connectionTimeoutSec: StateFlow<Int> = _connectionTimeoutSec.asStateFlow()

    // Filtered server list based on preferred country
    val allServers: StateFlow<List<VpnServer>> = repository.observeAllServers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredServers: StateFlow<List<VpnServer>> = combine(
        allServers,
        _preferredCountry
    ) { servers, country ->
        if (country == null) {
            servers
        } else {
            servers.filter { it.countryShort.equals(country.code, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initial load or refresh if cache empty
        viewModelScope.launch {
            if (allServers.value.isEmpty()) {
                refreshServers()
            }
        }
    }

    fun toggleConnection() {
        when (connectionState.value) {
            is ConnectionState.Connected,
            is ConnectionState.Connecting,
            is ConnectionState.Reconnecting -> {
                connectionManager.disconnect()
            }
            is ConnectionState.Disconnected,
            is ConnectionState.ConnectionFailed -> {
                val manualTarget = _selectedServer.value
                if (manualTarget != null) {
                    connectionManager.connectSpecific(manualTarget)
                } else {
                    connectionManager.connectFastest(_preferredCountry.value)
                }
            }
        }
    }

    fun connectToServer(server: VpnServer) {
        _selectedServer.value = server
        connectionManager.connectSpecific(server)
    }

    fun selectPreferredCountry(country: ServerCountry?) {
        _preferredCountry.value = country
        _selectedServer.value = null // Reset manual pick when country changes to pick fastest
        prefs.edit().putString("preferred_country", country?.code ?: "AUTO").apply()
    }

    fun refreshServers() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            val result = repository.refreshServers()
            _isRefreshing.value = false

            if (result.isFailure) {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Failed to refresh server list"
            }
        }
    }

    fun showServerDetails(server: VpnServer) {
        _detailServer.value = server
    }

    fun dismissServerDetails() {
        _detailServer.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    fun setAutoConnectOnLaunch(enabled: Boolean) {
        _autoConnectOnLaunch.value = enabled
        prefs.edit().putBoolean("auto_connect", enabled).apply()
    }

    fun setWeeklySyncEnabled(enabled: Boolean) {
        _weeklySyncEnabled.value = enabled
        prefs.edit().putBoolean("weekly_sync", enabled).apply()
        if (enabled) {
            WeeklyVpnSyncWorker.enqueueWeeklySync(getApplication())
        }
    }

    fun setConnectionTimeoutSec(seconds: Int) {
        _connectionTimeoutSec.value = seconds
        prefs.edit().putInt("conn_timeout", seconds).apply()
    }

    private fun loadSavedCountry(): ServerCountry? {
        val code = prefs.getString("preferred_country", "AUTO")
        return if (code == null || code == "AUTO") null else ServerCountry.fromCode(code)
    }
}
