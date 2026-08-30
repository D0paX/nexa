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
import com.example.nexa.theme.*

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
                navigationIcon = {
                    Text(
                        "Cancel",
                        style = Typography.titleMedium,
                        color = NexaTextSecondary,
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
            
            // SIMULATION ONLY
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(strong = true)
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("SIMULATION ONLY", style = Typography.displayLarge.copy(color = NexaInformation))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("NO FIREWALL MUTATION WILL OCCUR", style = Typography.labelLarge, color = NexaTextSecondary)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Target Identity", style = Typography.titleMedium, color = NexaTextSecondary)
            }

            // Target Context
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass()
                        .padding(16.dp)
                ) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("MAC", style = Typography.bodyMedium, color = NexaTextSecondary)
                            Text(targetMac, style = MonospaceTextStyle, color = NexaTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("IP", style = Typography.bodyMedium, color = NexaTextSecondary)
                            Text("192.168.1.105", style = MonospaceTextStyle, color = NexaTextPrimary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Trust State", style = Typography.bodyMedium, color = NexaTextSecondary)
                            Text("VERIFIED", style = Typography.labelMedium, color = NexaSecure)
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Execution Consequence", style = Typography.titleMedium, color = NexaDanger)
            }

            item {
                Text(
                    text = "This action will isolate the target device from all network access except the designated remediation VLAN. Existing connections will be dropped.",
                    style = Typography.bodyLarge,
                    color = NexaTextPrimary
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { /* Execute */ },
                    colors = ButtonDefaults.buttonColors(containerColor = NexaDanger),
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text("CONFIRM $action", style = Typography.labelLarge, color = NexaBackground)
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedButton(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NexaTextPrimary),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NexaGlassBorder),
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp)
                ) {
                    Text("CANCEL", style = Typography.labelLarge)
                }
            }
        }
    }
}
