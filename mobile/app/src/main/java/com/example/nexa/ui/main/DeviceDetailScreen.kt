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
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

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
        contentWindowInsets = WindowInsets(0.dp),
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
                Text(
                    text = "Identity",
                    style = Typography.titleMedium,
                    color = NexaTextSecondary,
                    modifier = Modifier.padding(bottom = NexaTokens.SpacingSmall)
                )
            }

            // TrustedDeviceIdentity (Stronger Surface)
            item {
                GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("TRUSTED IDENTITY", style = Typography.labelMedium, color = NexaSecureOnDark)
                            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                            StatusBadge(text = "VERIFIED", color = NexaSecureOnDark)
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Text("Corp Laptop - Engineering", style = Typography.headlineMedium, color = NexaTextOnDark)
                        Text("Owner: jsmith@example.com", style = Typography.bodyLarge, color = NexaTextOnDarkMuted)
                    }
                }
            }

            // DeviceRecord (Standard Surface)
            item {
                GlassSurface(variant = GlassVariant.Standard, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text("Network Record", style = Typography.labelLarge, color = NexaTextPrimary)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("MAC Address", style = Typography.bodyMedium, color = NexaTextSecondary)
                            TechnicalValue(mac)
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Current IP", style = Typography.bodyMedium, color = NexaTextSecondary)
                            TechnicalValue("192.168.1.105")
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Scope", style = Typography.bodyMedium, color = NexaTextSecondary)
                            TechnicalValue("VLAN_SECURE", color = NexaInformation)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                Text("Enforcement & Actions", style = Typography.titleMedium, color = NexaTextSecondary)
            }

            item {
                GlassSurface(variant = GlassVariant.Standard, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("State", style = Typography.bodyLarge, color = NexaTextPrimary)
                            StatusBadge(text = "PERMITTED", color = NexaSecure)
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
                        
                        NexaButton(
                            text = "QUARANTINE",
                            onClick = { onNavigate(ActionConfirmation("QUARANTINE_DEVICE", mac, "Quarantine Device")) },
                            isDestructive = true
                        )
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
            }
        }
    }
}
