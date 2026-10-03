package com.vpngate.tunnel.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpngate.tunnel.data.model.VpnServer
import com.vpngate.tunnel.ui.theme.ColorBackground
import com.vpngate.tunnel.ui.theme.ColorBlack
import com.vpngate.tunnel.ui.theme.ColorBorder
import com.vpngate.tunnel.ui.theme.ColorSurface
import com.vpngate.tunnel.ui.theme.ColorTextPrimary
import com.vpngate.tunnel.ui.theme.ColorTextSecondary
import com.vpngate.tunnel.ui.theme.ColorTextTertiary
import com.vpngate.tunnel.ui.theme.ColorWhite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerDetailBottomSheet(
    server: VpnServer,
    onDismiss: () -> Unit,
    onConnect: (VpnServer) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ColorBackground,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(ColorBorder)
                    .height(4.dp)
                    .fillMaxWidth(0.12f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Server Details",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextPrimary
            )
            Text(
                text = server.displayLocation,
                fontSize = 13.sp,
                color = ColorTextSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 16.dp)
            )

            // Technical details table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ColorSurface)
                    .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow(label = "Public IP", value = server.ip)
                DetailRow(label = "DDNS Hostname", value = "${server.hostName}.opengw.net")
                DetailRow(label = "Protocol", value = "${server.proto.uppercase()} :${server.port}")
                DetailRow(label = "Encryption Cipher", value = server.cipher)
                DetailRow(label = "Latency", value = "${server.pingMs} ms")
                DetailRow(label = "Throughput", value = "${server.speedMbps.toInt()} Mbps")
                DetailRow(label = "Active Sessions", value = "${server.numVpnSessions} users")
                DetailRow(
                    label = "Uptime",
                    value = "${(server.uptimeMs / (1000 * 60 * 60 * 24)).toInt()} days"
                )
                DetailRow(
                    label = "Rank Score",
                    value = String.format("%.1f / 100", server.rankScore)
                )
            }

            if (server.operator.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(ColorSurface)
                        .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Operator Note",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ColorTextTertiary
                    )
                    Text(
                        text = server.operator,
                        fontSize = 12.sp,
                        color = ColorTextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ColorWhite),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(ColorBorder))
                ) {
                    Text("Close")
                }

                Button(
                    onClick = {
                        onConnect(server)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ColorWhite,
                        contentColor = ColorBlack
                    )
                ) {
                    Text("Connect to Server", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = ColorTextSecondary
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = ColorWhite
        )
    }
}
