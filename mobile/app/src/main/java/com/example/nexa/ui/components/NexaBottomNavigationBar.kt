package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaAction
import com.example.nexa.theme.NexaBackground
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
    // Wrap the navigation bar in a GlassSurface to give it the NEXA liquid glass treatment
    GlassSurface(
        variant = GlassVariant.Standard,
        modifier = modifier.fillMaxWidth().padding(horizontal = NexaTokens.SpacingSmall, vertical = NexaTokens.SpacingSmall)
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            contentColor = NexaTextPrimary,
            tonalElevation = 0.dp // Managed by GlassSurface
        ) {
            items.forEach { item ->
                NavigationBarItem(
                    selected = item.isSelected,
                    onClick = item.onClick,
                    icon = {
                        // Semantic accent dot for selected state to emphasize spatial/clean design instead of heavy icons
                        if (item.isSelected) {
                            Box(modifier = Modifier.padding(bottom = 4.dp)) {
                                Text("•", color = NexaAction, style = Typography.titleLarge)
                            }
                        }
                    },
                    label = { 
                        Text(
                            text = item.label.uppercase(), 
                            style = Typography.labelMedium,
                            color = if (item.isSelected) NexaTextPrimary else NexaTextSecondary
                        ) 
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NexaAction,
                        unselectedIconColor = NexaTextSecondary,
                        selectedTextColor = NexaTextPrimary,
                        unselectedTextColor = NexaTextSecondary,
                        indicatorColor = Color.Transparent // We use our own indicator (the dot and color)
                    )
                )
            }
        }
    }
}
