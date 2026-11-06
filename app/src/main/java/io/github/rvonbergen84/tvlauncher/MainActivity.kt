package io.github.rvonbergen84.tvlauncher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the screen reloads apps, weather and status. */
    private val resumeTick = mutableIntStateOf(0)
    /** Bumped when Home is pressed while we're already open: close Setup, jump back to the top. */
    private val homeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                LauncherScreen(resumeTick.intValue, homeTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homeTick.intValue++
    }
}

val Background = Color(0xFF0B0E14)
val Muted = Color(0xFFB4BCC8)
val Warning = Color(0xFFFFC266)

@Composable
private fun LauncherScreen(resumeTick: Int, homeTick: Int) {
    var showSetup by remember { mutableStateOf(false) }
    LaunchedEffect(homeTick) { showSetup = false }

    Box(Modifier.fillMaxSize().background(Background)) {
        if (showSetup) {
            BackHandler { showSetup = false }
            SetupScreen(resumeTick, onClose = { showSetup = false })
        } else {
            BackHandler { /* A home screen has nowhere to go back to. */ }
            HomeScreen(resumeTick, homeTick, onOpenSetup = { showSetup = true })
        }
    }
}

fun toast(context: Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
