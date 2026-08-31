package com.example.nexa.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaAction
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.Typography

data class NavItem(val label: String, val icon: ImageVector, val isSelected: Boolean, val onClick: () -> Unit)

@Composable
fun NexaBottomNavigationBar(
    items: List<NavItem>,
    modifier: Modifier = Modifier
) {
    // Spatial Floating Control Layer
    GlassSurface(
        variant = GlassVariant.Standard,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NexaTokens.SpacingMedium, vertical = NexaTokens.SpacingLarge) // Elevated from bottom
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NexaTokens.SpacingSmall, horizontal = NexaTokens.SpacingSmall),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val weight by animateFloatAsState(targetValue = if (item.isSelected) 2f else 1f, label = "weight")
                NexaBottomNavigationItem(
                    item = item,
                    modifier = Modifier.weight(weight)
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
    val interactionSource = remember { MutableInteractionSource() }
    
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = item.onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (item.isSelected) {
            // Elevated glass pill for active state
            GlassSurface(
                variant = GlassVariant.Hero,
                shape = CircleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = NexaAction,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                    Text(
                        text = item.label,
                        style = Typography.labelMedium,
                        color = NexaTextPrimary
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = NexaTextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.label,
                    style = Typography.labelMedium,
                    color = NexaTextSecondary
                )
            }
        }
    }
}
