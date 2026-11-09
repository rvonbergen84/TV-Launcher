package io.github.rvonbergen84.tvlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

/**
 * Background pictures. Photos come from Wikimedia Commons "featured pictures": picked by
 * the Commons community, freely licensed, high resolution, and no API key needed.
 * Abstract pieces are drawn on the TV (see [AbstractArt]).
 *
 * The list is fetched at most once a day and cached on disk, so the TV makes very few
 * requests and the home screen has pictures straight away at startup.
 */
object Photos {
    /** What the user can switch on and off. Each photo theme is one or more Commons searches. */
    enum class Theme(val label: String, val searches: List<String>) {
        AERIAL("Aerial landscapes", listOf("$FEATURED (aerial OR drone OR \"bird's-eye\") $PHOTOS")),
        SPACE(
            "Earth from space",
            listOf("$FEATURED (\"from space\" OR \"satellite image\" OR Landsat OR Sentinel-2 OR \"from the International Space Station\") $PHOTOS"),
        ),
        WILDLIFE(
            "Wildlife",
            listOf("deepcat:\"Featured pictures of mammals\" $PHOTOS", "deepcat:\"Featured pictures of birds\" $PHOTOS"),
        ),
        ABSTRACT("Abstract art", emptyList()),
    }

    data class Photo(val url: String, val title: String, val credit: String)

    // Every featured picture is directly in this category, so text searches can be limited to it.
    private const val FEATURED = "incategory:Featured_pictures_on_Wikimedia_Commons"
    private const val PHOTOS = "filetype:bitmap"
    private const val CACHE_FILE = "photos.json"
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    private const val PER_SEARCH = 50
    private const val ABSTRACT_PIECES = 20

    /**
     * Kept off a family TV even if Commons lists them under one of the themes: nudity, and
     * the darker side of wildlife photography. Checked against title, description and
     * categories. Errs on the side of skipping a good photo.
     */
    private val notForKids = Regex(
        "\\b(nud(e|es|ity)|naked|topless|breasts?|erotic|sexual|genital|corpses?|carcass|dead|death|" +
            "blood|bloody|prey|predation|kill(s|ed|ing)?|hunt(s|ed|ing)?|skulls?|skeletons?|bones?|fossils?|" +
            "mating|copulat\\w*|slaughter\\w*|taxiderm\\w*|roadkill|wound\\w*|injur\\w*|parasit\\w*|" +
            "feeding on|eating|devour\\w*|war|weapons?|guns?|bomb\\w*|explosion)\\b",
        RegexOption.IGNORE_CASE,
    )

    internal fun suitableForKids(text: String) = !notForKids.containsMatchIn(text)

    /** Cached list if it's fresh and matches the chosen themes, else a new one. Network call. */
    fun load(context: Context, forceRefresh: Boolean = false): List<Photo> {
        val themes = Prefs.photoThemes(context)
        val key = themes.map { it.name }.sorted().joinToString(",")
        val cached = readCache(context)
        if (!forceRefresh && cached != null && cached.themes == key && cached.width == targetWidth(context) &&
            System.currentTimeMillis() - cached.savedAt < MAX_AGE_MS && cached.photos.isNotEmpty()
        ) {
            return cached.photos
        }
        val fetched = themes.flatMap { theme ->
            theme.searches.flatMap { search ->
                runCatching { fetch(search, targetWidth(context)) }
                    .onSuccess { Prefs.log(context, "Photos: ${it.size} for ${theme.label}") }
                    .onFailure { Prefs.log(context, "Photos: ${theme.label} failed, ${it.message}") }
                    .getOrDefault(emptyList())
            }
        }
        if (fetched.isEmpty() && themes.any { it != Theme.ABSTRACT }) {
            // Offline or rate-limited: an old list beats starting over.
            cached?.photos?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        val abstract = if (Theme.ABSTRACT in themes) {
            List(ABSTRACT_PIECES) { AbstractArt.entry(Random.nextInt()) }
        } else {
            emptyList()
        }
        val fresh = (fetched + abstract).shuffled()
        if (fresh.isNotEmpty()) writeCache(context, key, targetWidth(context), fresh)
        return fresh
    }

    /** Cached list only, no network. For drawing the first frame quickly. */
    fun cachedOnly(context: Context): List<Photo> = readCache(context)?.photos.orEmpty()

    private const val API = "https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2" +
        "&prop=imageinfo|categories&cllimit=50&clshow=!hidden" +
        "&iiprop=url|size|extmetadata&iiextmetadatafilter=ObjectName|Artist|ImageDescription"

    /** Photo width to ask for: 4K if the TV draws its home screen at 4K, else 1080p. */
    fun targetWidth(context: Context): Int {
        val m = context.resources.displayMetrics
        return if (maxOf(m.widthPixels, m.heightPixels) > 2000) 3840 else 1920
    }

    /** One Commons search, in random order so each day brings a different set. */
    private fun fetch(search: String, width: Int): List<Photo> = query(
        width,
        "$API&iiurlwidth=$width&generator=search&gsrnamespace=6&gsrlimit=$PER_SEARCH&gsrsort=random&gsrsearch=" +
            Http.enc(search),
    )

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
        val categories = page.optJSONArray("categories")?.let { arr ->
            (0 until arr.length()).joinToString(" ") { arr.getJSONObject(it).optString("title") }
        }.orEmpty()
        val description = meta?.optJSONObject("ImageDescription")?.optString("value")?.let { plainText(it, 2000) }.orEmpty()
        val text = listOf(page.optString("title"), description, categories).joinToString(" ")
        if (!suitableForKids(text)) return null
        val title = meta?.optJSONObject("ObjectName")?.optString("value")?.let(::plainText)
            ?: page.optString("title").removePrefix("File:").substringBeforeLast('.')
        val credit = meta?.optJSONObject("Artist")?.optString("value")?.let(::plainText).orEmpty()
        return Photo(thumb, title, credit)
    }

    /** Commons metadata is HTML; keep just the text. */
    private fun plainText(html: String, max: Int = 80): String =
        html.replace(Regex("<[^>]*>"), "").replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#039;", "'").replace(Regex("\\s+"), " ").trim().take(max)

    private class Cache(val themes: String, val width: Int, val savedAt: Long, val photos: List<Photo>)

    private fun readCache(context: Context): Cache? = runCatching {
        val json = JSONObject(File(context.filesDir, CACHE_FILE).readText())
        val arr = json.getJSONArray("photos")
        Cache(
            json.optString("themes"),
            json.optInt("width", 1920),
            json.getLong("savedAt"),
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Photo(o.getString("url"), o.optString("title"), o.optString("credit"))
            },
        )
    }.getOrNull()

    private fun writeCache(context: Context, themes: String, width: Int, photos: List<Photo>) {
        val arr = JSONArray()
        photos.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("credit", it.credit)) }
        val json = JSONObject().put("themes", themes).put("width", width).put("savedAt", System.currentTimeMillis()).put("photos", arr)
        File(context.filesDir, CACHE_FILE).writeText(json.toString())
    }
}
