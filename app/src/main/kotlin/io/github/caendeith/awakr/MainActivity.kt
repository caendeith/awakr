package io.github.caendeith.awakr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF0B0C0E)
private val Accent = Color(0xFFFFB020)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { HelloAwakr() }
    }
}

@Composable
private fun HelloAwakr() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "07:30",
                color = Color(0xFFE9EAEC),
                fontSize = 88.sp,
                fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "Awakr",
                color = Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "toolchain ok",
                color = Color(0xFF8A8F98),
                fontSize = 14.sp,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun HelloAwakrPreview() {
    HelloAwakr()
}
