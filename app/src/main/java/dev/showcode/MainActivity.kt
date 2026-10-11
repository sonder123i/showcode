package dev.showcode

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.showcode.ui.ShowCodeRoot
import dev.showcode.ui.theme.ShowCodeTheme
import dev.showcode.widget.WidgetNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WidgetNav.request.value = intent
        setContent {
            ShowCodeTheme { ShowCodeRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WidgetNav.request.value = intent
    }
}
