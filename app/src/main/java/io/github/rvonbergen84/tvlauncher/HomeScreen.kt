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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
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
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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
    var inputs by remember { mutableStateOf(Catalog.lastInputs) }
    var editTick by remember { mutableIntStateOf(0) }
    val layout = remember(apps, inputs, editTick) { Catalog.arrange(context, apps, inputs) }
    var serviceOn by remember { mutableStateOf(true) }
    var menuFor by remember { mutableStateOf<Tile?>(null) }
    var renaming by remember { mutableStateOf<Tile?>(null) }
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
        serviceOn = serviceOn,
        frame = frame,
        now = now,
        weather = weather,
        place = place,
        listState = listState,
        firstFavorite = firstFavorite,
        onOpenSetup = onOpenSetup,
        onTileMenu = { menuFor = it },
    )

    menuFor?.let { tile ->
        TileMenu(
            tile = tile,
            order = layout.visible.map { it.key },
            hiddenKeys = layout.hidden.map { it.key },
            onRename = {
                menuFor = null
                renaming = tile
            },
            onRecolor = { editTick++ },
            onDone = { changed ->
                menuFor = null
                if (changed) editTick++
            },
        )
    }
    renaming?.let { tile ->
        RenameDialog(tile) { changed ->
            renaming = null
            if (changed) editTick++
        }
    }
}

/** Everything drawn on the home screen. No loading here, so it can be previewed with sample data. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContent(
    layout: Catalog.Layout,
    serviceOn: Boolean,
    frame: Frame?,
    now: Date,
    weather: Weather.Now?,
    place: Prefs.Place?,
    listState: LazyListState,
    firstFavorite: FocusRequester,
    onOpenSetup: () -> Unit,
    onTileMenu: (Tile) -> Unit,
) {
    LaunchedEffect(layout.top.isNotEmpty()) {
        if (layout.top.isNotEmpty()) runCatching { firstFavorite.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val topGap = 24.dp
        val rowGap = 20.dp
        val content = maxWidth - (SIDE * 2).dp
        val topWidth = (content - topGap * (Prefs.FAVORITE_SLOTS - 1)) / Prefs.FAVORITE_SLOTS
        val smallWidth = (content - rowGap * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        // Room under each row for the name of the selected tile.
        val labelSpace = 30.dp
        val rowsHeight = topWidth * 9f / 16f + labelSpace +
            (if (layout.second.isNotEmpty()) smallWidth * 9f / 16f + labelSpace else 0.dp)
        // The rows end at the bottom edge (the space under the second row doubles as
        // the margin), so the "More" area starts fully off screen.
        val heroHeight = maxHeight - rowsHeight

        // Both main rows sit on the photo with nothing scrolling. Only the "More" area,
        // for anything beyond 11 tiles, scrolls up and blurs the photo.
        var inMore by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val moreTitleIndex = 2 // after hero and rows
        val onRowsFocus = Modifier.onFocusChanged {
            if (it.hasFocus) {
                inMore = false
                scope.launch { listState.animateScrollToItem(0) }
            }
        }
        val onMoreFocus = Modifier.onFocusChanged {
            if (it.hasFocus && !inMore) {
                inMore = true
                scope.launch { listState.animateScrollToItem(moreTitleIndex) }
            }
        }
        // The TV's own "keep the selected tile in view" scroll can land after ours. While
        // the main rows have focus, any scroll that stops away from the top goes back.
        LaunchedEffect(inMore) {
            if (inMore) return@LaunchedEffect
            snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
                val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                if (!scrolling && !atTop) listState.animateScrollToItem(0)
            }
        }
        // Remember which top-row tile was last selected, so Down from the gear lands on it.
        val topFocus = remember(layout.top.size) { List(layout.top.size) { FocusRequester() } }
        var lastTop by remember { mutableIntStateOf(0) }

        // 0 on the main rows, 1 in "More". Drives the blur, dim and fades together.
        val moreAmount = animateFloatAsState(if (inMore) 1f else 0f, tween(400), label = "more")
        Backdrop(frame, moreAmount)

        // TVs default to scrolling the selected item a third of the way down the screen.
        // Only scroll as much as needed to keep it in view.
        CompositionLocalProvider(LocalBringIntoViewSpec provides KeepInView) {
            LazyColumn(
                state = listState,
                // Room at the end so "More" can scroll the main rows fully off screen.
                contentPadding = PaddingValues(
                    start = SIDE.dp,
                    end = SIDE.dp,
                    bottom = if (layout.more.isNotEmpty()) maxHeight / 2 else 0.dp,
                ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "hero") {
                    Box(Modifier.fillMaxWidth().height(heroHeight)) {
                        frame?.let {
                            Caption(
                                it.photo,
                                Modifier.align(Alignment.BottomEnd).padding(bottom = 14.dp)
                                    .graphicsLayer { alpha = 1f - moreAmount.value },
                            )
                        }
                    }
                }
                item(key = "rows") {
                    Column(onRowsFocus) {
                        Row(horizontalArrangement = Arrangement.spacedBy(topGap)) {
                            layout.top.forEachIndexed { index, tile ->
                                TileCard(
                                    tile = tile,
                                    width = topWidth,
                                    modifier = Modifier
                                        .then(if (index == 0) Modifier.focusRequester(firstFavorite) else Modifier)
                                        .focusRequester(topFocus[index])
                                        .onFocusChanged { if (it.isFocused) lastTop = index },
                                    onLongClick = { onTileMenu(tile) },
                                )
                            }
                        }
                        if (layout.second.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(rowGap)) {
                                layout.second.forEach { tile ->
                                    TileCard(tile, smallWidth, Modifier, onLongClick = { onTileMenu(tile) })
                                }
                            }
                        }
                    }
                }
                if (layout.more.isNotEmpty()) {
                    item(key = "more-title") { SectionTitle("More") }
                    items(layout.more.chunked(GRID_COLUMNS), key = { row -> row.first().key }) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(rowGap),
                            modifier = onMoreFocus.padding(bottom = 4.dp),
                        ) {
                            row.forEach { tile -> TileCard(tile, smallWidth, Modifier, onLongClick = { onTileMenu(tile) }) }
                        }
                    }
                }
            }
        }

        TopOverlay(
            now, weather, place, hidden = inMore, gridAmount = moreAmount, serviceOn = serviceOn,
            downTo = topFocus.getOrNull(lastTop) ?: firstFavorite,
            onOpenSetup = onOpenSetup,
        )
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

/** A photo ready to show, plus its blurred copy for behind the app grid. */
class Frame(val bitmap: ImageBitmap, val photo: Photos.Photo, val blurred: ImageBitmap)

/**
 * Smooth blur that works on every Android version (Compose's blur() needs Android 12+).
 * Shrinks the photo to 1/8 size, runs three box-blur passes on it (close to a Gaussian
 * blur), and lets the screen stretch the result back up. Takes a few milliseconds.
 */
internal fun softBlur(source: ImageBitmap): ImageBitmap {
    val src = source.asAndroidBitmap()
    val w = maxOf(1, src.width / 8)
    val h = maxOf(1, src.height / 8)
    val small = android.graphics.Bitmap.createScaledBitmap(src, w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    val tmp = IntArray(w * h)
    repeat(3) {
        boxBlur(px, tmp, w, h, radius = 3, horizontal = true)
        boxBlur(tmp, px, w, h, radius = 3, horizontal = false)
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
            // Blur it now, in the background, so moving into the grid doesn't stall.
            val blurred = withContext(Dispatchers.Default) { softBlur(bitmap) }
            frame = Frame(bitmap, photo, blurred)
            // Fetch the next one now so the switch is instant.
            prefetch(context, photos[index % photos.size].url)
            delay(Prefs.photoSeconds(context) * 1000L)
        }
        Prefs.log(context, "Photos: none of the images would load")
    }
    return frame
}

@Composable
private fun Backdrop(frame: Frame?, gridAmount: State<Float>) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = frame, animationSpec = tween(1500), label = "photo") { f ->
            if (f != null) {
                Box(Modifier.fillMaxSize()) {
                    Image(f.bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Image(
                        f.blurred,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = gridAmount.value },
                    )
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
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = GRID_DIM * gridAmount.value)) })
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
    if (AbstractArt.isAbstract(url)) {
        // Drawn at half the screen size: it's all soft shapes, so it scales up cleanly.
        val m = context.resources.displayMetrics
        val w = maxOf(m.widthPixels, m.heightPixels) / 2
        val h = minOf(m.widthPixels, m.heightPixels) / 2
        return withContext(Dispatchers.Default) { runCatching { AbstractArt.render(url, w, h).asImageBitmap() }.getOrNull() }
    }
    val result = context.imageLoader.execute(photoRequest(context, url))
    return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap?.asImageBitmap()
}

private fun prefetch(context: Context, url: String) {
    if (AbstractArt.isAbstract(url)) return // nothing to download
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
private fun TopOverlay(
    now: Date,
    weather: Weather.Now?,
    place: Prefs.Place?,
    hidden: Boolean,
    gridAmount: State<Float>,
    serviceOn: Boolean,
    downTo: FocusRequester,
    onOpenSetup: () -> Unit,
) {
    val context = LocalContext.current
    // Fades out while browsing the app grid, which scrolls up underneath it.
    Row(
        Modifier.fillMaxWidth()
            .graphicsLayer { alpha = 1f - gridAmount.value }
            .padding(start = SIDE.dp, end = SIDE.dp, top = 28.dp),
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
            if (!serviceOn) {
                Text(
                    "The Home button isn't connected. Open Settings (the gear) to turn it on.",
                    color = Warning,
                    fontSize = 14.sp,
                    style = TextStyle(shadow = TextShadow),
                    modifier = Modifier.padding(top = 8.dp),
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
            modifier = Modifier.focusProperties {
                canFocus = !hidden
                down = downTo
            },
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
private fun TileCard(tile: Tile, width: Dp, modifier: Modifier, onLongClick: () -> Unit) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(width)) {
        Card(
            onClick = {
                if (!Catalog.open(context, tile)) {
                    toast(context, if (tile.isInput) "This TV didn't accept the input switch." else "Couldn't open ${tile.label}.")
                }
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
            val bitmap = tile.image
            when {
                bitmap != null && tile.hasBanner -> Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = tile.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Inputs: the name big (e.g. "Xbox"), the port small underneath.
                tile.isInput -> Column(
                    Modifier
                        .fillMaxSize()
                        .background(
                            TileColors.palette[tile.colorIndex.coerceIn(TileColors.palette.indices)].let {
                                Brush.verticalGradient(listOf(Color(it.top), Color(it.bottom)))
                            },
                        )
                        .semantics { contentDescription = tile.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        tile.label,
                        fontSize = (width.value / 7f).coerceIn(14f, 22f).sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    tile.detail?.let {
                        Text(it, fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                    }
                }
                else -> Column(
                    Modifier.fillMaxSize().semantics { contentDescription = tile.label },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (bitmap != null) {
                        Image(bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.size(width / 4))
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        tile.label,
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
        // Name under the selected tile only, so the screen stays clean.
        Text(
            tile.label,
            fontSize = 13.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(shadow = TextShadow),
            modifier = Modifier.padding(top = 8.dp).alpha(if (focused) 1f else 0f),
        )
    }
}

// ---------- Hold-OK menu ----------

/**
 * Options for one tile. [order] is every visible tile in screen order: the first 5 are
 * the top row, the next 6 the second row, the rest "More".
 */
@Composable
internal fun TileMenu(
    tile: Tile,
    order: List<String>,
    hiddenKeys: List<String>,
    onRename: () -> Unit,
    onRecolor: () -> Unit = {},
    onDone: (changed: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val index = order.indexOf(tile.key)
    val top = Prefs.FAVORITE_SLOTS
    val endOfSecond = top + Prefs.SECOND_ROW_SLOTS
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    var colorIndex by remember { mutableIntStateOf(tile.colorIndex.coerceIn(TileColors.palette.indices)) }

    fun moveTo(target: Int) {
        val list = order.toMutableList()
        list.removeAt(index)
        list.add(target.coerceIn(0, list.size), tile.key)
        Prefs.setOrder(context, list + hiddenKeys)
        onDone(true)
    }

    val actions = buildList<Pair<String, () -> Unit>> {
        if (index > 0) add("Move left" to { moveTo(index - 1) })
        if (index < order.size - 1) add("Move right" to { moveTo(index + 1) })
        when {
            // Out of the top row: it becomes the first tile of the second row.
            index < top -> if (order.size > top) add("Move to second row" to { moveTo(top) })
            // Into the top row as its last tile; that row's last tile drops to the second row.
            else -> add("Move to top row" to { moveTo(top - 1) })
        }
        if (index >= endOfSecond) add("Move to second row" to { moveTo(endOfSecond - 1) })
        if (tile.isInput) {
            add("Rename" to onRename)
            add("Color: ${TileColors.palette[colorIndex].name}" to {
                colorIndex = (colorIndex + 1) % TileColors.palette.size
                Prefs.setTileColor(context, tile.key, colorIndex)
                onRecolor()
            })
        }
        add("Hide" to {
            Prefs.setHidden(context, Prefs.hidden(context) + tile.key)
            toast(context, "${tile.label} hidden. Show it again from Settings.")
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
            Text(tile.label, fontSize = 20.sp, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
            actions.forEachIndexed { i, (label, action) ->
                Button(
                    onClick = action,
                    modifier = Modifier.width(260.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                ) { Text(label) }
            }
        }
    }
}

/** Lets the user name an input, e.g. HDMI 4 -> "Xbox". Empty restores the TV's name. */
@Composable
private fun RenameDialog(tile: Tile, onDone: (changed: Boolean) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(tile.label) }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }

    fun save() {
        Prefs.setTileName(context, tile.key, text)
        onDone(true)
    }

    Dialog(onDismissRequest = { onDone(false) }) {
        Column(
            Modifier.background(Color(0xFF1A202B), RoundedCornerShape(16.dp)).padding(24.dp).width(420.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Rename ${tile.detail ?: tile.label}", fontSize = 20.sp, color = Color.White)
            Text("For example Xbox or Switch. Leave empty to use the TV's name.", fontSize = 14.sp, color = Muted)
            BasicTextField(
                value = text,
                onValueChange = { text = it.take(24) },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier
                    .focusRequester(field)
                    .border(1.dp, Muted, RoundedCornerShape(8.dp))
                    .padding(12.dp)
                    .width(372.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { save() }) { Text("Save") }
                Button(onClick = { onDone(false) }) { Text("Cancel") }
            }
        }
    }
}
