package pl.walbrzych.autobus

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import pl.walbrzych.autobus.ui.AutoBusApp
import pl.walbrzych.autobus.ui.DepartureLiveUpdateNavigation
import pl.walbrzych.autobus.ui.theme.AutoBusTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DepartureLiveUpdateNavigation.accept(intent)
        enableEdgeToEdge()
        setContent {
            AutoBusTheme {
                AutoBusApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DepartureLiveUpdateNavigation.accept(intent)
    }
}
