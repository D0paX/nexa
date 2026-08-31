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
    scope: String,
    identityId: String?,
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
                    TargetRow(label = "ACTION") { TechnicalValue(action, emphasized = true) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    TargetRow(label = "TARGET MAC") {
                        TechnicalValue(if (targetMac.isBlank()) "not bound" else targetMac)
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    // Scope travels with the target: the same MAC in another
                    // NetworkScope is not the same logical target.
                    TargetRow(label = "NETWORK SCOPE") {
                        TechnicalValue(if (scope.isBlank()) "unknown" else scope, emphasized = true)
                    }
                    if (identityId != null) {
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        TargetRow(label = "IDENTITY") { TechnicalValue(identityId) }
                    }
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
            val consequence = consequenceFor(action)
            DestructiveConfirmation(
                actionName = action,
                consequenceText = consequence.text,
                onConfirm = { /* Execute */ },
                onCancel = onBack,
                destructive = consequence.destructive,
                confirmIcon = consequence.icon
            )
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
        }
    }
}

/**
 * What an action actually does, per action.
 *
 * Each Phase 4 action code states its own consequence. A trust operation is
 * never described with enforcement's words: reverification does not isolate
 * anything, and release is not a quarantine. An unrecognized code says so
 * rather than borrowing the nearest description.
 */
private data class ActionConsequence(
    val text: String,
    val destructive: Boolean,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun consequenceFor(action: String): ActionConsequence = when (action) {
    "QUARANTINE_DEVICE" -> ActionConsequence(
        text = "This action will isolate the target device from all network access except the designated remediation VLAN. Existing connections will be dropped.",
        destructive = true,
        icon = NexaIcons.Quarantine
    )
    "RELEASE_QUARANTINE" -> ActionConsequence(
        text = "This action will remove the enforcement binding for the target and restore its normal network access. It does not change the target's trust standing.",
        destructive = true,
        icon = NexaIcons.Release
    )
    "REQUIRE_REVERIFICATION" -> ActionConsequence(
        text = "This action requires the target's cryptographic identity to be verified again. It does not quarantine the device, does not revoke trust, and makes no change to firewall state.",
        destructive = false,
        icon = NexaIcons.Reverification
    )
    else -> ActionConsequence(
        text = "NEXA cannot describe the consequence of this action. Do not confirm it unless you know what it does.",
        destructive = true,
        icon = NexaIcons.Unknown
    )
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
