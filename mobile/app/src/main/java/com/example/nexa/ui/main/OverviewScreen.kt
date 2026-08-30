package com.example.nexa.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*

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
                    containerColor = NexaBackground,
                    titleContentColor = NexaTextPrimary
                )
            )
        },
        containerColor = NexaBackground,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Security Overview",
                    style = Typography.headlineMedium,
                    color = NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Primary security state
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(strong = true)
                        .padding(20.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "System State", style = Typography.titleMedium, color = NexaTextSecondary)
                            Box(
                                modifier = Modifier
                                    .liquidGlass()
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = MockData.systemStatus,
                                    color = NexaSecure,
                                    style = Typography.labelLarge
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Nftables backend is active. 3 devices quarantined.", style = Typography.bodyMedium, color = NexaTextPrimary)
                    }
                }
            }
            
            // Important metrics (Bento Row)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    MetricCard(
                        title = "Active Devices",
                        value = MockData.activeDevices.toString(),
                        modifier = Modifier.weight(1f).clickable { onItemClick(DeviceDetail("00:1A:2B:3C:4D:5E")) }
                    )
                    MetricCard(
                        title = "Active Alerts",
                        value = MockData.activeAlerts.toString(),
                        isWarning = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recent alerts
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Recent Alerts",
                    style = Typography.titleLarge,
                    color = NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(MockData.recentAlerts) { alert ->
                AlertItem(
                    alert = alert,
                    onClick = { onItemClick(AlertDetail(alert.id)) }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, isWarning: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .liquidGlass()
            .padding(16.dp)
    ) {
        Column {
            Text(text = title, style = Typography.labelMedium, color = NexaTextSecondary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = Typography.displayLarge,
                color = if (isWarning) NexaWarning else NexaTextPrimary
            )
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .liquidGlass()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = severityColor,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(12.dp)
            ) {}
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = alert.description, style = Typography.bodyLarge, color = NexaTextPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = alert.targetMac, style = MonospaceTextStyle.copy(color = NexaTextSecondary))
            }
            Text(text = alert.timeAgo, style = Typography.labelMedium, color = NexaTextMuted)
        }
    }
}
