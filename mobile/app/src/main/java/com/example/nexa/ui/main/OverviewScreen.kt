package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.Text
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@Composable
fun OverviewScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    NexaScreen(modifier = modifier) {
        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = NexaTextPrimary)) { append("NEXA") }
                    withStyle(SpanStyle(color = NexaAction)) { append(".") }
                },
                style = NexaType.Display
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium)) // modest gap
        }

        // Primary security state (HERO SURFACE)
        item {
            GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = NexaTokens.SpacingSmall)
                ) {
                    Text(text = "SYSTEM STATE", style = NexaType.Metadata, color = NexaTextOnDarkMuted)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    Text(
                        text = "ENFORCING",
                        style = NexaType.Display,
                        color = NexaSecureOnDark
                    )
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    Text(
                        text = "Nftables backend is active. 3 devices quarantined.",
                        style = NexaType.BodySecondary,
                        color = NexaTextOnDark
                    )
                }
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall)) // compact gap
        }

        // Important metrics (Bento Row)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(NexaTokens.SpacingMedium)
            ) {
                MetricSurface(
                    title = "ACTIVE DEVICES",
                    value = MockData.activeDevices.toString(),
                    glassVariant = GlassVariant.Interactive,
                    onClick = { onItemClick(DeviceDetail("00:1A:2B:3C:4D:5E")) },
                    modifier = Modifier.weight(1f)
                )
                MetricSurface(
                    title = "ACTIVE ALERTS",
                    value = MockData.activeAlerts.toString(),
                    valueColor = NexaWarning,
                    glassVariant = GlassVariant.Strong,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge)) // meaningful section gap
        }

        item {
            SectionHeader(text = "Recent Alerts")
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium)) // gap before activity list
        }

        items(MockData.recentAlerts, key = { it.id }) { alert ->
            AlertItem(
                alert = alert,
                onClick = { onItemClick(AlertDetail(alert.id)) }
            )
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.NavigationContentClearance))
        }
    }
}

@Composable
fun AlertItem(alert: AlertItemData, onClick: () -> Unit) {
    val status = statusForSeverity(alert.severity)
    val style = status.style
    val isCritical = status == NexaStatus.Critical

    NexaListRow(
        title = alert.description,
        onClick = onClick,
        // A critical alert is lifted onto denser glass rather than shouting in color.
        variant = if (isCritical) GlassVariant.Strong else GlassVariant.Standard,
        leadingIcon = style.icon,
        leadingTint = style.onLight,
        leadingContentDescription = style.label,
        titleStyle = if (isCritical) NexaType.Title else NexaType.Body,
        titleColor = if (isCritical) NexaTextPrimary else NexaTextSecondary,
        technical = alert.targetMac,
        timestamp = alert.timeAgo
    )
}
