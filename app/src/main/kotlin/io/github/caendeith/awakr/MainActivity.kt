package io.github.caendeith.awakr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.caendeith.awakr.core.ui.theme.AwakrTheme
import io.github.caendeith.awakr.navigation.AwakrNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwakrTheme {
                AwakrNavHost()
            }
        }
    }
}
