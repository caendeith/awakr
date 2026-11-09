package io.github.caendeith.awakr.health

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import io.github.caendeith.awakr.MainActivity

/**
 * Health Connect opens this screen when the user taps "why does this app read my sleep?".
 * Awakr has no long permission pitch: it just brings the app to the front.
 */
class HealthConnectRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
        finish()
    }
}
