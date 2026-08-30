package com.example.nexa.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.nexa.ActionConfirmation
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*

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
                            .padding(16.dp)
                            .clickable(onClick = onBack)
                    )
                },
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
                    Box(
                        modifier = Modifier
                            .liquidGlass(strong = true)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(alert.severity, style = Typography.labelLarge, color = severityColor)
                    }
                    
                    Text(alert.timeAgo, style = Typography.labelMedium, color = NexaTextMuted)
                }
            }

            item {
                Text(text = alert.description, style = Typography.headlineMedium, color = NexaTextPrimary)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Alert Lifecycle state vs Notification state
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f).liquidGlass().padding(12.dp)) {
                        Column {
                            Text("Alert State", style = Typography.labelMedium, color = NexaTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("NEW", style = Typography.labelLarge, color = NexaWarning)
                        }
                    }
                    Box(modifier = Modifier.weight(1f).liquidGlass().padding(12.dp)) {
                        Column {
                            Text("Delivery", style = Typography.labelMedium, color = NexaTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("FAILED", style = Typography.labelLarge, color = NexaDanger)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Target Identity", style = Typography.titleMedium, color = NexaTextSecondary)
            }
            
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass()
                        .clickable { onNavigate(DeviceDetail(alert.targetMac)) }
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("MAC Address", style = Typography.labelMedium, color = NexaTextSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(alert.targetMac, style = MonospaceTextStyle, color = NexaTextPrimary)
                        }
                        Text("View Context →", style = Typography.labelMedium, color = NexaAction)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onNavigate(ActionConfirmation("QUARANTINE_DEVICE", alert.targetMac, "Quarantine Target")) },
                    colors = ButtonDefaults.buttonColors(containerColor = NexaDanger),
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("QUARANTINE TARGET", style = Typography.labelLarge, color = NexaBackground)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = { /* Acknowledge */ },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexaTextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexaGlassBorder),
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Text("ACKNOWLEDGE", style = Typography.labelLarge)
                }
            }
        }
    }
}
