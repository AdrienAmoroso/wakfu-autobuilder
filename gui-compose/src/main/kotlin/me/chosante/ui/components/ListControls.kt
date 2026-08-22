package me.chosante.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WTypography

/**
 * Small, generic list/form building blocks shared by the Market and Kamas screens -- both are
 * "browse a ranked/filterable list, click a row for detail" UIs, so the pill tab button, compact
 * text field/button, empty/error message card, and label-value stat row are reused verbatim rather
 * than re-implemented per screen.
 */
@Composable
internal fun TabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) WColor.raised else WColor.bg)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = WTypography.labelMedium.copy(color = if (selected) WColor.text else WColor.muted))
    }
}

@Composable
internal fun SmallButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (filled) WColor.accent else WColor.raised)
                .border(1.dp, if (filled) WColor.accent else WColor.border, RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style =
                WTypography.labelMedium.copy(
                    color =
                        if (!enabled) {
                            WColor.faint
                        } else if (filled) {
                            WColor.bg
                        } else {
                            WColor.text
                        }
                )
        )
    }
}

@Composable
internal fun SmallTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WColor.surface)
                .border(1.dp, WColor.border, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            cursorBrush = SolidColor(WColor.accent),
            textStyle = WTypography.bodyMedium.copy(color = WColor.text),
            modifier = Modifier.fillMaxWidth()
        )
        if (value.isEmpty()) {
            Text(text = placeholder, style = WTypography.bodyMedium.copy(color = WColor.faint))
        }
    }
}

@Composable
internal fun MessageCard(
    title: String,
    hint: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(WDimens.radius))
                .background(WColor.surface)
                .border(1.dp, WColor.hairline, RoundedCornerShape(WDimens.radius))
                .padding(vertical = 28.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = title, style = WTypography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = hint, style = WTypography.bodySmall.copy(color = WColor.muted))
    }
}

@Composable
internal fun StatLine(
    label: String,
    value: String,
    hint: String? = null,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = WTypography.bodyMedium.copy(color = WColor.muted))
            if (hint != null) {
                InfoTip(text = hint)
            }
        }
        Text(text = value, style = WTypography.bodyMedium)
    }
}
