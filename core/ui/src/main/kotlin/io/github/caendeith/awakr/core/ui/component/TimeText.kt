package io.github.caendeith.awakr.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.caendeith.awakr.core.ui.theme.TimeStyle

/**
 * Tabular monospace clock text. Keeps digits from jumping while counting down.
 */
@Composable
fun TimeText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 72.sp,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    style: TextStyle = MaterialTheme.typography.displayLarge,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = 1,
        style = style.merge(TimeStyle).copy(fontSize = fontSize, lineHeight = fontSize * 1.05f),
    )
}
