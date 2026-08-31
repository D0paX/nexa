package com.example.nexa.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.example.nexa.theme.NexaInk
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTokens

/**
 * Every icon in NEXA is drawn through here.
 *
 * Size comes from the token scale rather than the drawing library's default,
 * and `contentDescription` defaults to null so decorative icons stay out of
 * the accessibility tree — an icon that duplicates adjacent text should not
 * be announced twice.
 */
@Composable
fun NexaIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Dp = NexaTokens.IconMedium,
    tint: Color = NexaTextPrimary
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size)
    )
}

/**
 * An icon-only control: a token-sized glyph centered in a target that is
 * never smaller than 48dp, however small the glyph is. Pressing settles a
 * faint ink wash — the restrained response the material system uses
 * everywhere else, with no scale, bounce or glow.
 */
@Composable
fun NexaIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = NexaTokens.IconLarge,
    tint: Color = NexaTextPrimary
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressedSurface by animateColorAsState(
        targetValue = if (isPressed) NexaInk.copy(alpha = 0.08f) else Color.Transparent,
        label = "iconButtonPressedSurface"
    )

    Box(
        modifier = modifier
            .sizeIn(minWidth = NexaTokens.MinTouchTarget, minHeight = NexaTokens.MinTouchTarget)
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
        NexaIcon(
            icon = icon,
            contentDescription = contentDescription,
            size = iconSize,
            tint = tint
        )
    }
}
