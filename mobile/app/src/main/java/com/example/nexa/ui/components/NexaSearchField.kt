package com.example.nexa.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import com.example.nexa.theme.GlassVariant
import com.example.nexa.theme.NexaAction
import com.example.nexa.theme.NexaIcons
import com.example.nexa.theme.NexaShapes
import com.example.nexa.theme.NexaTextMuted
import com.example.nexa.theme.NexaTextPrimary
import com.example.nexa.theme.NexaTokens
import com.example.nexa.theme.NexaType

/**
 * The NEXA search field.
 *
 * Built on the glass material rather than a Material text field so it
 * belongs to the same surface hierarchy as everything around it. The clear
 * affordance appears only when there is something to clear, and carries its
 * own 48dp target.
 */
@Composable
fun NexaSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    label: String = "Search"
) {
    val focusManager = LocalFocusManager.current

    GlassSurface(
        variant = GlassVariant.Standard,
        shape = NexaShapes.Pill,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = NexaTokens.SpacingMedium,
            vertical = NexaTokens.SpacingXSmall
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NexaTokens.MinTouchTarget)
        ) {
            NexaIcon(
                icon = NexaIcons.Search,
                size = NexaTokens.IconMedium,
                tint = NexaTextMuted
            )
            Spacer(modifier = Modifier.width(NexaTokens.SpacingSmall))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = NexaType.Body,
                        color = NexaTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.merge(NexaType.Body).merge(
                        androidx.compose.ui.text.TextStyle(color = NexaTextPrimary)
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(NexaAction),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = label }
                )
            }

            if (query.isNotEmpty()) {
                NexaIconButton(
                    icon = NexaIcons.Cancel,
                    onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    },
                    contentDescription = "Clear search",
                    iconSize = NexaTokens.IconMedium,
                    tint = NexaTextMuted
                )
            }
        }
    }
}
