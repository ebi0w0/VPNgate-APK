package com.vpngate.tunnel.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpngate.tunnel.data.model.ServerCountry
import com.vpngate.tunnel.ui.theme.ColorBackground
import com.vpngate.tunnel.ui.theme.ColorBlack
import com.vpngate.tunnel.ui.theme.ColorBorder
import com.vpngate.tunnel.ui.theme.ColorSurface
import com.vpngate.tunnel.ui.theme.ColorSurfaceVariant
import com.vpngate.tunnel.ui.theme.ColorTextPrimary
import com.vpngate.tunnel.ui.theme.ColorTextSecondary
import com.vpngate.tunnel.ui.theme.ColorTextTertiary
import com.vpngate.tunnel.ui.theme.ColorWhite

@Composable
fun SettingsScreen(
    preferredCountry: ServerCountry?,
    autoConnectOnLaunch: Boolean,
    weeklySyncEnabled: Boolean,
    connectionTimeoutSec: Int,
    onBack: () -> Unit,
    onSelectCountry: (ServerCountry?) -> Unit,
    onToggleAutoConnect: (Boolean) -> Unit,
    onToggleWeeklySync: (Boolean) -> Unit,
    onChangeTimeout: (Int) -> Unit
) {
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
                text = "Settings",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = ColorTextPrimary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Preferred Countries Section
            SectionContainer(title = "PREFERRED REGION") {
                CountrySelectionOption(
                    title = "Fastest Server",
                    subtitle = "Automatically select lowest latency between Japan & US",
                    isSelected = preferredCountry == null,
                    onClick = { onSelectCountry(null) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorBorder))
                CountrySelectionOption(
                    title = "🇯🇵 Japan Only",
                    subtitle = "Prefer VPN Gate nodes located in Tokyo & Osaka",
                    isSelected = preferredCountry == ServerCountry.JAPAN,
                    onClick = { onSelectCountry(ServerCountry.JAPAN) }
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorBorder))
                CountrySelectionOption(
                    title = "🇺🇸 United States Only",
                    subtitle = "Prefer VPN Gate nodes located in US East & West",
                    isSelected = preferredCountry == ServerCountry.UNITED_STATES,
                    onClick = { onSelectCountry(ServerCountry.UNITED_STATES) }
                )
            }

            // Automation Section
            SectionContainer(title = "AUTOMATION") {
                SwitchOption(
                    title = "Auto-Connect on Launch",
                    subtitle = "Immediately establish tunnel upon opening app",
                    isChecked = autoConnectOnLaunch,
                    onCheckedChange = onToggleAutoConnect
                )
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorBorder))
                SwitchOption(
                    title = "Weekly Server Auto-Sync",
                    subtitle = "Background WorkManager updates server cache every 7 days",
                    isChecked = weeklySyncEnabled,
                    onCheckedChange = onToggleWeeklySync
                )
            }

            // Connection Timeout
            SectionContainer(title = "CONNECTION TIMEOUT") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Server Failover Timeout",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = ColorWhite
                        )
                        Text(
                            text = "${connectionTimeoutSec}s",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ColorWhite
                        )
                    }
                    Text(
                        text = "Time to wait before automatically attempting the next candidate",
                        fontSize = 12.sp,
                        color = ColorTextSecondary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )

                    Slider(
                        value = connectionTimeoutSec.toFloat(),
                        onValueChange = { onChangeTimeout(it.toInt()) },
                        valueRange = 5f..30f,
                        steps = 5,
                        colors = SliderDefaults.colors(
                            thumbColor = ColorWhite,
                            activeTrackColor = ColorWhite,
                            inactiveTrackColor = ColorSurfaceVariant
                        )
                    )
                }
            }

            // About Section
            SectionContainer(title = "ABOUT") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GateTunnel VPN",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorWhite
                    )
                    Text(
                        text = "Version 1.0.0 (Build 100)",
                        fontSize = 12.sp,
                        color = ColorTextTertiary,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    Text(
                        text = "Powered by VPN Gate Academic Experiment Project (University of Tsukuba, Japan). Compliant with Android VpnService standards and strict zero-leak DNS routing.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = ColorTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionContainer(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = ColorTextTertiary,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ColorSurface)
                .border(1.dp, ColorBorder, RoundedCornerShape(12.dp))
        ) {
            content()
        }
    }
}

@Composable
private fun CountrySelectionOption(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = ColorWhite
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = ColorTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isSelected) ColorWhite else androidx.compose.ui.graphics.Color.Transparent)
                .border(1.dp, if (isSelected) ColorWhite else ColorBorder, RoundedCornerShape(4.dp))
                .height(18.dp)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Text(
                    text = "ACTIVE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ColorBlack
                )
            }
        }
    }
}

@Composable
private fun SwitchOption(
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = ColorWhite
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = ColorTextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ColorWhite,
                checkedTrackColor = ColorBorder,
                uncheckedThumbColor = ColorTextSecondary,
                uncheckedTrackColor = ColorSurfaceVariant,
                checkedBorderColor = ColorWhite,
                uncheckedBorderColor = ColorBorder
            )
        )
    }
}
