package io.github.rvonbergen84.tvlauncher

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.DisplayMetrics
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val packageName: String,
    val label: String,
    /** Wide 16:9 TV banner if the app has one, otherwise its square icon. */
    val image: Bitmap?,
    val hasBanner: Boolean,
)

data class InputEntry(val id: String, val label: String)

/** Something on the home screen: an app, or a TV input such as a game console. */
data class Tile(
    /** Package name for apps, "input:<id>" for inputs. Used to save the order. */
    val key: String,
    val label: String,
    val image: Bitmap? = null,
    val hasBanner: Boolean = false,
    /** Set for inputs. */
    val inputId: String? = null,
    /** Small second line, e.g. "HDMI 4" under an input renamed to "Xbox". */
    val detail: String? = null,
) {
    val isInput: Boolean get() = inputId != null
}

/** Reads installed TV apps and HDMI inputs from the system. Call off the main thread. */
object Catalog {

    /** Last loaded lists, so the home screen can draw instantly when reopened. */
    @Volatile
    var lastApps: List<AppEntry> = emptyList()
        private set
    @Volatile
    var lastInputs: List<InputEntry> = emptyList()
        private set

    /** Starting order before the user arranges anything. First installed ones win. */
    private val DEFAULT_ORDER = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "com.wbd.stream", // HBO Max
        "com.amazon.amazonvideo.livingroom",
        "com.plexapp.android",
        "com.hulu.livingroomplus",
        "com.peacocktv.peacockandroid",
        "com.apple.atve.androidtv.appletv",
        "com.cbs.ott", // Paramount+
        "com.disney.disneyplus",
        "com.google.android.youtube.tvunplugged",
        "com.spotify.tv.android",
    )

    fun inputKey(id: String) = "input:$id"

    /** Top row, second row, anything beyond those, and hidden tiles. */
    data class Layout(val top: List<Tile>, val second: List<Tile>, val more: List<Tile>, val hidden: List<Tile>) {
        val visible: List<Tile> get() = top + second + more
    }

    fun tiles(context: Context, apps: List<AppEntry>, inputs: List<InputEntry>): List<Tile> =
        apps.map { Tile(it.packageName, it.label, it.image, it.hasBanner) } +
            inputs.map { input ->
                val key = inputKey(input.id)
                val name = Prefs.tileName(context, key)
                Tile(key, name ?: input.label, inputId = input.id, detail = if (name != null) input.label else null)
            }

    fun arrange(context: Context, apps: List<AppEntry>, inputs: List<InputEntry>): Layout {
        val all = tiles(context, apps, inputs)
        val byKey = all.associateBy { it.key }
        val hidden = Prefs.hidden(context)
        // Saved order first; anything new (a freshly installed app) goes at the end.
        val saved = Prefs.order(context) ?: startingOrder(context, all)
        val keys = (saved + all.map { it.key }).distinct().filter { it in byKey }
        val visible = keys.filter { it !in hidden }.mapNotNull { byKey[it] }
        val top = Prefs.FAVORITE_SLOTS
        val second = Prefs.SECOND_ROW_SLOTS
        return Layout(
            top = visible.take(top),
            second = visible.drop(top).take(second),
            more = visible.drop(top + second),
            hidden = keys.filter { it in hidden }.mapNotNull { byKey[it] },
        )
    }

    /** First run, or right after updating from the version with a separate favourites list. */
    private fun startingOrder(context: Context, all: List<Tile>): List<String> {
        val keys = all.map { it.key }.toSet()
        val earlierFavorites = Prefs.favorites(context).orEmpty()
        return (earlierFavorites + DEFAULT_ORDER.filter { it in keys } +
            all.filter { !it.isInput }.map { it.key } + all.filter { it.isInput }.map { it.key }).distinct()
    }

    fun open(context: Context, tile: Tile): Boolean =
        if (tile.inputId != null) switchToInput(context, tile.inputId) else launchApp(context, tile.key)

    fun loadApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val leanback = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0,
        )
        // Phone-style apps that never declared TV support still show up, after the TV apps.
        val phone = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0,
        )
        val seen = mutableSetOf(context.packageName)
        return (leanback + phone)
            .filter { seen.add(it.activityInfo.packageName) }
            .map { toEntry(pm, it) }
            .sortedBy { it.label.lowercase() }
            .also { lastApps = it }
    }

    private fun toEntry(pm: PackageManager, info: ResolveInfo): AppEntry {
        val activity = info.activityInfo
        val app = activity.applicationInfo
        // Ask for the sharpest version the app ships (xxxhdpi). The default load gives
        // the TV's own density, which is often a small 320x180 banner.
        val banner = hiRes(pm, app, activity.banner) ?: hiRes(pm, app, app.banner)
            ?: activity.loadBanner(pm) ?: app.loadBanner(pm)
        val drawable = banner ?: hiRes(pm, app, activity.iconResource) ?: activity.loadIcon(pm)
        val bitmap = runCatching {
            if (banner != null) drawable.toBitmap(BANNER_W, BANNER_H) else drawable.toBitmap(ICON_PX, ICON_PX)
        }.getOrNull()
        return AppEntry(
            packageName = activity.packageName,
            label = info.loadLabel(pm).toString(),
            image = bitmap,
            hasBanner = banner != null,
        )
    }

    // About 1.6x the largest tile on a 1080p screen, so focus zoom stays sharp.
    private const val BANNER_W = 480
    private const val BANNER_H = 270
    private const val ICON_PX = 192

    private fun hiRes(pm: PackageManager, app: ApplicationInfo, resId: Int): Drawable? {
        if (resId == 0) return null
        return runCatching {
            pm.getResourcesForApplication(app).getDrawableForDensity(resId, DisplayMetrics.DENSITY_XXXHIGH, null)
        }.getOrNull()
    }

    fun launchApp(context: Context, packageName: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
            ?: pm.getLaunchIntentForPackage(packageName)
            ?: return false
        return runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** HDMI and other pass-through inputs, using the names set in the TV's settings. */
    fun loadInputs(context: Context): List<InputEntry> {
        val tim = context.getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager
            ?: return emptyList()
        return runCatching {
            tim.tvInputList
                .filter { it.isPassthroughInput && !it.isHidden(context) }
                .map { InputEntry(it.id, labelFor(context, it)) }
                .sortedBy { it.label }
        }.getOrDefault(emptyList()).also { lastInputs = it }
    }

    private fun labelFor(context: Context, info: TvInputInfo): String =
        (info.loadCustomLabel(context) ?: info.loadLabel(context)).toString()

    /** Asks the TV's own Live TV app to switch to an input. */
    fun switchToInput(context: Context, inputId: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun openSystemSettings(context: Context) = tryStart(context, Intent(Settings.ACTION_SETTINGS))

    fun openAccessibilitySettings(context: Context) =
        tryStart(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    /** Opens the stock home screen, with redirect paused so it actually stays open. */
    fun openStockHome(context: Context): Boolean {
        Prefs.startBypass(context, 60_000)
        val pm = context.packageManager
        val homes = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        ).map { it.activityInfo }.filter { it.packageName != context.packageName }
        val stock = homes.firstOrNull() ?: return false
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setClassName(stock.packageName, stock.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return tryStart(context, intent)
    }

    /** Which app the system currently opens for Home. */
    fun currentHomePackage(context: Context): String? =
        context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName

    private fun tryStart(context: Context, intent: Intent) =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
}
