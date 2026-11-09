package io.github.rvonbergen84.tvlauncher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PHOTO_INTERVALS = listOf(15, 30, 60, 300)

@Composable
fun SetupScreen(resumeTick: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firstButton = remember { FocusRequester() }

    var serviceOn by remember { mutableStateOf(false) }
    var homePackage by remember { mutableStateOf<String?>(null) }
    var catchKey by remember { mutableStateOf(Prefs.catchHomeKey(context)) }
    var redirect by remember { mutableStateOf(Prefs.redirectStockHome(context)) }
    var log by remember { mutableStateOf(Prefs.readLog(context)) }
    var place by remember { mutableStateOf(Prefs.place(context)) }
    var fahrenheit by remember { mutableStateOf(Prefs.fahrenheit(context)) }
    var photoThemes by remember { mutableStateOf(Prefs.photoThemes(context)) }
    var photoSeconds by remember { mutableStateOf(Prefs.photoSeconds(context)) }
    var hidden by remember { mutableStateOf(Catalog.arrange(context, Catalog.lastApps, Catalog.lastInputs).hidden) }
    var editingPlace by remember { mutableStateOf(false) }
    val installed = remember { Updater.installedVersion(context) }
    var latest by remember { mutableStateOf<Updater.Release?>(null) }
    var updating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { runCatching { firstButton.requestFocus() } }
    LaunchedEffect(Unit) { latest = withContext(Dispatchers.IO) { Updater.fetchLatest() } }
    LaunchedEffect(resumeTick) {
        while (true) {
            serviceOn = HomeButtonService.isEnabled(context)
            homePackage = Catalog.currentHomePackage(context)
            log = Prefs.readLog(context)
            delay(1_000)
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp)) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            item { Text("Settings", fontSize = 32.sp, color = Color.White) }

            item { Heading("Weather") }
            item {
                Button(onClick = { editingPlace = true }, modifier = Modifier.focusRequester(firstButton)) {
                    Text("Location: ${place?.name ?: "not set"}")
                }
            }
            item {
                Button(onClick = {
                    fahrenheit = !fahrenheit
                    Prefs.setFahrenheit(context, fahrenheit)
                }) { Text("Temperature: ${if (fahrenheit) "°F" else "°C"}") }
            }

            item { Heading("Pictures") }
            Photos.Theme.entries.forEach { theme ->
                item {
                    val on = theme in photoThemes
                    Button(onClick = {
                        val next = if (on) photoThemes - theme else photoThemes + theme
                        if (next.isEmpty()) {
                            toast(context, "Keep at least one kind of picture on.")
                        } else {
                            photoThemes = next
                            Prefs.setPhotoThemes(context, next)
                        }
                    }) { Text("${theme.label}: ${if (on) "On" else "Off"}") }
                }
            }
            item {
                Button(onClick = {
                    photoSeconds = PHOTO_INTERVALS[(PHOTO_INTERVALS.indexOf(photoSeconds) + 1) % PHOTO_INTERVALS.size]
                    Prefs.setPhotoSeconds(context, photoSeconds)
                }) { Text("Change every: ${describeSeconds(photoSeconds)}") }
            }
            item {
                Button(onClick = {
                    scope.launch {
                        val n = withContext(Dispatchers.IO) { Photos.load(context, forceRefresh = true).size }
                        toast(context, if (n > 0) "Loaded $n new pictures. They'll show on the home screen." else "Couldn't get new pictures. See the log.")
                    }
                }) { Text("Get new pictures now") }
            }

            item { Heading("Apps") }
            item {
                Button(onClick = {
                    Prefs.resetOrder(context)
                    toast(context, "Home screen order reset.")
                }) { Text("Reset home screen order") }
            }
            if (hidden.isEmpty()) {
                item { Text("Nothing hidden. Hold OK on a tile to hide it.", color = Muted, fontSize = 14.sp) }
            }
            hidden.forEach { app ->
                item {
                    Button(onClick = {
                        Prefs.setHidden(context, Prefs.hidden(context) - app.key)
                        hidden = hidden - app
                    }) { Text("Show ${app.label} again") }
                }
            }
            item {
                Button(onClick = { Catalog.openSystemSettings(context) }) { Text("TV settings") }
            }

            item { Heading("Home button") }
            item {
                StatusLine("Home button service", if (serviceOn) "On" else "Off", ok = serviceOn)
                StatusLine(
                    "System home app",
                    when (homePackage) {
                        null, "android" -> "Not chosen"
                        context.packageName -> "TV Launcher"
                        else -> homePackage!!
                    },
                    ok = homePackage == context.packageName,
                )
            }
            item {
                Button(onClick = {
                    if (!Catalog.openAccessibilitySettings(context)) {
                        toast(context, "Couldn't open Accessibility settings. Use the adb command below.")
                    }
                }) { Text("Turn on the Home button service") }
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

            item { Heading("Updates") }
            item {
                val newer = latest?.takeIf { it.versionCode > installed.versionCode }
                Button(
                    enabled = !updating,
                    onClick = {
                        if (!Updater.canInstall(context)) {
                            toast(context, "Turn on \"Allow from this source\", then press Back and Update again.")
                            Updater.openInstallPermission(context)
                            return@Button
                        }
                        updating = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                if (newer == null) {
                                    latest = Updater.fetchLatest()
                                } else {
                                    runCatching { Updater.downloadAndInstall(context) }
                                        .onFailure { Prefs.log(context, "Update failed: ${it.message}") }
                                }
                            }
                            updating = false
                        }
                    },
                ) {
                    Text(
                        when {
                            updating -> "Working..."
                            newer != null -> "Update to ${newer.versionName}"
                            latest == null -> "Check for update (have ${installed.versionName})"
                            else -> "Up to date (${installed.versionName}), check again"
                        },
                    )
                }
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
                    "If Accessibility settings won't let you turn the service on, run from a computer:\n" +
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
            if (log.isEmpty()) Text("Nothing yet.", color = Muted)
            log.take(24).forEach {
                Text(it, fontSize = 13.sp, color = Color.White, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }

    if (editingPlace) {
        PlaceDialog(
            onDone = { found ->
                editingPlace = false
                if (found != null) place = found
            },
        )
    }
}

@Composable
private fun PlaceDialog(onDone: (Prefs.Place?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    fun search() {
        if (searching || text.isBlank()) return
        searching = true
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { Weather.findPlace(text) }
                    .onFailure { Prefs.log(context, "Place search: ${it.message}") }
                    .getOrNull()
            }
            searching = false
            if (found == null) {
                toast(context, "Couldn't find \"$text\". Try a city name.")
            } else {
                Prefs.setPlace(context, found)
                toast(context, "Weather set to ${found.name}.")
                onDone(found)
            }
        }
    }

    Dialog(onDismissRequest = { onDone(null) }) {
        Column(
            Modifier.background(Color(0xFF1A202B), RoundedCornerShape(16.dp)).padding(24.dp).width(420.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Weather location", fontSize = 20.sp, color = Color.White)
            Text("City name or ZIP code", fontSize = 14.sp, color = Muted)
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier
                    .focusRequester(field)
                    .border(1.dp, Muted, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .width(372.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { search() }, enabled = !searching) { Text(if (searching) "Searching..." else "Save") }
                Button(onClick = { onDone(null) }) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 18.sp, color = Muted, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun StatusLine(label: String, value: String, ok: Boolean) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("$label: ", fontSize = 16.sp, color = Muted)
        Text(value, fontSize = 16.sp, color = if (ok) Color(0xFF7BD88F) else Warning)
    }
}

private fun describeSeconds(s: Int) = if (s < 60) "$s seconds" else if (s == 60) "1 minute" else "${s / 60} minutes"
