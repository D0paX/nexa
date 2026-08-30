package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NEXA", style = Typography.displayLarge, color = NexaTextPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = NexaTextPrimary
                )
            )
        },
        containerColor = Color.Transparent,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = NexaTokens.SpacingMedium),
            verticalArrangement = Arrangement.spacedBy(NexaTokens.SpacingMedium)
        ) {
            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            }
            
            // Primary security state (HERO SURFACE)
            item {
                GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "System State", style = Typography.titleMedium, color = NexaTextSecondary)
                            StatusBadge(text = MockData.systemStatus, color = NexaSecure)
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Text(text = "Nftables backend is active. 3 devices quarantined.", style = Typography.bodyLarge, color = NexaTextPrimary)
                    }
                }
            }
            
            // Important metrics (Bento Row)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingMedium)
                ) {
                    MetricSurface(
                        title = "Active Devices",
                        value = MockData.activeDevices.toString(),
                        onClick = { onItemClick(DeviceDetail("00:1A:2B:3C:4D:5E")) },
                        modifier = Modifier.weight(1f)
                    )
                    MetricSurface(
                        title = "Active Alerts",
                        value = MockData.activeAlerts.toString(),
                        valueColor = NexaWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent alerts
            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
                Text(
                    text = "Recent Alerts",
                    style = Typography.titleLarge,
                    color = NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            }

            items(MockData.recentAlerts) { alert ->
                AlertItem(
                    alert = alert,
                    onClick = { onItemClick(AlertDetail(alert.id)) }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
            }
        }
    }
}

@Composable
fun AlertItem(alert: AlertItemData, onClick: () -> Unit) {
    val severityColor = when(alert.severity) {
        "CRITICAL" -> NexaCritical
        "WARNING" -> NexaWarning
        else -> NexaInformation
    }

    GlassSurface(
        variant = GlassVariant.Interactive,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = NexaTokens.SpacingSmall)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = severityColor,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(10.dp)
            ) {}
            Spacer(modifier = Modifier.width(NexaTokens.SpacingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = alert.description, style = Typography.bodyLarge, color = NexaTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                TechnicalValue(text = alert.targetMac, color = NexaTextSecondary)
            }
            Text(text = alert.timeAgo, style = Typography.labelMedium, color = NexaTextMuted)
        }
    }
}
