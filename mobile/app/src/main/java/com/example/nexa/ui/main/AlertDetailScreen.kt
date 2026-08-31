package com.example.nexa.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.nexa.ActionConfirmation
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDetailScreen(
    alertId: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val alert = MockData.recentAlerts.find { it.id == alertId } ?: MockData.recentAlerts.first()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(alert.id, style = Typography.titleLarge, color = NexaTextPrimary) },
                navigationIcon = {
                    Text(
                        "←",
                        style = Typography.titleLarge,
                        color = NexaAction,
                        modifier = Modifier
                            .padding(NexaTokens.SpacingMedium)
                            .clickable(onClick = onBack)
                    )
                },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val severityColor = when(alert.severity) {
                        "CRITICAL" -> NexaCritical
                        "WARNING" -> NexaWarning
                        else -> NexaInformation
                    }
                    
                    StatusBadge(text = alert.severity, color = severityColor)
                    Text(alert.timeAgo, style = Typography.labelMedium, color = NexaTextMuted)
                }
            }

            item {
                GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                        Text(text = alert.description, style = Typography.headlineMedium, color = NexaTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                
                // Alert Lifecycle state vs Notification state
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingMedium)) {
                    MetricSurface(
                        title = "Alert State",
                        value = "NEW",
                        valueColor = NexaWarning,
                        modifier = Modifier.weight(1f)
                    )
                    MetricSurface(
                        title = "Delivery",
                        value = "FAILED",
                        valueColor = NexaDanger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                Text("Target Identity", style = Typography.titleMedium, color = NexaTextSecondary)
            }
            
            item {
                GlassSurface(
                    variant = GlassVariant.Interactive,
                    onClick = { onNavigate(DeviceDetail(alert.targetMac)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("MAC Address", style = Typography.labelMedium, color = NexaTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            TechnicalValue(alert.targetMac)
                        }
                        Text("Context →", style = Typography.labelMedium, color = NexaAction)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
                
                NexaButton(
                    text = "QUARANTINE TARGET",
                    onClick = { onNavigate(ActionConfirmation("QUARANTINE_DEVICE", alert.targetMac, "Quarantine Target")) },
                    isDestructive = true
                )
                
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                
                NexaOutlinedButton(
                    text = "ACKNOWLEDGE",
                    onClick = { /* Acknowledge */ }
                )
            }
        }
    }
}
