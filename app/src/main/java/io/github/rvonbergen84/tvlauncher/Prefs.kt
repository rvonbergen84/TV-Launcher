package io.github.rvonbergen84.tvlauncher

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small store for the launcher's settings and a short diagnostic log.
 * The log shows on the Setup screen so we can see what the TV actually
 * sends when Home is pressed.
 */
object Prefs {
    private const val FILE = "tvlauncher"
    private const val KEY_LOG = "log"
    private const val KEY_CATCH_KEY = "catch_home_key"
    private const val KEY_REDIRECT = "redirect_stock_home"
    private const val KEY_BYPASS_UNTIL = "bypass_until"
    private const val KEY_FAVORITES = "favorites"
    private const val KEY_HIDDEN = "hidden"
    private const val KEY_ORDER = "order"
    private const val KEY_PLACE_NAME = "place_name"
    private const val KEY_PLACE_LAT = "place_lat"
    private const val KEY_PLACE_LON = "place_lon"
    private const val KEY_FAHRENHEIT = "fahrenheit"
    private const val KEY_PHOTO_THEMES = "photo_themes"
    private const val KEY_PHOTO_SECONDS = "photo_seconds"
    private const val MAX_LOG_LINES = 40

    const val FAVORITE_SLOTS = 5
    const val SECOND_ROW_SLOTS = 6

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // --- Home button ---

    /** Method 1: swallow the Home key press and open our launcher. */
    fun catchHomeKey(context: Context) = prefs(context).getBoolean(KEY_CATCH_KEY, true)
    fun setCatchHomeKey(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_CATCH_KEY, on).apply()

    /** Method 2: when the stock home screen appears, replace it with ours. */
    fun redirectStockHome(context: Context) = prefs(context).getBoolean(KEY_REDIRECT, true)
    fun setRedirectStockHome(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_REDIRECT, on).apply()

    /** Lets the user open the stock home screen for a short time without being redirected. */
    fun bypassActive(context: Context) =
        System.currentTimeMillis() < prefs(context).getLong(KEY_BYPASS_UNTIL, 0)
    fun startBypass(context: Context, millis: Long) =
        prefs(context).edit().putLong(KEY_BYPASS_UNTIL, System.currentTimeMillis() + millis).apply()

    // --- Apps ---

    /** Favourites list from before 0.10; read once to carry the user's top row over. */
    fun favorites(context: Context): List<String>? =
        prefs(context).getString(KEY_FAVORITES, null)?.split(',')?.filter { it.isNotBlank() }

    /** Order of everything on the home screen (apps and inputs), or null if never arranged. */
    fun order(context: Context): List<String>? =
        prefs(context).getString(KEY_ORDER, null)?.split(',')?.filter { it.isNotBlank() }
    fun setOrder(context: Context, keys: List<String>) =
        prefs(context).edit().putString(KEY_ORDER, keys.joinToString(",")).apply()
    fun resetOrder(context: Context) =
        prefs(context).edit().remove(KEY_ORDER).remove(KEY_FAVORITES).apply()

    /** The user's own name for a tile, e.g. "Xbox" for HDMI 4. */
    fun tileName(context: Context, key: String): String? =
        prefs(context).getString("name:$key", null)?.takeIf { it.isNotBlank() }
    fun setTileName(context: Context, key: String, name: String) =
        prefs(context).edit().putString("name:$key", name.trim()).apply()

    /** Chosen colour for an input tile (index into [TileColors.palette]), or null for the default. */
    fun tileColor(context: Context, key: String): Int? =
        prefs(context).getInt("color:$key", -1).takeIf { it >= 0 }
    fun setTileColor(context: Context, key: String, index: Int) =
        prefs(context).edit().putInt("color:$key", index).apply()

    fun hidden(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet())!!.toSet()
    fun setHidden(context: Context, packages: Set<String>) =
        prefs(context).edit().putStringSet(KEY_HIDDEN, packages).apply()

    // --- Weather ---

    data class Place(val name: String, val lat: Double, val lon: Double)

    fun place(context: Context): Place? {
        val p = prefs(context)
        val name = p.getString(KEY_PLACE_NAME, null) ?: return null
        return Place(name, p.getFloat(KEY_PLACE_LAT, 0f).toDouble(), p.getFloat(KEY_PLACE_LON, 0f).toDouble())
    }
    fun setPlace(context: Context, place: Place) = prefs(context).edit()
        .putString(KEY_PLACE_NAME, place.name)
        .putFloat(KEY_PLACE_LAT, place.lat.toFloat())
        .putFloat(KEY_PLACE_LON, place.lon.toFloat())
        .apply()

    /** Defaults to Fahrenheit in the US and a few other places, Celsius elsewhere. */
    fun fahrenheit(context: Context) = prefs(context).getBoolean(
        KEY_FAHRENHEIT,
        Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW"),
    )
    fun setFahrenheit(context: Context, on: Boolean) =
        prefs(context).edit().putBoolean(KEY_FAHRENHEIT, on).apply()

    // --- Photos ---

    /** Which picture themes are on. All of them by default. */
    fun photoThemes(context: Context): Set<Photos.Theme> {
        val saved = prefs(context).getStringSet(KEY_PHOTO_THEMES, null) ?: return Photos.Theme.entries.toSet()
        return saved.mapNotNull { name -> Photos.Theme.entries.firstOrNull { it.name == name } }.toSet()
    }
    fun setPhotoThemes(context: Context, themes: Set<Photos.Theme>) =
        prefs(context).edit().putStringSet(KEY_PHOTO_THEMES, themes.map { it.name }.toSet()).apply()

    fun photoSeconds(context: Context) = prefs(context).getInt(KEY_PHOTO_SECONDS, 30)
    fun setPhotoSeconds(context: Context, seconds: Int) =
        prefs(context).edit().putInt(KEY_PHOTO_SECONDS, seconds).apply()

    // --- Diagnostic log ---

    fun log(context: Context, line: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val p = prefs(context)
        val lines = p.getString(KEY_LOG, "")!!.lines().filter { it.isNotBlank() }
        val updated = (listOf("$time  $line") + lines).take(MAX_LOG_LINES)
        p.edit().putString(KEY_LOG, updated.joinToString("\n")).apply()
    }

    fun readLog(context: Context): List<String> =
        prefs(context).getString(KEY_LOG, "")!!.lines().filter { it.isNotBlank() }

    fun clearLog(context: Context) = prefs(context).edit().remove(KEY_LOG).apply()
}
