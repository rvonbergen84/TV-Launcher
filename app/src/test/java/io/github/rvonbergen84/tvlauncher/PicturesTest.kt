package io.github.rvonbergen84.tvlauncher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PicturesTest {

    @Test
    fun kidSafeFilter() {
        // Kept
        listOf(
            "File:Aerial view of the Grand Canyon.jpg",
            "Earth from the International Space Station at night",
            "Red fox (Vulpes vulpes) in snow",
            "Bald eagle in flight over a lake",
        ).forEach { assertTrue(it, Photos.suitableForKids(it)) }
        // Skipped
        listOf(
            "Reclining nude, oil on canvas",
            "Lioness with prey on the savanna",
            "Skull of a brown bear",
            "Category:Dead animals",
            "Heron eating a fish",
        ).forEach { assertFalse(it, Photos.suitableForKids(it)) }
    }

    /** Renders a sheet of generated pieces to app/build/previews/abstract-sheet.png. */
    @Test
    fun abstractSheet() {
        val seeds = listOf(11, 23, 37, 41, 58, 64, 79, 85, 97)
        val w = 640
        val h = 360
        val sheet = Bitmap.createBitmap(w * 3, h * 3, Bitmap.Config.ARGB_8888)
        val c = Canvas(sheet)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 22f; setShadowLayer(4f, 0f, 1f, 0xFF000000.toInt()) }
        val pieces = seeds.map { AbstractArt.entry(it) }
        pieces.forEachIndexed { i, entry ->
            val art = AbstractArt.render(entry.url, w, h)
            c.drawBitmap(art, (i % 3) * w.toFloat(), (i / 3) * h.toFloat(), null)
            c.drawText(entry.title, (i % 3) * w + 16f, (i / 3) * h + h - 18f, label)
        }
        // Different seeds should give different pictures.
        assertNotEquals(
            AbstractArt.render(pieces[0].url, 64, 36).getPixel(32, 18),
            AbstractArt.render(pieces[1].url, 64, 36).getPixel(32, 18),
        )
        File("build/previews").mkdirs()
        File("build/previews/abstract-sheet.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
