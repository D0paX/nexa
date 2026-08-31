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

        // Target snapshot.
        //
        // Only the identity actually handed to this screen is shown. Address
        // and trust standing are deliberately NOT asserted here: they are
        // resolved by the Phase 4 TargetSnapshot at execution time, and
        // displaying a remembered value would risk stating something about
        // the target that is no longer true — exactly the stale-IP class of
        // error the enforcement layer exists to prevent.
        item {
            GlassSurface(variant = GlassVariant.Strong, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                    TargetRow(label = "TARGET MAC") { TechnicalValue(targetMac) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    TargetRow(label = "ACTION") { TechnicalValue(action, emphasized = true) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    Text(
                        text = "Current address and trust standing are re-resolved from the authoritative target snapshot when this action executes.",
                        style = NexaType.Metadata,
                        color = NexaTextMuted
                    )
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
