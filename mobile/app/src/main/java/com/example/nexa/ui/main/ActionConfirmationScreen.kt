package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@Composable
fun ActionConfirmationScreen(
    action: String,
    targetMac: String,
    actionLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    NexaScreen(
        modifier = modifier,
        title = "Confirm Action",
        onBack = onBack,
        backContentDescription = "Cancel action",
        itemSpacing = NexaTokens.SpacingMedium
    ) {
        // Execution mode must be visible before the operator commits.
        item {
            SimulationBanner()
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            SectionHeader(text = "Target Identity", level = SectionLevel.Group)
        }

        // Target snapshot
        item {
            GlassSurface(variant = GlassVariant.Strong, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                    TargetRow(label = "TARGET MAC") { TechnicalValue(targetMac) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    TargetRow(label = "CURRENT IP") { TechnicalValue("192.168.1.105") }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    TargetRow(label = "TRUST STATE") { StatusBadge(status = NexaStatus.Verified) }
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

/** Field label on the left, its snapshot value on the right. */
@Composable
private fun TargetRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NexaType.Metadata, color = NexaTextSecondary)
        value()
    }
}
