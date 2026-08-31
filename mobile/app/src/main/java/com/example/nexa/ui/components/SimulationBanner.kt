package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaSimulationOnDark
import com.example.nexa.theme.NexaTextOnDarkMuted
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * The AUDIT_ONLY execution-mode banner.
 *
 * Phase 4 draws a hard line between an execution that will mutate nftables
 * and one that will not. That line has to be visible before the operator
 * commits, so simulation gets its own surface, its own color and its own
 * flask icon — it must never be possible to mistake a simulated run for a
 * real one, or to discover the difference only afterwards in the audit log.
 *
 * Defined once here so every future screen that can execute in AUDIT_ONLY
 * announces it identically.
 */
@Composable
fun SimulationBanner(
    modifier: Modifier = Modifier,
    title: String = "SIMULATION ONLY",
    detail: String = "NO FIREWALL MUTATION WILL OCCUR"
) {
    GlassSurface(
        variant = GlassVariant.Hero,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NexaIcon(
                    icon = NexaIcons.Simulated,
                    size = NexaTokens.IconLarge,
                    tint = NexaSimulationOnDark
                )
                Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                Text(text = title, style = NexaType.Headline, color = NexaSimulationOnDark)
            }
            Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
            Text(text = detail, style = NexaType.Metadata, color = NexaTextOnDarkMuted)
        }
    }
}
