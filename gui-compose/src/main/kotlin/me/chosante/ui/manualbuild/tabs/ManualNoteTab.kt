package me.chosante.ui.manualbuild.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.chosante.ui.i18n.Tr
import me.chosante.ui.i18n.tr
import me.chosante.ui.theme.WColor
import me.chosante.ui.theme.WDimens
import me.chosante.ui.theme.WTypography

/**
 * The manual-construction screen's Note tab: one freeform textarea, bound to
 * [me.chosante.ui.state.UiState.manualNote]. Same textarea idiom as `Modals.kt`'s private
 * `MultilineField` (the import dialog's paste box), recreated locally without the monospace font --
 * this one is for prose, not pasted JSON.
 */
@Composable
fun ManualNoteTab(
    note: String,
    onNoteChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(WDimens.gap)) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(WDimens.radius))
                    .background(WColor.surface)
                    .border(1.dp, WColor.border, RoundedCornerShape(WDimens.radius))
                    .padding(16.dp)
        ) {
            BasicTextField(
                value = note,
                onValueChange = onNoteChange,
                singleLine = false,
                cursorBrush = SolidColor(WColor.accent),
                textStyle = WTypography.bodyMedium.copy(color = WColor.text, lineHeight = 20.sp),
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
            if (note.isEmpty()) {
                Text(text = tr(Tr.MANUAL_NOTE_PLACEHOLDER), style = WTypography.bodyMedium.copy(color = WColor.faint))
            }
        }
    }
}
