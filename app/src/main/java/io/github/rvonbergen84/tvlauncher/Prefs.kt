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
    private const val MAX_LOG_LINES = 40

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

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
