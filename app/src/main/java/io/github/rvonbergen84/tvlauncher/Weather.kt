package io.github.rvonbergen84.tvlauncher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Shared HTTP GET with a polite User-Agent (Wikimedia requires one). */
object Http {
    private const val USER_AGENT =
        "TVLauncher/1 (personal TV home screen; https://github.com/rvonbergen84/tv-launcher)"

    fun getText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} from ${URL(url).host}")
            return conn.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            conn.disconnect()
        }
    }

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/** Current conditions from Open-Meteo, which is free and needs no API key. */
object Weather {
    data class Now(val temperature: Int, val code: Int, val isDay: Boolean) {
        val icon: String get() = iconFor(code, isDay)
        val description: String get() = describe(code)
    }

    /** Finds a place by city name or postal code. Network call. */
    fun findPlace(query: String): Prefs.Place? {
        val q = query.trim()
        if (q.isEmpty()) return null
        val json = JSONObject(
            Http.getText(
                "https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" +
                    Http.enc(q),
            ),
        )
        val r = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        val region = r.optString("admin1").takeIf { it.isNotBlank() && it != r.optString("name") }
        val name = listOfNotNull(r.optString("name"), region).joinToString(", ")
        return Prefs.Place(name, r.getDouble("latitude"), r.getDouble("longitude"))
    }

    /** Network call. */
    fun fetch(context: Context): Now? {
        val place = Prefs.place(context) ?: return null
        val unit = if (Prefs.fahrenheit(context)) "fahrenheit" else "celsius"
        val json = JSONObject(
            Http.getText(
                "https://api.open-meteo.com/v1/forecast?current=temperature_2m,weather_code,is_day" +
                    "&timezone=auto&temperature_unit=$unit&latitude=${place.lat}&longitude=${place.lon}",
            ),
        )
        val current = json.getJSONObject("current")
        return Now(
            temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
        )
    }

    // WMO weather codes, as used by Open-Meteo.
    private fun iconFor(code: Int, day: Boolean) = when (code) {
        0 -> if (day) "☀️" else "🌙"
        1, 2 -> if (day) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Clear"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67 -> "Rain"
        in 71..77 -> "Snow"
        in 80..82 -> "Showers"
        85, 86 -> "Snow showers"
        in 95..99 -> "Thunderstorms"
        else -> ""
    }
}
