package com.vpngate.tunnel

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vpngate.tunnel.data.model.ConnectionState
import com.vpngate.tunnel.ui.screen.MainScreen
import com.vpngate.tunnel.ui.screen.ServerDetailBottomSheet
import com.vpngate.tunnel.ui.screen.ServerListScreen
import com.vpngate.tunnel.ui.screen.SettingsScreen
import com.vpngate.tunnel.ui.theme.ColorBackground
import com.vpngate.tunnel.ui.theme.GateTunnelTheme
import com.vpngate.tunnel.ui.viewmodel.VpnViewModel

enum class CurrentScreen {
    MAIN,
    SERVER_LIST,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: VpnViewModel by viewModels()

    // Android VpnService permission launcher
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Permission granted by user, proceed to connect
            viewModel.toggleConnection()
        } else {
            Toast.makeText(this, "VPN permission required to create secure tunnel", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            GateTunnelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ColorBackground
                ) {
                    AppNavigation()
                }
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        var currentScreen by remember { mutableStateOf(CurrentScreen.MAIN) }

        val connectionState by viewModel.connectionState.collectAsState()
        val preferredCountry by viewModel.preferredCountry.collectAsState()
        val filteredServers by viewModel.filteredServers.collectAsState()
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        val errorMessage by viewModel.errorMessage.collectAsState()
        val detailServer by viewModel.detailServer.collectAsState()

        val autoConnect by viewModel.autoConnectOnLaunch.collectAsState()
        val weeklySync by viewModel.weeklySyncEnabled.collectAsState()
        val timeoutSec by viewModel.connectionTimeoutSec.collectAsState()

        // Handle error toast
        LaunchedEffect(errorMessage) {
            errorMessage?.let { msg ->
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearErrorMessage()
            }
        }

        // Auto-connect on launch if preference is enabled
        LaunchedEffect(Unit) {
            if (autoConnect && connectionState is ConnectionState.Disconnected) {
                checkPermissionAndConnect()
            }
        }

        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                CurrentScreen.MAIN -> {
                    MainScreen(
                        connectionState = connectionState,
                        preferredCountry = preferredCountry,
                        topServer = filteredServers.firstOrNull(),
                        isRefreshing = isRefreshing,
                        onToggleConnect = { checkPermissionAndConnect() },
                        onOpenServerList = { currentScreen = CurrentScreen.SERVER_LIST },
                        onOpenSettings = { currentScreen = CurrentScreen.SETTINGS },
                        onRefreshServers = { viewModel.refreshServers() },
                        onSelectCountry = { viewModel.selectPreferredCountry(it) },
                        onShowDetails = { viewModel.showServerDetails(it) }
                    )
                }

                CurrentScreen.SERVER_LIST -> {
                    val activeIp = when (val state = connectionState) {
                        is ConnectionState.Connected -> state.server.ip
                        is ConnectionState.Connecting -> state.server.ip
                        else -> null
                    }
                    ServerListScreen(
                        servers = filteredServers,
                        activeServerIp = activeIp,
                        onBack = { currentScreen = CurrentScreen.MAIN },
                        onSelectServer = { server ->
                            viewModel.connectToServer(server)
                            currentScreen = CurrentScreen.MAIN
                        },
                        onShowDetails = { viewModel.showServerDetails(it) }
                    )
                }

                CurrentScreen.SETTINGS -> {
                    SettingsScreen(
                        preferredCountry = preferredCountry,
                        autoConnectOnLaunch = autoConnect,
                        weeklySyncEnabled = weeklySync,
                        connectionTimeoutSec = timeoutSec,
                        onBack = { currentScreen = CurrentScreen.MAIN },
                        onSelectCountry = { viewModel.selectPreferredCountry(it) },
                        onToggleAutoConnect = { viewModel.setAutoConnectOnLaunch(it) },
                        onToggleWeeklySync = { viewModel.setWeeklySyncEnabled(it) },
                        onChangeTimeout = { viewModel.setConnectionTimeoutSec(it) }
                    )
                }
            }
        }

        // Server Detail Modal Bottom Sheet
        detailServer?.let { server ->
            ServerDetailBottomSheet(
                server = server,
                onDismiss = { viewModel.dismissServerDetails() },
                onConnect = {
                    viewModel.connectToServer(it)
                    if (currentScreen != CurrentScreen.MAIN) {
                        currentScreen = CurrentScreen.MAIN
                    }
                }
            )
        }
    }

    private fun checkPermissionAndConnect() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            // Must ask user for Android system VPN permission
            vpnPermissionLauncher.launch(prepareIntent)
        } else {
            // Already prepared/granted
            viewModel.toggleConnection()
        }
    }
}
