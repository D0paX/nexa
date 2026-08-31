package com.example.nexa.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaAction
import com.example.nexa.theme.NexaMotion
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

data class NavItem(val label: String, val icon: ImageVector, val isSelected: Boolean, val onClick: () -> Unit)

@Composable
fun NexaBottomNavigationBar(
    items: List<NavItem>,
    modifier: Modifier = Modifier
) {
    // Independent floating control plane — quiet, so the selected capsule reads against it
    GlassSurface(
        variant = GlassVariant.Standard,
        shape = NexaShapes.Pill,
        contentPadding = PaddingValues(
            horizontal = NexaTokens.SpacingSmall,
            vertical = NexaTokens.NavigationBarVerticalPadding
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = NexaTokens.NavigationHorizontalMargin,
                vertical = NexaTokens.NavigationBottomSpacing
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NexaTokens.NavigationHeight)
                .padding(horizontal = NexaTokens.NavigationItemSpacing),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val weight by animateFloatAsState(
                    targetValue = if (item.isSelected) 2f else 1f,
                    animationSpec = NexaMotion.standard(),
                    label = "weight"
                )
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
            .fillMaxHeight()
            .clip(NexaShapes.Pill)
            .semantics { selected = item.isSelected }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClickLabel = item.label,
                onClick = item.onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (item.isSelected) {
            // Elevated glass capsule: near-opaque surface, NEXA red accent, dark readable label
            GlassSurface(
                variant = GlassVariant.Selected,
                shape = NexaShapes.Pill,
                contentPadding = PaddingValues(
                    horizontal = NexaTokens.SpacingSmall,
                    vertical = NexaTokens.NavigationActivePillVerticalPadding
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NexaTokens.NavigationActivePillHeight)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {
                    NexaIcon(
                        icon = item.icon,
                        size = NexaTokens.NavigationIconSize,
                        tint = NexaAction
                    )
                    Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))
                    Text(
                        text = item.label,
                        style = NexaType.Metadata,
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
                NexaIcon(
                    icon = item.icon,
                    size = NexaTokens.NavigationIconSize,
                    tint = NexaTextSecondary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingHairline))
                Text(
                    text = item.label,
                    style = NexaType.Metadata,
                    color = NexaTextSecondary
                )
            }
        }
    }
}
