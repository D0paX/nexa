package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaElevatedBackground
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaTextOnDark
import com.example.nexa.theme.NexaTextOnDarkMuted
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTextSecondary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * A NEXA modal surface.
 *
 * Built from the same glass as the rest of the application rather than a
 * platform dialog, so a confirmation never arrives looking like it came from
 * a different product. [destructive] switches it to the charcoal anchor used
 * for consequential decisions.
 */
@Composable
fun NexaDialog(
    title: String,
    message: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit
) {
    val onDark = destructive
    Dialog(onDismissRequest = onDismissRequest) {
        GlassSurface(
            variant = if (destructive) GlassVariant.Destructive else GlassVariant.Strong,
            shape = NexaShapes.Dialog,
            contentPadding = PaddingValues(NexaTokens.SpacingLarge),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = NexaType.Headline,
                    color = if (onDark) NexaTextOnDark else NexaTextPrimary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingSmall))
                Text(
                    text = message,
                    style = NexaType.Body,
                    color = if (onDark) NexaTextOnDarkMuted else NexaTextSecondary
                )
                Spacer(modifier = Modifier.height(NexaTokens.SpacingLarge))
                Column(
                    verticalArrangement = Arrangement.spacedBy(NexaTokens.SpacingSmall),
                    modifier = Modifier.fillMaxWidth(),
                    content = actions
                )
            }
        }
    }
}

/**
 * A NEXA bottom sheet: contextual detail, selection and filtering.
 *
 * Uses the platform sheet for its drag and inset behavior — which must stay
 * native — while presenting NEXA's own surface and shape.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = NexaElevatedBackground,
        shape = NexaShapes.Sheet,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = NexaTokens.ScreenHorizontalPadding,
                    end = NexaTokens.ScreenHorizontalPadding,
                    bottom = NexaTokens.SpacingLarge
                )
        ) {
            if (title != null) {
                SectionHeader(text = title)
                Spacer(modifier = Modifier.height(NexaTokens.SpacingMedium))
            }
            content()
        }
    }
}
