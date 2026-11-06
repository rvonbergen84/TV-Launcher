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
    private const val KEY_PLACE_NAME = "place_name"
    private const val KEY_PLACE_LAT = "place_lat"
    private const val KEY_PLACE_LON = "place_lon"
    private const val KEY_FAHRENHEIT = "fahrenheit"
    private const val KEY_PHOTO_SOURCE = "photo_source"
    private const val KEY_PHOTO_SECONDS = "photo_seconds"
    private const val MAX_LOG_LINES = 40

    const val FAVORITE_SLOTS = 5

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

    /** Ordered package names for the top row, or null if the user hasn't picked any yet. */
    fun favorites(context: Context): List<String>? =
        prefs(context).getString(KEY_FAVORITES, null)?.split(',')?.filter { it.isNotBlank() }
    fun setFavorites(context: Context, packages: List<String>) =
        prefs(context).edit().putString(KEY_FAVORITES, packages.joinToString(",")).apply()

    fun resetFavorites(context: Context) = prefs(context).edit().remove(KEY_FAVORITES).apply()

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

    fun photoSource(context: Context): Photos.Source =
        runCatching { Photos.Source.valueOf(prefs(context).getString(KEY_PHOTO_SOURCE, "")!!) }
            .getOrDefault(Photos.Source.BOTH)
    fun setPhotoSource(context: Context, source: Photos.Source) =
        prefs(context).edit().putString(KEY_PHOTO_SOURCE, source.name).apply()

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
