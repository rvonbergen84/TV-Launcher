package io.github.rvonbergen84.tvlauncher

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    /** Bumped on every resume so the screen reloads apps and status. */
    private val resumeTick = mutableIntStateOf(0)
    /** Bumped when Home is pressed while we're already open, to close Setup. */
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

private val Background = Color(0xFF10141C)
private val Muted = Color(0xFF9AA3B2)
private val Warning = Color(0xFFFFC266)

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
            HomeScreen(resumeTick, onOpenSetup = { showSetup = true })
        }
    }
}

@Composable
private fun HomeScreen(resumeTick: Int, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<InputEntry>>(emptyList()) }
    var serviceOn by remember { mutableStateOf(true) }
    val firstApp = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(resumeTick) {
        serviceOn = HomeButtonService.isEnabled(context)
        withContext(Dispatchers.IO) {
            val loadedApps = Catalog.loadApps(context)
            val loadedInputs = Catalog.loadInputs(context)
            withContext(Dispatchers.Main) {
                apps = loadedApps
                inputs = loadedInputs
            }
        }
    }
    LaunchedEffect(apps) {
        if (apps.isNotEmpty() && !focusedOnce) {
            runCatching { firstApp.requestFocus() }
            focusedOnce = true
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TopBar(onOpenSetup)
        }
        if (!serviceOn) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "The Home button isn't connected yet. Open Setup to turn it on.",
                    color = Warning,
                    fontSize = 16.sp,
                )
            }
        }
        if (inputs.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    SectionTitle("Inputs")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(inputs, key = { it.id }) { input ->
                            Card(
                                onClick = {
                                    if (!Catalog.switchToInput(context, input.id)) {
                                        toast(context, "This TV didn't accept the input switch.")
                                    }
                                },
                                modifier = Modifier.width(180.dp).height(64.dp),
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(input.label, fontSize = 18.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SectionTitle("Apps")
        }
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            AppCard(
                app = app,
                modifier = if (index == 0) Modifier.focusRequester(firstApp) else Modifier,
                onClick = {
                    if (!Catalog.launchApp(context, app.packageName)) {
                        toast(context, "Couldn't open ${app.label}.")
                    }
                },
            )
        }
    }
}

@Composable
private fun TopBar(onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(15_000)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            DateFormat.getTimeInstance(DateFormat.SHORT).format(now),
            fontSize = 32.sp,
            color = Color.White,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onOpenSetup) { Text("Setup") }
        Spacer(Modifier.width(12.dp))
        Button(onClick = { Catalog.openSystemSettings(context) }) { Text("TV settings") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 20.sp, color = Muted, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun AppCard(app: AppEntry, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.aspectRatio(16f / 9f)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF1E2533)), contentAlignment = Alignment.Center) {
            val bitmap = app.image
            when {
                bitmap != null && app.hasBanner -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        app.label,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(resumeTick: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    var serviceOn by remember { mutableStateOf(false) }
    var homePackage by remember { mutableStateOf<String?>(null) }
    var catchKey by remember { mutableStateOf(Prefs.catchHomeKey(context)) }
    var redirect by remember { mutableStateOf(Prefs.redirectStockHome(context)) }
    var log by remember { mutableStateOf(Prefs.readLog(context)) }
    val firstButton = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }
    LaunchedEffect(resumeTick) {
        while (true) {
            serviceOn = HomeButtonService.isEnabled(context)
            homePackage = Catalog.currentHomePackage(context)
            log = Prefs.readLog(context)
            delay(1_000)
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item { Text("Setup", fontSize = 32.sp, color = Color.White) }
            item {
                StatusLine("Home button service", if (serviceOn) "On" else "Off", ok = serviceOn)
                val isUs = homePackage == context.packageName
                StatusLine(
                    "System home app",
                    when (homePackage) {
                        null, "android" -> "Not chosen"
                        context.packageName -> "TV Launcher"
                        else -> homePackage!!
                    },
                    ok = isUs,
                )
            }
            item {
                Button(
                    onClick = {
                        if (!Catalog.openAccessibilitySettings(context)) {
                            toast(context, "Couldn't open Accessibility settings. Use the adb command on the right.")
                        }
                    },
                    modifier = Modifier.focusRequester(firstButton),
                ) { Text("1. Turn on the Home button service") }
            }
            item {
                Button(onClick = {
                    catchKey = !catchKey
                    Prefs.setCatchHomeKey(context, catchKey)
                }) { Text("Method A, catch Home key: ${if (catchKey) "On" else "Off"}") }
            }
            item {
                Button(onClick = {
                    redirect = !redirect
                    Prefs.setRedirectStockHome(context, redirect)
                }) { Text("Method B, replace stock home: ${if (redirect) "On" else "Off"}") }
            }
            item {
                Button(onClick = {
                    if (!Catalog.openStockHome(context)) toast(context, "Couldn't find the stock home screen.")
                }) { Text("Open stock home for 60 seconds") }
            }
            item {
                Button(onClick = {
                    Prefs.clearLog(context)
                    log = emptyList()
                }) { Text("Clear log") }
            }
            item { Button(onClick = onClose) { Text("Back") } }
            item {
                Text(
                    "If Accessibility settings won't let you turn it on, run from a computer:\n" +
                        "adb shell settings put secure enabled_accessibility_services " +
                        "${context.packageName}/.HomeButtonService\n" +
                        "adb shell settings put secure accessibility_enabled 1",
                    fontSize = 12.sp,
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text("Log (newest first)", fontSize = 20.sp, color = Muted)
            Spacer(Modifier.height(8.dp))
            if (log.isEmpty()) {
                Text("Nothing yet. Press Home on the remote and come back here.", color = Muted)
            }
            log.take(24).forEach {
                Text(it, fontSize = 13.sp, color = Color.White, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 18.sp, color = Muted)
        Text(value, fontSize = 18.sp, color = if (ok) Color(0xFF7BD88F) else Warning)
    }
}

private fun toast(context: android.content.Context, message: String) =
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
