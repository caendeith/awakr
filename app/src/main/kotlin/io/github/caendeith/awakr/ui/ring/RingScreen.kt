package io.github.caendeith.awakr.ui.ring

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.caendeith.awakr.R
import io.github.caendeith.awakr.core.ui.component.PrimaryButton
import io.github.caendeith.awakr.core.ui.component.SecondaryButton
import io.github.caendeith.awakr.core.ui.component.TimeText

@Composable
fun RingScreen(
    time: String,
    label: String,
    missionText: String,
    showSnooze: Boolean,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        TimeText(
            text = time,
            fontSize = MaterialTheme.typography.displayLarge.fontSize,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = missionText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        PrimaryButton(
            text = stringResource(R.string.ring_dismiss),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showSnooze) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(
                text = stringResource(R.string.ring_snooze),
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
