package com.vpngate.tunnel.ui.screen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpngate.tunnel.data.model.ConnectionState
import com.vpngate.tunnel.data.model.ServerCountry
import com.vpngate.tunnel.data.model.VpnServer
import com.vpngate.tunnel.ui.theme.ColorActiveIndicator
import com.vpngate.tunnel.ui.theme.ColorBackground
import com.vpngate.tunnel.ui.theme.ColorBlack
import com.vpngate.tunnel.ui.theme.ColorBorder
import com.vpngate.tunnel.ui.theme.ColorConnectingGrey
import com.vpngate.tunnel.ui.theme.ColorDisconnectedGrey
import com.vpngate.tunnel.ui.theme.ColorSurface
import com.vpngate.tunnel.ui.theme.ColorSurfaceVariant
import com.vpngate.tunnel.ui.theme.ColorTextPrimary
import com.vpngate.tunnel.ui.theme.ColorTextSecondary
import com.vpngate.tunnel.ui.theme.ColorTextTertiary
import com.vpngate.tunnel.ui.theme.ColorWhite

@Composable
fun MainScreen(
    connectionState: ConnectionState,
    preferredCountry: ServerCountry?,
    topServer: VpnServer?,
    isRefreshing: Boolean,
    onToggleConnect: () -> Unit,
    onOpenServerList: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefreshServers: () -> Unit,
    onSelectCountry: (ServerCountry?) -> Unit,
    onShowDetails: (VpnServer) -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Reconnecting

    val activeServer = when (connectionState) {
        is ConnectionState.Connected -> connectionState.server
        is ConnectionState.Connecting -> connectionState.server
        is ConnectionState.Reconnecting -> connectionState.server
        else -> topServer
    }

    val statusLabel = when (connectionState) {
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Reconnecting -> "Reconnecting…"
        is ConnectionState.ConnectionFailed -> "Connection failed"
    }

    // Subtle breathing animation during connecting state
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isConnecting) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "VPN",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorTextPrimary,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshServers,
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = ColorWhite,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh servers",
                                tint = ColorTextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onOpenServerList) {
                        Icon(
                            imageVector = Icons.Outlined.List,
                            contentDescription = "Server list",
                            tint = ColorTextSecondary
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = ColorTextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Country Selector Segment (Japan 🇯🇵 / USA 🇺🇸 / Auto)
            CountrySegmentedControl(
                selectedCountry = preferredCountry,
                onSelectCountry = onSelectCountry,
                enabled = !isConnected && !isConnecting
            )

            Spacer(modifier = Modifier.weight(0.7f))

            // Status Indicator & State Text
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isConnected -> ColorWhite
                                isConnecting -> ColorConnectingGrey
                                else -> ColorDisconnectedGrey
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusLabel,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isConnected -> ColorWhite
                        isConnecting -> ColorConnectingGrey
                        else -> ColorTextSecondary
                    }
                )
            }

            // Candidate progress when cycling fallback servers
            if (connectionState is ConnectionState.Connecting) {
                Text(
                    text = "Attempting server ${connectionState.candidateIndex} of ${connectionState.totalCandidates}",
                    fontSize = 12.sp,
                    color = ColorTextTertiary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            // Large Central CONNECT Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
            ) {
                // Outer subtle border ring
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .border(
                            width = 1.dp,
                            color = if (isConnected) ColorWhite.copy(alpha = 0.25f) else ColorBorder,
                            shape = CircleShape
                        )
                )

                // Main actionable circular button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(164.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) ColorWhite else ColorSurface
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (isConnected) ColorWhite else ColorBorder,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleConnect
                        )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isConnected) "DISCONNECT" else if (isConnecting) "CANCEL" else "CONNECT",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) ColorBlack else ColorWhite,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.9f))

            // Selected Server Card
            if (activeServer != null) {
                ServerInfoCard(
                    server = activeServer,
                    isConnected = isConnected,
                    onClick = { onShowDetails(activeServer) }
                )
            } else {
                Text(
                    text = "Discovering fastest server…",
                    fontSize = 14.sp,
                    color = ColorTextTertiary
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CountrySegmentedControl(
    selectedCountry: ServerCountry?,
    onSelectCountry: (ServerCountry?) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SegmentButton(
            label = "Fastest",
            subLabel = "JP/US",
            isSelected = selectedCountry == null,
            onClick = { if (enabled) onSelectCountry(null) },
            modifier = Modifier.weight(1f)
        )
        SegmentButton(
            label = "Japan",
            subLabel = "🇯🇵",
            isSelected = selectedCountry == ServerCountry.JAPAN,
            onClick = { if (enabled) onSelectCountry(ServerCountry.JAPAN) },
            modifier = Modifier.weight(1f)
        )
        SegmentButton(
            label = "United States",
            subLabel = "🇺🇸",
            isSelected = selectedCountry == ServerCountry.UNITED_STATES,
            onClick = { if (enabled) onSelectCountry(ServerCountry.UNITED_STATES) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentButton(
    label: String,
    subLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ColorSurfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = subLabel,
                fontSize = 13.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) ColorWhite else ColorTextSecondary
            )
        }
    }
}

@Composable
private fun ServerInfoCard(
    server: VpnServer,
    isConnected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ColorSurface)
            .border(1.dp, ColorBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Location title
        Text(
            text = server.displayLocation,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ColorWhite,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Clean unboxed metadata with typographic separators
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = server.hostName,
                fontSize = 12.sp,
                color = ColorTextSecondary
            )
            Text(
                text = " · ",
                fontSize = 12.sp,
                color = ColorTextTertiary
            )
            Text(
                text = "${server.proto.uppercase()} :${server.port}",
                fontSize = 12.sp,
                color = ColorTextSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Performance metrics: Ping & Throughput
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${server.pingMs} ms",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) ColorWhite else ColorActiveIndicator
                )
                Text(
                    text = "Latency",
                    fontSize = 11.sp,
                    color = ColorTextTertiary
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(32.dp)
                    .background(ColorBorder)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${server.speedMbps.toInt()} Mbps",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) ColorWhite else ColorActiveIndicator
                )
                Text(
                    text = "Throughput",
                    fontSize = 11.sp,
                    color = ColorTextTertiary
                )
            }
        }
    }
}
