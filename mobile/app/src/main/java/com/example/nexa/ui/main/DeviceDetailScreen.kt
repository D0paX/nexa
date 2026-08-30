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
import com.example.nexa.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    mac: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Context", style = Typography.titleLarge, color = NexaTextPrimary) },
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
                Text(
                    text = "Identity",
                    style = Typography.titleMedium,
                    color = NexaTextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // TrustedDeviceIdentity
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(strong = true)
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Trusted Identity", style = Typography.labelLarge, color = NexaSecure)
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(color = NexaSecure.copy(alpha = 0.2f), shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)) {
                                Text(" VERIFIED ", style = Typography.labelMedium, color = NexaSecure)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Corp Laptop - Engineering", style = Typography.bodyLarge, color = NexaTextPrimary)
                        Text("Owner: jsmith@example.com", style = Typography.bodyMedium, color = NexaTextSecondary)
                    }
                }
            }

            // DeviceRecord
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass()
                        .padding(16.dp)
                ) {
                    Column {
                        Text("Network Record", style = Typography.labelLarge, color = NexaTextPrimary)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("MAC Address", style = Typography.bodyMedium, color = NexaTextSecondary)
                            Text(mac, style = MonospaceTextStyle, color = NexaTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current IP", style = Typography.bodyMedium, color = NexaTextSecondary)
                            Text("192.168.1.105", style = MonospaceTextStyle, color = NexaTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Scope", style = Typography.bodyMedium, color = NexaTextSecondary)
                            Text("VLAN_SECURE", style = MonospaceTextStyle, color = NexaInformation)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Enforcement & Actions", style = Typography.titleMedium, color = NexaTextSecondary)
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass()
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("State", style = Typography.bodyLarge, color = NexaTextPrimary)
                            Text("PERMITTED", style = Typography.labelLarge, color = NexaSecure)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Action Button
                        Button(
                            onClick = { onNavigate(ActionConfirmation("QUARANTINE_DEVICE", mac, "Quarantine Device")) },
                            colors = ButtonDefaults.buttonColors(containerColor = NexaDanger),
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                        ) {
                            Text("QUARANTINE", style = Typography.labelLarge, color = NexaBackground)
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
