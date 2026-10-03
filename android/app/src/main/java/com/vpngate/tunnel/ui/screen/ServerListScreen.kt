package com.vpngate.tunnel.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpngate.tunnel.data.model.ServerCountry
import com.vpngate.tunnel.data.model.VpnServer
import com.vpngate.tunnel.ui.theme.ColorBackground
import com.vpngate.tunnel.ui.theme.ColorBorder
import com.vpngate.tunnel.ui.theme.ColorSurface
import com.vpngate.tunnel.ui.theme.ColorSurfaceVariant
import com.vpngate.tunnel.ui.theme.ColorTextPrimary
import com.vpngate.tunnel.ui.theme.ColorTextSecondary
import com.vpngate.tunnel.ui.theme.ColorTextTertiary
import com.vpngate.tunnel.ui.theme.ColorWhite

@Composable
fun ServerListScreen(
    servers: List<VpnServer>,
    activeServerIp: String?,
    onBack: () -> Unit,
    onSelectServer: (VpnServer) -> Unit,
    onShowDetails: (VpnServer) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<ServerCountry?>(null) }

    val filteredList = remember(servers, selectedFilter) {
        if (selectedFilter == null) servers
        else servers.filter { it.countryShort.equals(selectedFilter?.code, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = ColorTextPrimary
                )
            }
            Text(
                text = "Servers",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${filteredList.size} available",
                fontSize = 12.sp,
                color = ColorTextSecondary
            )
        }

        // Segmented filter buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .background(ColorSurface, RoundedCornerShape(10.dp))
                .border(1.dp, ColorBorder, RoundedCornerShape(10.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilterTab(
                text = "All (JP & US)",
                isSelected = selectedFilter == null,
                onClick = { selectedFilter = null },
                modifier = Modifier.weight(1f)
            )
            FilterTab(
                text = "🇯🇵 Japan",
                isSelected = selectedFilter == ServerCountry.JAPAN,
                onClick = { selectedFilter = ServerCountry.JAPAN },
                modifier = Modifier.weight(1f)
            )
            FilterTab(
                text = "🇺🇸 United States",
                isSelected = selectedFilter == ServerCountry.UNITED_STATES,
                onClick = { selectedFilter = ServerCountry.UNITED_STATES },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Server Cards
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredList, key = { it.ip }) { server ->
                val isSelected = server.ip == activeServerIp
                ServerRowItem(
                    server = server,
                    isSelected = isSelected,
                    onConnect = { onSelectServer(server) },
                    onDetails = { onShowDetails(server) }
                )
            }
        }
    }
}

@Composable
private fun FilterTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) ColorSurfaceVariant else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) ColorWhite else ColorTextSecondary
        )
    }
}

@Composable
private fun ServerRowItem(
    server: VpnServer,
    isSelected: Boolean,
    onConnect: () -> Unit,
    onDetails: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ColorSurface)
            .border(
                width = 1.dp,
                color = if (isSelected) ColorWhite else ColorBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onConnect)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection radio/indicator
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .border(1.dp, if (isSelected) ColorWhite else ColorBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ColorWhite)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Country Flag & Server info
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (server.countryShort.equals("JP", true)) "🇯🇵 Japan" else "🇺🇸 United States",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = ColorWhite
                )
                Text(
                    text = " · ",
                    fontSize = 12.sp,
                    color = ColorTextTertiary
                )
                Text(
                    text = server.hostName,
                    fontSize = 12.sp,
                    color = ColorTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Clean unboxed metadata
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${server.pingMs} ms",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ColorWhite
                )
                Text(
                    text = " · ",
                    fontSize = 12.sp,
                    color = ColorTextTertiary
                )
                Text(
                    text = "${server.speedMbps.toInt()} Mbps",
                    fontSize = 12.sp,
                    color = ColorTextSecondary
                )
                Text(
                    text = " · ",
                    fontSize = 12.sp,
                    color = ColorTextTertiary
                )
                Text(
                    text = "${server.numVpnSessions} sessions",
                    fontSize = 12.sp,
                    color = ColorTextSecondary
                )
            }
        }

        // Details button
        IconButton(
            onClick = onDetails,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Details",
                tint = ColorTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
