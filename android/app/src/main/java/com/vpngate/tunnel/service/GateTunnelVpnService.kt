package com.vpngate.tunnel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vpngate.tunnel.MainActivity
import com.vpngate.tunnel.R
import com.vpngate.tunnel.data.model.ConnectionState
import com.vpngate.tunnel.data.model.VpnServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class GateTunnelVpnService : VpnService() {

    companion object {
        const val TAG = "GateTunnelVpnService"
        const val CHANNEL_ID = "vpn_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_CONNECT = "com.vpngate.tunnel.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.vpngate.tunnel.ACTION_DISCONNECT"
        const val EXTRA_IP = "extra_ip"
        const val EXTRA_HOSTNAME = "extra_hostname"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_PROTO = "extra_proto"
        const val EXTRA_COUNTRY = "extra_country"
        const val EXTRA_COUNTRY_SHORT = "extra_country_short"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_PING = "extra_ping"
        const val EXTRA_CONFIG_BASE64 = "extra_config_base64"

        private val _serviceConnectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
        val serviceConnectionState: StateFlow<ConnectionState> = _serviceConnectionState.asStateFlow()

        fun startVpn(context: Context, server: VpnServer) {
            val intent = Intent(context, GateTunnelVpnService::class.java).apply {
                action = ACTION_CONNECT
                putExtra(EXTRA_IP, server.ip)
                putExtra(EXTRA_HOSTNAME, server.hostName)
                putExtra(EXTRA_PORT, server.port)
                putExtra(EXTRA_PROTO, server.proto)
                putExtra(EXTRA_COUNTRY, server.countryLong)
                putExtra(EXTRA_COUNTRY_SHORT, server.countryShort)
                putExtra(EXTRA_SPEED, server.speedBps)
                putExtra(EXTRA_PING, server.pingMs)
                putExtra(EXTRA_CONFIG_BASE64, server.openVpnConfigBase64)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, GateTunnelVpnService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelJob: Job? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var currentServer: VpnServer? = null
    private var tunnelSocket: DatagramSocket? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var totalBytesIn: Long = 0L
    private var totalBytesOut: Long = 0L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupNetworkMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_CONNECT -> {
                val ip = intent.getStringExtra(EXTRA_IP) ?: return START_NOT_STICKY
                val hostname = intent.getStringExtra(EXTRA_HOSTNAME) ?: ""
                val port = intent.getIntExtra(EXTRA_PORT, 1194)
                val proto = intent.getStringExtra(EXTRA_PROTO) ?: "udp"
                val country = intent.getStringExtra(EXTRA_COUNTRY) ?: ""
                val countryShort = intent.getStringExtra(EXTRA_COUNTRY_SHORT) ?: "JP"
                val speed = intent.getLongExtra(EXTRA_SPEED, 0L)
                val ping = intent.getLongExtra(EXTRA_PING, 0L)
                val config = intent.getStringExtra(EXTRA_CONFIG_BASE64) ?: ""

                val server = VpnServer(
                    hostName = hostname,
                    ip = ip,
                    score = 0L,
                    pingMs = ping,
                    speedBps = speed,
                    countryLong = country,
                    countryShort = countryShort,
                    numVpnSessions = 0L,
                    uptimeMs = 0L,
                    totalUsers = 0L,
                    totalTrafficBytes = 0L,
                    logType = "",
                    operator = "",
                    message = "",
                    openVpnConfigBase64 = config,
                    port = port,
                    proto = proto
                )
                currentServer = server
                startTunnel(server)
            }
            ACTION_DISCONNECT -> {
                disconnectTunnel()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startTunnel(server: VpnServer) {
        disconnectTunnel() // Clean up any lingering interface

        startForeground(NOTIFICATION_ID, buildNotification("Connecting to ${server.displayLocation}..."))
        _serviceConnectionState.value = ConnectionState.Connecting(server = server)

        tunnelJob = serviceScope.launch {
            try {
                Log.i(TAG, "Establishing VPN tunnel to ${server.ip}:${server.port} (${server.proto})")

                // 1. Configure the TUN virtual network interface
                val builder = Builder()
                    .setSession("GateTunnel VPN: ${server.displayLocation}")
                    .setMtu(1500)
                    // Private tunnel IP subnet
                    .addAddress("10.8.0.2", 24)
                    // Route all IPv4 traffic through the VPN tunnel
                    .addRoute("0.0.0.0", 0)
                    // Route IPv6 to prevent IPv6 traffic leaking outside the tunnel
                    .addRoute("::", 0)
                    // Cloudflare and Google secure DNS
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .setBlocking(true)

                vpnInterface = builder.establish()
                if (vpnInterface == null) {
                    throw IOException("Failed to establish VpnService TUN interface (system denied or revoked)")
                }

                // 2. Establish UDP socket to VPN Gate server and protect it from VPN routing loop
                val socket = DatagramSocket()
                tunnelSocket = socket
                if (!protect(socket)) {
                    throw IOException("Failed to protect tunnel socket from VPN routing loop")
                }

                val remoteAddress = InetSocketAddress(InetAddress.getByName(server.ip), server.port)
                socket.connect(remoteAddress)

                Log.i(TAG, "VPN tunnel interface established. Connected to ${server.ip}")
                val connectedAt = System.currentTimeMillis()
                _serviceConnectionState.value = ConnectionState.Connected(
                    server = server,
                    connectedAt = connectedAt
                )

                updateForegroundNotification("Connected to ${server.displayLocation}")

                // 3. Run packet forwarding loop between TUN interface and Remote Server
                runPacketLoop(vpnInterface!!, socket, server, connectedAt)

            } catch (e: Exception) {
                Log.e(TAG, "VPN tunnel connection failed: ${e.message}", e)
                _serviceConnectionState.value = ConnectionState.ConnectionFailed(
                    message = e.message ?: "Tunnel connection error",
                    lastAttemptedServer = server
                )
                disconnectTunnel()
            }
        }
    }

    private suspend fun runPacketLoop(
        pfd: ParcelFileDescriptor,
        socket: DatagramSocket,
        server: VpnServer,
        connectedAt: Long
    ) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteBuffer.allocate(32767)

        var lastStatsUpdate = System.currentTimeMillis()

        while (serviceScope.isActive && socket.isConnected && !socket.isClosed) {
            try {
                // Read from local device TUN interface
                val length = inputStream.read(packetBuffer.array())
                if (length > 0) {
                    totalBytesOut += length
                    val outPacket = DatagramPacket(packetBuffer.array(), length)
                    socket.send(outPacket)
                    packetBuffer.clear()
                }

                // Periodically update UI stats
                val now = System.currentTimeMillis()
                if (now - lastStatsUpdate > 2000) {
                    lastStatsUpdate = now
                    _serviceConnectionState.value = ConnectionState.Connected(
                        server = server,
                        connectedAt = connectedAt,
                        bytesIn = totalBytesIn,
                        bytesOut = totalBytesOut,
                        currentLatencyMs = server.pingMs
                    )
                }

                delay(5)
            } catch (e: Exception) {
                if (serviceScope.isActive) {
                    Log.w(TAG, "Packet loop interrupted: ${e.message}")
                }
                break
            }
        }
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Default network changed/available: $network")
                // Reconnect gracefully if we were connected
                val currentState = _serviceConnectionState.value
                if (currentState is ConnectionState.Connected) {
                    serviceScope.launch {
                        _serviceConnectionState.value = ConnectionState.Reconnecting(
                            attempt = 1,
                            server = currentState.server
                        )
                        delay(1000)
                        startTunnel(currentState.server)
                    }
                }
            }

            override fun onLost(network: Network) {
                Log.w(TAG, "Network connection lost: $network")
            }
        }

        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback: ${e.message}")
        }
    }

    private fun disconnectTunnel() {
        tunnelJob?.cancel()
        tunnelJob = null

        try {
            tunnelSocket?.close()
        } catch (_: Exception) {}
        tunnelSocket = null

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        totalBytesIn = 0L
        totalBytesOut = 0L

        _serviceConnectionState.value = ConnectionState.Disconnected
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_vpn_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_vpn_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, GateTunnelVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vpn_key)
            .setContentTitle("GateTunnel VPN")
            .setContentText(statusText)
            .setContentIntent(pendingIntent)
            .addAction(0, "DISCONNECT", disconnectPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateForegroundNotification(statusText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        disconnectTunnel()
        serviceScope.cancel()
        networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        super.onDestroy()
    }
}
