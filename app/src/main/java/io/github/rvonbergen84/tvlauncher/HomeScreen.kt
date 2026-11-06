package io.github.rvonbergen84.tvlauncher

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.random.Random

private const val SIDE = 56
private const val GRID_COLUMNS = 6
private val TileShape = RoundedCornerShape(10.dp)
private val TextShadow = Shadow(Color.Black.copy(alpha = 0.7f), Offset(0f, 2f), 10f)

/** Survives leaving and re-entering the home screen, so weather shows instantly. */
private object WeatherMemory {
    var key: String = ""
    var fetchedAt = 0L
    var now: Weather.Now? = null
}

@Composable
fun HomeScreen(resumeTick: Int, homeTick: Int, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf(Catalog.lastApps) }
    var inputs by remember { mutableStateOf<List<InputEntry>>(emptyList()) }
    var editTick by remember { mutableIntStateOf(0) }
    val layout = remember(apps, editTick) { Catalog.arrange(context, apps) }
    var serviceOn by remember { mutableStateOf(true) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var photos by remember { mutableStateOf(Photos.cachedOnly(context)) }
    val frame = rememberPhotoFrame(photos)
    val place = remember { Prefs.place(context) }
    val weather = rememberWeather(place)
    val now = rememberNow()
    val listState = rememberLazyListState()
    val firstFavorite = remember { FocusRequester() }

    LaunchedEffect(resumeTick) {
        serviceOn = HomeButtonService.isEnabled(context)
        val loaded = withContext(Dispatchers.IO) { Catalog.loadApps(context) to Catalog.loadInputs(context) }
        apps = loaded.first
        inputs = loaded.second
    }
    LaunchedEffect(Unit) {
        while (true) {
            photos = withContext(Dispatchers.IO) { Photos.load(context) }.ifEmpty { photos }
            delay(6L * 60 * 60 * 1000) // Photos.load only hits the network once a day
        }
    }
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            listState.scrollToItem(0)
            runCatching { firstFavorite.requestFocus() }
        }
    }

    HomeContent(
        layout = layout,
        inputs = inputs,
        serviceOn = serviceOn,
        frame = frame,
        now = now,
        weather = weather,
        place = place,
        listState = listState,
        firstFavorite = firstFavorite,
        onOpenSetup = onOpenSetup,
        onAppMenu = { menuFor = it },
    )

    menuFor?.let { app ->
        AppMenu(
            app = app,
            favorites = layout.favorites.map { it.packageName },
            onDone = { changed ->
                menuFor = null
                if (changed) editTick++
            },
        )
    }
}

/** Everything drawn on the home screen. No loading here, so it can be previewed with sample data. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    layout: Catalog.Layout,
    inputs: List<InputEntry>,
    serviceOn: Boolean,
    frame: Frame?,
    now: Date,
    weather: Weather.Now?,
    place: Prefs.Place?,
    listState: LazyListState,
    firstFavorite: FocusRequester,
    onOpenSetup: () -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    LaunchedEffect(layout.favorites.isNotEmpty()) {
        if (layout.favorites.isNotEmpty()) runCatching { firstFavorite.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val favGap = 24.dp
        val gridGap = 20.dp
        val content = maxWidth - (SIDE * 2).dp
        val favWidth = (content - favGap * (Prefs.FAVORITE_SLOTS - 1)) / Prefs.FAVORITE_SLOTS
        val gridWidth = (content - gridGap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val heroHeight = maxHeight - favWidth * 9f / 16f - 96.dp
        // Which part of the screen has focus. The top row keeps the photo sharp and
        // the clock showing; anything below it blurs and dims the photo.
        var inGrid by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val appsTitleIndex = if (serviceOn) 2 else 3 // after hero, favorites, [warning]
        val onFavoritesFocus = Modifier.onFocusChanged {
            if (it.hasFocus) {
                inGrid = false
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
        val onGridFocus = Modifier.onFocusChanged {
            if (it.hasFocus && !inGrid) {
                inGrid = true
                scope.launch { listState.animateScrollToItem(appsTitleIndex) }
            }
        }

        Backdrop(frame, dimmed = inGrid)

        // TVs default to scrolling the selected item a third of the way down the screen,
        // which pushed the top row up. Only scroll as much as needed to keep it in view.
        CompositionLocalProvider(LocalBringIntoViewSpec provides KeepInView) {
        LazyColumn(
            state = listState,
            // Room at the end so the grid can always scroll the top row fully off screen.
            contentPadding = PaddingValues(start = SIDE.dp, end = SIDE.dp, bottom = maxHeight / 2),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                Box(Modifier.fillMaxWidth().height(heroHeight)) {
                    frame?.let { Caption(it.photo, Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp)) }
                }
            }
            item(key = "favorites") {
                Row(horizontalArrangement = Arrangement.spacedBy(favGap), modifier = onFavoritesFocus) {
                    layout.favorites.forEachIndexed { index, app ->
                        AppTile(
                            app = app,
                            width = favWidth,
                            modifier = if (index == 0) Modifier.focusRequester(firstFavorite) else Modifier,
                            onLongClick = { onAppMenu(app) },
                        )
                    }
                }
            }
            if (!serviceOn) {
                item(key = "warning") {
                    Text(
                        "The Home button isn't connected. Open Settings at the top right to turn it on.",
                        color = Warning,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (layout.others.isNotEmpty()) {
                item(key = "apps-title") {
                    SectionTitle("Apps")
                }
                items(layout.others.chunked(GRID_COLUMNS), key = { row -> row.first().packageName }) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gridGap),
                        modifier = onGridFocus.padding(bottom = 4.dp),
                    ) {
                        row.forEach { app -> AppTile(app, gridWidth, Modifier, onLongClick = { onAppMenu(app) }) }
                    }
                }
            }
            if (inputs.isNotEmpty()) {
                item(key = "inputs") {
                    Column(onGridFocus) {
                        SectionTitle("Inputs")
                        Row(horizontalArrangement = Arrangement.spacedBy(gridGap)) {
                            inputs.forEach { InputTile(it, gridWidth) }
                        }
                    }
                }
            }
        }

        }

        TopOverlay(now, weather, place, hidden = inGrid, onOpenSetup)
    }
}

/** Scrolls only as far as needed to keep the focused item on screen, with a small margin. */
@OptIn(ExperimentalFoundationApi::class)
private object KeepInView : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val margin = containerSize * 0.06f
        val end = offset + size
        return when {
            size + margin * 2 > containerSize -> offset - margin
            offset < margin -> offset - margin
            end > containerSize - margin -> end - (containerSize - margin)
            else -> 0f
        }
    }
}

// ---------- Background photo ----------

class Frame(val bitmap: ImageBitmap, val photo: Photos.Photo) {
    /** Soft blurred copy, shown behind the app grid. Made once per photo. */
    val blurred: ImageBitmap by lazy { softBlur(bitmap) }
}

/**
 * Smooth blur that works on every Android version (Compose's blur() needs Android 12+).
 * Shrinks the photo to 1/8 size, runs three box-blur passes on it (close to a Gaussian
 * blur), and lets the screen stretch the result back up. Takes a few milliseconds.
 */
private fun softBlur(source: ImageBitmap): ImageBitmap {
    val src = source.asAndroidBitmap()
    val w = maxOf(1, src.width / 8)
    val h = maxOf(1, src.height / 8)
    val small = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(3) {
        boxBlur(px, tmp, w, h, radius = 5, horizontal = true)
        boxBlur(tmp, px, w, h, radius = 5, horizontal = false)
    }
    val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out.asImageBitmap()
}

/** One pass of a sliding-window box blur along rows or columns. */
private fun boxBlur(input: IntArray, output: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val length = if (horizontal) w else h
    val window = radius * 2 + 1
    for (line in 0 until lines) {
        fun at(i: Int): Int {
            val c = i.coerceIn(0, length - 1)
            return input[if (horizontal) line * w + c else c * w + line]
        }
        var r = 0; var g = 0; var b = 0
        for (i in -radius..radius) {
            val p = at(i); r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
        }
        for (i in 0 until length) {
            val idx = if (horizontal) line * w + i else i * w + line
            output[idx] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val add = at(i + radius + 1)
            val drop = at(i - radius)
            r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
            b += (add and 0xFF) - (drop and 0xFF)
        }
    }
}

/** How dark the photo gets behind the app grid. */
internal const val GRID_DIM = 0.4f

/** Cycles through the photos on a timer, loading each before showing it. */
@Composable
private fun rememberPhotoFrame(photos: List<Photos.Photo>): Frame? {
    val context = LocalContext.current
    var frame by remember { mutableStateOf<Frame?>(null) }

    LaunchedEffect(photos) {
        if (photos.isEmpty()) return@LaunchedEffect
        var index = Random.nextInt(photos.size)
        var failures = 0
        while (failures < photos.size) {
            val photo = photos[index % photos.size]
            index++
            val bitmap = loadBitmap(context, photo.url)
            if (bitmap == null) {
                failures++
                delay(1_000)
                continue
            }
            failures = 0
            frame = Frame(bitmap, photo)
            // Fetch the next one now so the switch is instant.
            prefetch(context, photos[index % photos.size].url)
            delay(Prefs.photoSeconds(context) * 1000L)
        }
        Prefs.log(context, "Photos: none of the images would load")
    }
    return frame
}

@Composable
private fun Backdrop(frame: Frame?, dimmed: Boolean) {
    val dim by animateFloatAsState(if (dimmed) GRID_DIM else 0f, tween(500), label = "dim")
    val blur by animateFloatAsState(if (dimmed) 1f else 0f, tween(500), label = "blur")
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = frame, animationSpec = tween(1500), label = "photo") { f ->
            if (f != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(f.bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    if (blur > 0f) {
                        Image(
                            f.blurred,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().alpha(blur),
                        )
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF1B2638), Color(0xFF0B0E14))),
                    ),
                )
            }
        }
        // Keeps the clock readable on bright photos.
        Box(
            Modifier.fillMaxWidth().height(200.dp).background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)),
            ),
        )
        // Keeps the app row readable.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.5f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.7f),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
    }
}

private fun photoRequest(context: Context, url: String): ImageRequest {
    val m = context.resources.displayMetrics
    // Decode at the screen's full size, exactly, so nothing gets scaled down first.
    return ImageRequest.Builder(context).data(url)
        .size(maxOf(m.widthPixels, m.heightPixels), minOf(m.widthPixels, m.heightPixels))
        .precision(Precision.EXACT)
        .allowHardware(false)
        .build()
}

private suspend fun loadBitmap(context: Context, url: String): ImageBitmap? {
    val result = context.imageLoader.execute(photoRequest(context, url))
    return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
}

private fun prefetch(context: Context, url: String) {
    context.imageLoader.enqueue(photoRequest(context, url))
}

@Composable
private fun Caption(photo: Photos.Photo, modifier: Modifier) {
    val text = listOf(photo.title, photo.credit).filter { it.isNotBlank() }.joinToString("  ·  ")
    Text(
        text,
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.75f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(shadow = TextShadow),
        textAlign = TextAlign.End,
        modifier = modifier.width(420.dp),
    )
}

// ---------- Clock, weather, settings ----------

@Composable
private fun rememberNow(): Date {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(5_000)
        }
    }
    return now
}

/** Current weather for the saved place, refreshed every 15 minutes. */
@Composable
private fun rememberWeather(place: Prefs.Place?): Weather.Now? {
    val context = LocalContext.current
    var weather by remember { mutableStateOf(WeatherMemory.now) }
    LaunchedEffect(place) {
        if (place == null) return@LaunchedEffect
        while (true) {
            val key = "${place.lat},${place.lon},${Prefs.fahrenheit(context)}"
            val stale = System.currentTimeMillis() - WeatherMemory.fetchedAt > 15 * 60_000
            if (key != WeatherMemory.key || stale) {
                val fresh = withContext(Dispatchers.IO) {
                    runCatching { Weather.fetch(context) }
                        .onFailure { Prefs.log(context, "Weather: ${it.message}") }
                        .getOrNull()
                }
                if (fresh != null) {
                    WeatherMemory.key = key
                    WeatherMemory.fetchedAt = System.currentTimeMillis()
                    WeatherMemory.now = fresh
                }
                weather = WeatherMemory.now
            }
            delay(60_000)
        }
    }
    return weather
}

@Composable
private fun TopOverlay(now: Date, weather: Weather.Now?, place: Prefs.Place?, hidden: Boolean, onOpenSetup: () -> Unit) {
    val context = LocalContext.current
    // Fades out while browsing the app grid, which scrolls up underneath it.
    val alpha by animateFloatAsState(if (hidden) 0f else 1f, tween(300), label = "overlay")
    Row(
        Modifier.fillMaxWidth().alpha(alpha).padding(start = SIDE.dp, end = SIDE.dp, top = 28.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                android.text.format.DateFormat.getTimeFormat(context).format(now),
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                style = TextStyle(shadow = TextShadow),
            )
            val w = weather
            when {
                place == null -> Text(
                    "Add your city in Settings to see the weather",
                    fontSize = 14.sp,
                    color = Muted,
                    style = TextStyle(shadow = TextShadow),
                )
                w != null -> Text(
                    "${w.icon}  ${w.temperature}°  ${w.description}  ·  ${place.name.substringBefore(',')}",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    style = TextStyle(shadow = TextShadow),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(
            onClick = onOpenSetup,
            colors = IconButtonDefaults.colors(
                containerColor = Color.Black.copy(alpha = 0.2f),
                contentColor = Color.White.copy(alpha = 0.85f),
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
            modifier = Modifier.focusProperties { canFocus = !hidden },
        ) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
    }
}

// ---------- Tiles ----------

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 18.sp, color = Muted, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
}

@Composable
private fun tileBorder() = CardDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = TileShape),
)

@Composable
private fun AppTile(app: AppEntry, width: Dp, modifier: Modifier, onLongClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width)) {
        Card(
            onClick = {
                if (!Catalog.launchApp(context, app.packageName)) toast(context, "Couldn't open ${app.label}.")
            },
            onLongClick = onLongClick,
            shape = CardDefaults.shape(TileShape),
            border = tileBorder(),
            colors = CardDefaults.colors(containerColor = Color(0xFF1E2533)),
            modifier = modifier
                .width(width)
                .height(width * 9f / 16f)
                .onFocusChanged { focused = it.isFocused },
        ) {
            val bitmap = app.image
            if (bitmap != null && app.hasBanner) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(width / 4))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        app.label,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        // Name under the focused tile only, so the screen stays clean.
        Text(
            app.label,
            fontSize = 13.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = TextShadow),
            modifier = Modifier.padding(top = 10.dp).alpha(if (focused) 1f else 0f),
        )
    }
}

@Composable
private fun InputTile(input: InputEntry, width: Dp) {
    val context = LocalContext.current
    Card(
        onClick = {
            if (!Catalog.switchToInput(context, input.id)) toast(context, "This TV didn't accept the input switch.")
        },
        shape = CardDefaults.shape(TileShape),
        border = tileBorder(),
        colors = CardDefaults.colors(containerColor = Color(0xFF1E2533)),
        modifier = Modifier.width(width).height(56.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(input.label, fontSize = 15.sp, color = Color.White, maxLines = 1)
        }
    }
}

// ---------- Hold-OK menu ----------

@Composable
internal fun AppMenu(app: AppEntry, favorites: List<String>, onDone: (changed: Boolean) -> Unit) {
    val context = LocalContext.current
    val index = favorites.indexOf(app.packageName)
    val isFavorite = index >= 0
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun save(list: List<String>) {
        Prefs.setFavorites(context, list)
        onDone(true)
    }

    val actions = buildList<Pair<String, () -> Unit>> {
        if (isFavorite) {
            if (index > 0) add("Move left" to {
                save(favorites.toMutableList().apply { add(index - 1, removeAt(index)) })
            })
            if (index < favorites.size - 1) add("Move right" to {
                save(favorites.toMutableList().apply { add(index + 1, removeAt(index)) })
            })
            add("Remove from top row" to { save(favorites - app.packageName) })
        } else {
            add("Add to top row" to {
                if (favorites.size >= Prefs.FAVORITE_SLOTS) {
                    toast(context, "The top row holds ${Prefs.FAVORITE_SLOTS} apps. Remove one first.")
                    onDone(false)
                } else {
                    save(favorites + app.packageName)
                }
            })
        }
        add("Hide app" to {
            Prefs.setHidden(context, Prefs.hidden(context) + app.packageName)
            if (isFavorite) Prefs.setFavorites(context, favorites - app.packageName)
            toast(context, "${app.label} hidden. Show it again from Settings.")
            onDone(true)
        })
        add("Cancel" to { onDone(false) })
    }

    // The menu opens while OK is still held down. When it's let go, that release would
    // press the first option, so ignore button releases until a new press starts here.
    var armed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = { onDone(false) }) {
        Column(
            Modifier
                .onPreviewKeyEvent { event ->
                    when {
                        armed -> false
                        // Fresh press (not the held button repeating): menu is live from here on.
                        event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0 -> {
                            armed = true
                            false
                        }
                        else -> true // repeats and the release of the held button: swallow
                    }
                }
                .background(Color(0xFF1A202B), RoundedCornerShape(16.dp)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(app.label, fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
            actions.forEachIndexed { i, (label, action) ->
                Button(
                    onClick = action,
                    modifier = Modifier.width(260.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) { Text(label) }
            }
        }
    }
}
