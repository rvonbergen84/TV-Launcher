package io.github.rvonbergen84.tvlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * Background photos from Wikimedia Commons "featured pictures": hand-picked,
 * freely licensed, high quality, and no API key needed.
 *
 * The list is fetched at most once a day and cached on disk, so the TV makes
 * very few requests and the home screen has photos straight away at startup.
 */
object Photos {
    enum class Source(val label: String, val categories: List<String>) {
        BOTH("Landscapes and art", listOf(LANDSCAPES, PAINTINGS)),
        LANDSCAPES_ONLY("Landscapes", listOf(LANDSCAPES)),
        ART_ONLY("Art", listOf(PAINTINGS)),
    }

    data class Photo(val url: String, val title: String, val credit: String)

    private const val LANDSCAPES = "Category:Featured pictures of landscapes"
    private const val PAINTINGS = "Category:Featured pictures of paintings"
    private const val CACHE_FILE = "photos.json"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val PER_CATEGORY = 50

    /** Cached list if it's fresh and matches the chosen source, else a new one. Network call. */
    fun load(context: Context, forceRefresh: Boolean = false): List<Photo> {
        val source = Prefs.photoSource(context)
        val cached = readCache(context)
        if (!forceRefresh && cached != null && cached.source == source && cached.width == targetWidth(context) &&
            System.currentTimeMillis() - cached.savedAt < MAX_AGE_MS && cached.photos.isNotEmpty()
        ) {
            return cached.photos
        }
        val fresh = source.categories.flatMap { category ->
            runCatching { fetchCategory(category, targetWidth(context)) }
                .onSuccess { Prefs.log(context, "Photos: ${it.size} from ${category.substringAfter("of ")}") }
                .onFailure { Prefs.log(context, "Photos: ${category.substringAfter("of ")} failed, ${it.message}") }
                .getOrDefault(emptyList())
        }.shuffled()
        if (fresh.isNotEmpty()) {
            writeCache(context, source, targetWidth(context), fresh)
            return fresh
        }
        // Offline or rate-limited: an old list beats a blank screen.
        return cached?.photos.orEmpty()
    }

    /** Cached list only, no network. For drawing the first frame quickly. */
    fun cachedOnly(context: Context): List<Photo> = readCache(context)?.photos.orEmpty()

    private const val API = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
        "&prop=imageinfo&iiprop=url|size|extmetadata&iiextmetadatafilter=ObjectName|Artist"

    /** Photo width to ask for: 4K if the TV draws its home screen at 4K, else 1080p. */
    fun targetWidth(context: Context): Int {
        val m = context.resources.displayMetrics
        return if (maxOf(m.widthPixels, m.heightPixels) > 2000) 3840 else 1920
    }

    private fun fetchCategory(category: String, width: Int): List<Photo> {
        // Search includes subcategories (most paintings sit in "by artist" subcategories)
        // and returns a random sample, so each day brings a different set.
        val search = "deepcat:\"${category.removePrefix("Category:")}\" filetype:bitmap"
        val viaSearch = query(
            width,
            "$API&iiurlwidth=$width&generator=search&gsrnamespace=6&gsrlimit=$PER_CATEGORY&gsrsort=random&gsrsearch=" + Http.enc(search),
        )
        if (viaSearch.isNotEmpty()) return viaSearch
        // Fallback: files directly in the category, from a random point in its history.
        val year = Random.nextInt(2008, 2026)
        return query(
            width,
            "$API&iiurlwidth=$width&generator=categorymembers&gcmtype=file&gcmsort=timestamp&gcmlimit=$PER_CATEGORY" +
                "&gcmstart=$year-01-01T00:00:00Z&gcmtitle=" + Http.enc(category),
        )
    }

    private fun query(width: Int, url: String): List<Photo> {
        val pages = JSONObject(Http.getText(url)).optJSONObject("query")?.optJSONArray("pages")
            ?: return emptyList()
        return (0 until pages.length()).mapNotNull { i -> toPhoto(pages.getJSONObject(i), width) }
    }

    private fun toPhoto(page: JSONObject, minWidth: Int): Photo? {
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: return null
        val width = info.optInt("width")
        val height = info.optInt("height")
        // Wide images only; tall ones would be cropped too hard on a TV.
        if (height == 0 || width.toDouble() / height < 1.3) return null
        // Skip originals smaller than the screen; stretching them is what looks soft.
        if (width < minOf(minWidth, 1920)) return null
        val thumb = info.optString("thumburl").takeIf { it.isNotBlank() } ?: return null
        val meta = info.optJSONObject("extmetadata")
        val title = meta?.optJSONObject("ObjectName")?.optString("value")?.let(::plainText)
            ?: page.optString("title").removePrefix("File:").substringBeforeLast('.')
        val credit = meta?.optJSONObject("Artist")?.optString("value")?.let(::plainText).orEmpty()
        return Photo(thumb, title, credit)
    }

    /** Commons metadata is HTML; keep just the text. */
    private fun plainText(html: String): String =
        html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#039;", "'").replace(Regex("\\s+"), " ").trim().take(80)

    private class Cache(val source: Source, val width: Int, val savedAt: Long, val photos: List<Photo>)

    private fun readCache(context: Context): Cache? = runCatching {
        val json = JSONObject(File(context.filesDir, CACHE_FILE).readText())
        val arr = json.getJSONArray("photos")
        Cache(
            Source.valueOf(json.getString("source")),
            json.optInt("width", 1920),
            json.getLong("savedAt"),
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Photo(o.getString("url"), o.optString("title"), o.optString("credit"))
            },
        )
    }.getOrNull()

    private fun writeCache(context: Context, source: Source, width: Int, photos: List<Photo>) {
        val arr = JSONArray()
        photos.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("credit", it.credit)) }
        val json = JSONObject().put("source", source.name).put("width", width).put("savedAt", System.currentTimeMillis()).put("photos", arr)
        File(context.filesDir, CACHE_FILE).writeText(json.toString())
    }
}
