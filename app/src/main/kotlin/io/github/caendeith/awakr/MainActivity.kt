package io.github.caendeith.awakr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.caendeith.awakr.core.ui.theme.AwakrTheme
import io.github.caendeith.awakr.navigation.AwakrNavHost
import io.github.caendeith.awakr.navigation.Routes

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AwakrApplication
        setContent {
            AwakrTheme {
                // The onboarding flag lives in DataStore: render the themed background for the
                // one frame it takes to read it instead of flashing the wrong start screen.
                val settings by app.data.settings.settings.collectAsState(initial = null)
                val loaded = settings
                if (loaded == null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {}
                } else {
                    AwakrNavHost(
                        data = app.data,
                        plugins = app.plugins,
                        startDestination = if (loaded.onboarded) Routes.HOME else Routes.ONBOARDING,
                    )
                }
            }
        }
    }
}