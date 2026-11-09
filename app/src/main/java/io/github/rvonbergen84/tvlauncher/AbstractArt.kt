package io.github.rvonbergen84.tvlauncher

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Abstract backgrounds drawn on the TV itself. Each seed gives a different piece,
 * so there's always something new, nothing to download, and nothing unsuitable.
 */
object AbstractArt {

    const val URL_PREFIX = "abstract:"

    private class Palette(val name: String, val colors: List<Int>)

    private fun pal(name: String, vararg colors: Long) = Palette(name, colors.map { it.toInt() })

    private val palettes = listOf(
        pal("Dusk", 0xFF1B1F3B, 0xFF53354A, 0xFFA64942, 0xFFFF7844, 0xFFFFC07F),
        pal("Ocean", 0xFF03122B, 0xFF0B3C5D, 0xFF1D7A8C, 0xFF52B5B5, 0xFFB8E1DD),
        pal("Aurora", 0xFF07131F, 0xFF0F3B3A, 0xFF1F8A70, 0xFF7ED957, 0xFF8E7DFF),
        pal("Forest", 0xFF0E1A12, 0xFF234D20, 0xFF4F7942, 0xFF9DBF9E, 0xFFE3D7A3),
        pal("Desert", 0xFF2B1B17, 0xFF7A4A2A, 0xFFC0703F, 0xFFE8A85C, 0xFFF5DFB8),
        pal("Berry", 0xFF1A0F24, 0xFF4B1D52, 0xFF8E2C6B, 0xFFD6577A, 0xFFF4B6C2),
        pal("Glacier", 0xFF0B1320, 0xFF22385A, 0xFF4B78A6, 0xFF9CC3E4, 0xFFEAF4FB),
        pal("Citrus", 0xFF12241A, 0xFF2F5D3A, 0xFFE0A526, 0xFFF26B38, 0xFFF9E79F),
    )

    private val styles = listOf("Glow", "Ribbons", "Dunes", "Circles")

    /** A new list entry. The seed in the URL decides the piece. */
    fun entry(seed: Int): Photos.Photo {
        val r = Random(seed)
        val style = styles[r.nextInt(styles.size)]
        val palette = palettes[r.nextInt(palettes.size)]
        return Photos.Photo("$URL_PREFIX$seed", "$style in ${palette.name.lowercase()}", "Abstract, made on this TV")
    }

    fun isAbstract(url: String) = url.startsWith(URL_PREFIX)

    /** Draws the piece for [url] at the given size. Takes a few milliseconds; call off the main thread. */
    fun render(url: String, width: Int, height: Int): Bitmap {
        val seed = url.removePrefix(URL_PREFIX).toIntOrNull() ?: 0
        val r = Random(seed)
        val style = styles[r.nextInt(styles.size)]
        // Palettes run dark to light: the dark end is the background, so tiles and clock stay readable.
        val palette = palettes[r.nextInt(palettes.size)].colors.also { r.nextBoolean() }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val w = width.toFloat()
        val h = height.toFloat()

        // Base: a soft diagonal wash between the darkest and a mid colour.
        val base = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        base.shader = LinearGradient(0f, 0f, w * r.nextFloat(), h, palette[0], palette[1], Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, base)

        when (style) {
            "Glow" -> glow(c, w, h, palette, r)
            "Ribbons" -> ribbons(c, w, h, palette, r)
            "Dunes" -> dunes(c, w, h, palette, r)
            else -> circles(c, w, h, palette, r)
        }
        return bmp
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)

    private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

    /** Large soft lights blending into each other. */
    private fun glow(c: Canvas, w: Float, h: Float, pal: List<Int>, r: Random) {
        repeat(6 + r.nextInt(4)) {
            val color = pal[1 + r.nextInt(pal.size - 1)]
            val cx = w * r.nextFloat()
            val cy = h * (0.1f + 0.9f * r.nextFloat())
            val radius = w * (0.25f + 0.35f * r.nextFloat())
            val p = paint()
            p.shader = RadialGradient(cx, cy, radius, withAlpha(color, 150), withAlpha(color, 0), Shader.TileMode.CLAMP)
            if (Build.VERSION.SDK_INT >= 29) p.blendMode = BlendMode.SCREEN
            else p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
            c.drawCircle(cx, cy, radius, p)
        }
    }

    /** Translucent bands flowing across the screen. */
    private fun ribbons(c: Canvas, w: Float, h: Float, pal: List<Int>, r: Random) {
        repeat(5 + r.nextInt(4)) { i ->
            val top = wave(w, h, r, h * (0.15f + 0.7f * r.nextFloat()))
            val thickness = h * (0.06f + 0.14f * r.nextFloat())
            val path = Path().apply {
                moveTo(0f, top(0f))
                var x = 0f
                while (x <= w) { lineTo(x, top(x)); x += w / 120f }
                x = w
                while (x >= 0f) { lineTo(x, top(x) + thickness); x -= w / 120f }
                close()
            }
            val a = pal[1 + (i + r.nextInt(pal.size - 1)) % (pal.size - 1)]
            val b = pal[1 + r.nextInt(pal.size - 1)]
            val p = paint()
            p.shader = LinearGradient(0f, 0f, w, 0f, withAlpha(a, 170), withAlpha(b, 90), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
    }

    /** Smooth stacked hills, lighter in the distance. */
    private fun dunes(c: Canvas, w: Float, h: Float, pal: List<Int>, r: Random) {
        val layers = 5
        for (i in 0 until layers) {
            val crest = wave(w, h, r, h * (0.35f + 0.12f * i))
            val path = Path().apply {
                moveTo(0f, h)
                var x = 0f
                while (x <= w) { lineTo(x, crest(x)); x += w / 160f }
                lineTo(w, h)
                close()
            }
            val color = pal[pal.size - 1 - (i % (pal.size - 1))]
            val p = paint()
            p.shader = LinearGradient(0f, h * 0.3f, 0f, h, withAlpha(color, 235), withAlpha(pal[0], 255), Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
    }

    /** Overlapping translucent discs. */
    private fun circles(c: Canvas, w: Float, h: Float, pal: List<Int>, r: Random) {
        repeat(14 + r.nextInt(10)) {
            val color = pal[1 + r.nextInt(pal.size - 1)]
            val radius = h * (0.06f + 0.4f * r.nextFloat() * r.nextFloat())
            val cx = w * r.nextFloat()
            val cy = h * r.nextFloat()
            val p = paint()
            p.shader = RadialGradient(cx, cy, radius, withAlpha(color, 140), withAlpha(color, 60), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, radius, p)
        }
    }

    /** A gentle random curve across the screen, as y for a given x. */
    private fun wave(w: Float, h: Float, r: Random, baseline: Float): (Float) -> Float {
        val parts = List(3) { k -> Triple(h * (0.02f + 0.06f * r.nextFloat()) / (k + 1), (k + 1) * (0.6f + r.nextFloat()), r.nextFloat() * 6.28f) }
        return { x -> baseline + parts.sumOf { (amp, freq, phase) -> (amp * sin(x / w * 2 * PI * freq + phase)).toDouble() }.toFloat() }
    }
}
