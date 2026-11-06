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

/** Reads installed TV apps and HDMI inputs from the system. Call off the main thread. */
object Catalog {

    /** Last loaded list, so the home screen can draw instantly when reopened. */
    @Volatile
    var lastApps: List<AppEntry> = emptyList()
        private set

    /** Used to fill the top row until the user picks favourites. First installed ones win. */
    private val DEFAULT_FAVORITES = listOf(
        "com.google.android.youtube.tv",
        "com.netflix.ninja",
        "com.amazon.amazonvideo.livingroom",
        "com.disney.disneyplus",
        "com.wbd.stream",
        "com.hulu.livingroomplus",
        "com.apple.atve.androidtv.appletv",
        "com.google.android.youtube.tvunplugged",
        "com.plexapp.android",
        "com.spotify.tv.android",
    )

    data class Layout(val favorites: List<AppEntry>, val others: List<AppEntry>, val hidden: List<AppEntry>)

    /** Splits apps into the top row, the grid below it, and hidden apps. */
    fun arrange(context: Context, apps: List<AppEntry>): Layout {
        val byPackage = apps.associateBy { it.packageName }
        val hidden = Prefs.hidden(context)
        val chosen = Prefs.favorites(context)
            ?: (DEFAULT_FAVORITES.filter { it in byPackage } + apps.map { it.packageName })
                .distinct().take(Prefs.FAVORITE_SLOTS)
        val favorites = chosen.mapNotNull { byPackage[it] }.filter { it.packageName !in hidden }
        val favSet = favorites.map { it.packageName }.toSet()
        return Layout(
            favorites = favorites,
            others = apps.filter { it.packageName !in favSet && it.packageName !in hidden },
            hidden = apps.filter { it.packageName in hidden },
        )
    }

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
        }.getOrDefault(emptyList())
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
