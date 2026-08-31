package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.example.nexa.ActionConfirmation
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@Composable
fun AlertDetailScreen(
    alertId: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val alert = MockData.recentAlerts.find { it.id == alertId } ?: MockData.recentAlerts.first()
    val status = statusForSeverity(alert.severity)

    NexaScreen(
        modifier = modifier,
        title = alert.id,
        onBack = onBack,
        itemSpacing = NexaTokens.SpacingMedium
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(status = status, label = alert.severity)
                Text(alert.timeAgo, style = NexaType.Metadata, color = NexaTextMuted)
            }
        }

        item {
            GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                    Text(text = alert.description, style = NexaType.Headline, color = NexaTextOnDark)
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))

            // Alert lifecycle state vs notification delivery state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingMedium)
            ) {
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
            SectionHeader(text = "Target Identity", level = SectionLevel.Group)
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
                        Text("MAC Address", style = NexaType.Metadata, color = NexaTextSecondary)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingXSmall))
                        TechnicalValue(alert.targetMac)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Context", style = NexaType.Metadata, color = NexaAction)
                        NexaIcon(
                            icon = NexaIcons.Forward,
                            size = NexaTokens.IconMedium,
                            tint = NexaAction
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))

            NexaButton(
                text = "QUARANTINE TARGET",
                onClick = {
                    onNavigate(
                        ActionConfirmation(
                            action = "QUARANTINE_DEVICE",
                            targetMac = alert.targetMac,
                            actionLabel = "Quarantine Target",
                            // The alert carries no scope of its own; the snapshot
                            // resolves it rather than the UI guessing one.
                            scope = ""
                        )
                    )
                },
                variant = NexaButtonVariant.Destructive,
                icon = NexaIcons.Quarantine
            )

            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))

            NexaOutlinedButton(
                text = "ACKNOWLEDGE",
                onClick = { /* Acknowledge */ },
                icon = NexaIcons.Acknowledge
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
        }
    }
}
