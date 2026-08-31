package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfirmationScreen(
    action: String,
    targetMac: String,
    actionLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Confirm Action", style = Typography.titleLarge, color = NexaTextPrimary) },
                // Same control, announced for what it does here: abandoning the action.
                navigationIcon = {
                    NexaBackButton(onClick = onBack, contentDescription = "Cancel action")
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
            
            // SIMULATION ONLY (AUDIT_ONLY)
            item {
                GlassSurface(
                    variant = GlassVariant.Hero,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("SIMULATION ONLY", style = Typography.headlineMedium.copy(color = NexaInformationOnDark))
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Text("NO FIREWALL MUTATION WILL OCCUR", style = Typography.labelMedium, color = NexaTextOnDarkMuted)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                Text("Target Identity", style = Typography.titleMedium, color = NexaTextSecondary)
            }

            // Target Context
            item {
                GlassSurface(variant = GlassVariant.Strong, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TARGET MAC", style = Typography.labelMedium, color = NexaTextSecondary)
                            TechnicalValue(targetMac)
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("CURRENT IP", style = Typography.labelMedium, color = NexaTextSecondary)
                            TechnicalValue("192.168.1.105")
                        }
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("TRUST STATE", style = Typography.labelMedium, color = NexaTextSecondary)
                            StatusBadge(text = "VERIFIED", color = NexaSecure)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
            }

            item {
                DestructiveConfirmation(
                    actionName = action,
                    consequenceText = "This action will isolate the target device from all network access except the designated remediation VLAN. Existing connections will be dropped.",
                    onConfirm = { /* Execute */ },
                    onCancel = onBack
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
            }
        }
    }
}
