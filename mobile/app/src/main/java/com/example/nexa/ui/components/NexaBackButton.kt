package com.example.nexa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.example.nexa.theme.NexaInk
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTokens

/**
 * The single back control for every NEXA drill-down screen.
 *
 * A confident 24dp mark inside a full 48dp target. Pressing it settles a
 * faint ink wash beneath the icon — the same restrained surface response the
 * rest of the material system uses, with no glow, scale or bounce.
 */
@Composable
fun NexaBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Back"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressedSurface by animateColorAsState(
        targetValue = if (isPressed) NexaInk.copy(alpha = 0.08f) else Color.Transparent,
        label = "backPressedSurface"
    )

    Box(
        modifier = modifier
            .size(NexaTokens.MinTouchTarget)
            .clip(CircleShape)
            .background(pressedSurface, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = contentDescription,
            tint = NexaTextPrimary,
            modifier = Modifier.size(NexaTokens.BackIconSize)
        )
    }
}
