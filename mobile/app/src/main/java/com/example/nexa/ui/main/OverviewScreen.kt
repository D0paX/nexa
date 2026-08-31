package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.example.nexa.AlertDetail
import com.example.nexa.DeviceDetail
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0.dp),
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = NexaTokens.SpacingMedium)
        ) {
            item {
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = NexaTextPrimary)) { append("NEXA") }
                        withStyle(SpanStyle(color = NexaAction)) { append(".") }
                    },
                    style = Typography.displayLarge
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
                        Text(text = "SYSTEM STATE", style = Typography.labelMedium, color = NexaTextOnDarkMuted)
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                        Text(
                            text = "ENFORCING",
                            style = Typography.displayLarge,
                            color = NexaSecureOnDark
                        )
                        Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                        Text(
                            text = "Nftables backend is active. 3 devices quarantined.",
                            style = Typography.bodyMedium,
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
                Text(
                    text = "Recent Alerts",
                    style = Typography.titleLarge,
                    color = NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium)) // gap before activity list
            }

            items(MockData.recentAlerts) { alert ->
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
}

@Composable
fun AlertItem(alert: AlertItemData, onClick: () -> Unit) {
    val severityColor = when(alert.severity) {
        "CRITICAL" -> NexaCritical
        "WARNING" -> NexaWarning
        else -> NexaInformation
    }

    val variant = when(alert.severity) {
        "CRITICAL" -> GlassVariant.Strong
        else -> GlassVariant.Standard
    }

    GlassSurface(
        variant = variant,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = severityColor,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(if (alert.severity == "CRITICAL") 12.dp else 8.dp)
            ) {}
            Spacer(modifier = Modifier.width(NexaTokens.SpacingMedium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.description,
                    style = if (alert.severity == "CRITICAL") Typography.titleMedium else Typography.bodyLarge,
                    color = if (alert.severity == "CRITICAL") NexaTextPrimary else NexaTextSecondary
                )
                Spacer(modifier = Modifier.height(2.dp))
                TechnicalValue(text = alert.targetMac)
            }
            Text(text = alert.timeAgo, style = Typography.labelMedium, color = NexaTextMuted)
        }
    }
}
