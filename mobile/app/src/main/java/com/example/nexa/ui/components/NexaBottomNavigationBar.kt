package com.example.nexa.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaAction
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.Typography

data class NavItem(val label: String, val isSelected: Boolean, val onClick: () -> Unit)

@Composable
fun NexaBottomNavigationBar(
    items: List<NavItem>,
    modifier: Modifier = Modifier
) {
    // Spatial Floating Control Layer
    GlassSurface(
        variant = GlassVariant.Standard,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NexaTokens.SpacingMedium, vertical = NexaTokens.SpacingMedium)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NexaTokens.SpacingSmall, horizontal = NexaTokens.SpacingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                NexaBottomNavigationItem(
                    item = item,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun NexaBottomNavigationItem(
    item: NavItem,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(NexaTokens.CornerRadiusMedium))
            .clickable(onClick = item.onClick),
        contentAlignment = Alignment.Center
    ) {
        if (item.isSelected) {
            // Elevated glass pill for active state
            GlassSurface(
                variant = GlassVariant.Hero,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = item.label.uppercase(),
                        style = Typography.labelMedium,
                        color = NexaAction
                    )
                }
            }
        } else {
            Text(
                text = item.label.uppercase(),
                style = Typography.labelMedium,
                color = NexaTextSecondary
            )
        }
    }
}
