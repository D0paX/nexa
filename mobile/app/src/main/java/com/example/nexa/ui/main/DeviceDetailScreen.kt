package com.example.nexa.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import com.example.nexa.ActionConfirmation
import com.example.nexa.theme.*
import com.example.nexa.ui.components.*

@Composable
fun DeviceDetailScreen(
    mac: String,
    onBack: () -> Unit,
    onNavigate: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    NexaScreen(
        modifier = modifier,
        title = "Device Context",
        onBack = onBack,
        itemSpacing = NexaTokens.SpacingMedium
    ) {
        item {
            SectionHeader(
                text = "Identity",
                level = SectionLevel.Group,
                modifier = Modifier.padding(bottom = NexaTokens.SpacingSmall)
            )
        }

        // TrustedDeviceIdentity (spatial anchor)
        item {
            GlassSurface(variant = GlassVariant.Hero, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = NexaTokens.SpacingSmall)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TRUSTED IDENTITY", style = NexaType.Metadata, color = NexaSecureOnDark)
                        Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                        StatusBadge(status = NexaStatus.Verified, onDarkSurface = true)
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    Text("Corp Laptop - Engineering", style = NexaType.Headline, color = NexaTextOnDark)
                    Text("Owner: jsmith@example.com", style = NexaType.Body, color = NexaTextOnDarkMuted)
                }
            }
        }

        // DeviceRecord
        item {
            GlassSurface(variant = GlassVariant.Standard, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("Network Record", style = NexaType.Button, color = NexaTextPrimary)
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
                    DetailRow(label = "MAC Address") { TechnicalValue(mac) }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    DetailRow(label = "Current IP") { TechnicalValue("192.168.1.105") }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                    DetailRow(label = "Scope") { TechnicalValue("VLAN_SECURE", color = NexaInformation) }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            SectionHeader(text = "Enforcement & Actions", level = SectionLevel.Group)
        }

        item {
            GlassSurface(variant = GlassVariant.Standard, modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("State", style = NexaType.Body, color = NexaTextPrimary)
                        StatusBadge(status = NexaStatus.Permitted)
                    }
                    Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))

                    NexaButton(
                        text = "QUARANTINE",
                        onClick = { onNavigate(ActionConfirmation("QUARANTINE_DEVICE", mac, "Quarantine Device")) },
                        variant = NexaButtonVariant.Destructive,
                        icon = NexaIcons.Quarantine
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(NexaTokens.SpacingXLarge))
        }
    }
}

/** Label on the left, technical value on the right — the record row pattern. */
@Composable
private fun DetailRow(label: String, value: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = NexaType.BodySecondary, color = NexaTextSecondary)
        value()
    }
}
