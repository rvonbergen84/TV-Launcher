package io.github.rvonbergen84.tvlauncher

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Calendar

/**
 * Renders the real home screen with sample data at the TV's size (1920x1080),
 * so the design can be checked without installing it. Images go to app/build/previews/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
class HomePreviewTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var listState: LazyListState

    /** Pretend to be a TV: Compose scrolls differently on TVs, and that's what we ship to. */
    @Before
    fun actLikeTv() {
        shadowOf(RuntimeEnvironment.getApplication().packageManager)
            .setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
    }

    @Test
    fun home() {
        render()
        focus("Netflix")
        assertAtTop()
        capture("home")
    }

    @Test
    fun upFromTopRowReachesSettings() {
        render()
        focus("Netflix")
        press(Key.DirectionUp)
        compose.onNodeWithContentDescription("Settings").assertIsFocused()
        assertAtTop()
        capture("settings")
    }

    @Test
    fun downFromSettingsReturnsToTopRow() {
        render()
        focus("YouTube")
        press(Key.DirectionUp)
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("YouTube").assertIsFocused()
        assertAtTop()
    }

    @Test
    fun secondRowNeedsNoScrolling() {
        render()
        focus("Netflix")
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Hulu").assertIsFocused()
        assertAtTop()
        press(Key.DirectionRight, times = 4)
        compose.onNodeWithContentDescription("Xbox").assertIsFocused()
        assertAtTop()
        capture("xbox")
        press(Key.DirectionUp)
        assertAtTop()
    }

    @Test
    fun repeatedDownAndUpEndsAtTop() {
        render(extraApps = true)
        focus("Netflix")
        for (gap in listOf(400L, 120L, 50L)) {
            repeat(3) { pressQuick(Key.DirectionDown, gap) }
            repeat(3) { pressQuick(Key.DirectionUp, gap) }
        }
        settle()
        assertAtTop()
    }

    @Test
    fun extraAppsGoInMore() {
        render(extraApps = true)
        focus("Netflix")
        press(Key.DirectionDown)
        press(Key.DirectionDown)
        compose.onNodeWithContentDescription("Spotify").assertIsFocused()
        compose.runOnIdle { assertTrue("More should scroll up", listState.firstVisibleItemIndex > 0) }
        capture("more")
    }

    /** A press at remote speed: only a few frames pass before the next one. */
    @OptIn(ExperimentalTestApi::class)
    private fun pressQuick(key: Key, gapMs: Long) {
        compose.onRoot().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(gapMs)
    }

    private fun focus(app: String) {
        compose.onNodeWithContentDescription(app).requestFocus()
        settle()
    }

    @OptIn(ExperimentalTestApi::class)
    private fun press(key: Key, times: Int = 1) = repeat(times) {
        compose.onRoot().performKeyInput { pressKey(key) }
        settle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    private fun assertAtTop() {
        compose.runOnIdle {
            assertEquals("list should not scroll while on the top row", 0, listState.firstVisibleItemIndex)
            assertEquals("list should not scroll while on the top row", 0, listState.firstVisibleItemScrollOffset)
        }
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/previews/$name.png")

    private fun render(extraApps: Boolean = false) {
        val tiles = sampleTiles(extraApps)
        val layout = Catalog.Layout(
            top = tiles.take(5),
            second = tiles.drop(5).take(6),
            more = tiles.drop(11),
            hidden = emptyList(),
        )
        listState = LazyListState()
        val photo = javaClass.getResourceAsStream("/preview/landscape.jpg")!!.use { BitmapFactory.decodeStream(it) }
        val image = photo.asImageBitmap()
        val frame = Frame(image, Photos.Photo("", "Sunset over the range", "Sample image"), softBlur(image))
        val now = Calendar.getInstance().apply { set(2026, 10, 6, 19, 42) }.time

        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Box(Modifier.fillMaxSize().background(Background)) {
                    HomeContent(
                        layout = layout,
                        serviceOn = true,
                        frame = frame,
                        now = now,
                        weather = Weather.Now(temperature = 54, code = 1, isDay = true),
                        place = Prefs.Place("Denver, Colorado", 39.7, -105.0),
                        listState = listState,
                        firstFavorite = FocusRequester(),
                        onOpenSetup = {},
                        onTileMenu = {},
                    )
                }
            }
        }
        settle()
    }

    /** The household's 9 apps and 2 consoles, in the default order. Banners are stand-ins. */
    private fun sampleTiles(extraApps: Boolean): List<Tile> {
        val apps = listOf(
            "Netflix" to 0xFF141414, "YouTube" to 0xFFCC0000, "HBO Max" to 0xFF002BE7,
            "Prime Video" to 0xFF0F79AF, "Plex" to 0xFF1F1F1F, "Hulu" to 0xFF1CE783,
            "Peacock" to 0xFF000000, "Apple TV" to 0xFF000000, "Paramount+" to 0xFF0064FF,
        ) + if (extraApps) listOf("Spotify" to 0xFF1DB954, "Tubi" to 0xFF7408FF, "PBS" to 0xFF2638C4) else emptyList()
        val appTiles = apps.map { (label, color) ->
            Tile("sample.${label.lowercase().replace(Regex("[^a-z]"), "")}", label, banner(label, color.toInt()), true)
        }
        val consoles = listOf(
            Tile("input:hdmi4", "Xbox", inputId = "hdmi4", detail = "HDMI 4"),
            Tile("input:hdmi1", "Switch", inputId = "hdmi1", detail = "HDMI 1"),
        )
        // Default order puts the 9 apps first, then the consoles; extras land in "More".
        return appTiles.take(9) + consoles + appTiles.drop(9)
    }

    private fun banner(label: String, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (label == "Hulu") 0xFF0B0C0F.toInt() else 0xFFFFFFFF.toInt()
            textSize = if (label.length > 9) 34f else 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        c.drawText(label, 160f, 90f - (paint.descent() + paint.ascent()) / 2, paint)
        return bmp
    }

    private fun icon(): Bitmap {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawCircle(64f, 64f, 56f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5B8DEF.toInt() })
        return bmp
    }
}
