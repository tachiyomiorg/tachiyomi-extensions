package eu.kanade.tachiyomi.extension.en.mangapark

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.absoluteValue


class MangaPark : ConfigurableSource, ParsedHttpSource() {

    override val lang = "en"

    override val supportsLatest = true
    override val name = "MangaPark"
    override val baseUrl = "https://mangapark.net"

    private val directorySelector = ".ls1 .item"
    private val directoryUrl = "/genre"
    private val directoryNextPageSelector = ".paging.full > li:last-child > a"

    private val dateFormat = SimpleDateFormat("MMM d, yyyy, HH:mm a", Locale.ENGLISH)
    private val dateFormatTimeOnly = SimpleDateFormat("HH:mm a", Locale.ENGLISH)

    private fun cleanUrl(url: String) = if (url.startsWith("//"))
        "http:$url"
    else url


    override fun popularMangaRequest(page: Int) = GET("$baseUrl$directoryUrl/$page?views_a")

    override fun popularMangaSelector() = directorySelector

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun popularMangaNextPageSelector() = directoryNextPageSelector


    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl$directoryUrl/$page?update")

    override fun latestUpdatesSelector() = directorySelector

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = directoryNextPageSelector


    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search").buildUpon()
        uri.appendQueryParameter("q", query)
        filters.forEach {
            if (it is UriFilter)
                it.addToUri(uri)
        }
        uri.appendQueryParameter("page", page.toString())
        return GET(uri.toString())
    }

    override fun searchMangaSelector() = ".item"

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaNextPageSelector() = ".paging:not(.order) > li:last-child > a"

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        val coverElement = element.getElementsByClass("cover").first()
        url = coverElement.attr("href")
        title = coverElement.attr("title")
        thumbnail_url = coverElement.select("img").attr("abs:src")
    }

    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val coverElement = document.select(".cover > img").first()

        title = coverElement.attr("title")

        thumbnail_url = cleanUrl(coverElement.attr("src"))

        document.select(".attr > tbody > tr").forEach {
            when (it.getElementsByTag("th").first().text().trim().toLowerCase()) {
                "author(s)" -> {
                    author = it.getElementsByTag("a").joinToString(transform = Element::text)
                }
                "artist(s)" -> {
                    artist = it.getElementsByTag("a").joinToString(transform = Element::text)
                }
                "genre(s)" -> {
                    genre = it.getElementsByTag("a").joinToString(transform = Element::text)
                }
                "status" -> {
                    status = when (it.getElementsByTag("td").text().trim().toLowerCase()) {
                        "ongoing" -> SManga.ONGOING
                        "completed" -> SManga.COMPLETED
                        else -> SManga.UNKNOWN
                    }
                }
            }
        }

        description = document.getElementsByClass("summary").text().trim()
    }
    
    override fun chapterListParse(response: Response): List<SChapter> {
        fun List<SChapter>.getMissingChapters(allChapters: List<SChapter>): List<SChapter> {
            val chapterNums = this.map { it.chapter_number }
            return allChapters.filter { it.chapter_number !in chapterNums }.distinctBy { it.chapter_number }
        }

        fun List<SChapter>.filterOrAll(source: String): List<SChapter>{
            val chapters = this.filter { it.scanlator!!.contains(source) }
            return if (chapters.isNotEmpty()) {
                (chapters + chapters.getMissingChapters(this)).sortedByDescending { it.chapter_number }
            } else {
                this
            }
        }

        val mangaBySource = response.asJsoup().select("div[id^=stream]").map { sourceElement ->
            sourceElement.select(chapterListSelector()).map { chapterFromElement(it, sourceElement.select("i + span").text()) }
        }
        return when (getSourcePref()) {
            // source with most chapters along with chapters that source doesn't have
            "most" -> {
                val chapters = mangaBySource.maxBy { it.count() }!!
                (chapters + chapters.getMissingChapters(mangaBySource.flatten())).sortedByDescending { it.chapter_number }
            }
            // "smart list" - try not to miss a chapter and avoid dupes
            "smart" -> mangaBySource.flatten().distinctBy { it.chapter_number }.sortedByDescending { it.chapter_number }
            // use a specific source + any missing chapters, display all if none available from that source
            "rock" -> mangaBySource.flatten().filterOrAll("Rock")
            "duck" -> mangaBySource.flatten().filterOrAll("Duck")
            "mini" -> mangaBySource.flatten().filterOrAll("Mini")
            "fox" -> mangaBySource.flatten().filterOrAll("Fox")
            "panda" -> mangaBySource.flatten().filterOrAll("Panda")
            // all sources, all chapters
            else -> mangaBySource.flatten()
        }
    }

    override fun chapterListSelector() = ".volume .chapter li"

    private fun chapterFromElement(element: Element, source: String) = SChapter.create().apply {
        url = element.select(".tit > a").first().attr("href").replaceAfterLast("/", "")
        name = element.select(".tit > a").first().text()
        // Get the chapter number or create a unique one if it's not available
        chapter_number = Regex("""\b\d+\.?\d?\b""").find(name)?.value?.toFloatOrNull() ?: ".${name.hashCode().absoluteValue}".toFloat()
        date_upload = parseDate(element.select(".time").first().text().trim())
        scanlator = source
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    @SuppressLint("DefaultLocale")
    private fun parseDate(date: String): Long {
        val lcDate = date.toLowerCase()
        if (lcDate.endsWith("ago")) return parseRelativeDate(lcDate)

        //Handle 'yesterday' and 'today'
        var relativeDate: Calendar? = null
        if (lcDate.startsWith("yesterday")) {
            relativeDate = Calendar.getInstance()
            relativeDate.add(Calendar.DAY_OF_MONTH, -1) //yesterday
        } else if (lcDate.startsWith("today")) {
            relativeDate = Calendar.getInstance()
        }

        relativeDate?.let {
            //Since the date is not specified, it defaults to 1970!
            val time = dateFormatTimeOnly.parse(lcDate.substringAfter(' '))
            val cal = Calendar.getInstance()
            cal.time = time

            //Copy time to relative date
            it.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
            it.set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
            return it.timeInMillis
        }

        return dateFormat.parse(lcDate).time
    }

    /**
     * Parses dates in this form:
     * `11 days ago`
     */
    private fun parseRelativeDate(date: String): Long {
        val trimmedDate = date.split(" ")

        if (trimmedDate[2] != "ago") return 0

        val number = when (trimmedDate[0]) {
            "a" -> 1
            else -> trimmedDate[0].toIntOrNull() ?: return 0
        }
        val unit = trimmedDate[1].removeSuffix("s") //Remove 's' suffix

        val now = Calendar.getInstance()

        //Map English unit to Java unit
        val javaUnit = when (unit) {
            "year" -> Calendar.YEAR
            "month" -> Calendar.MONTH
            "week" -> Calendar.WEEK_OF_MONTH
            "day" -> Calendar.DAY_OF_MONTH
            "hour" -> Calendar.HOUR
            "minute" -> Calendar.MINUTE
            "second" -> Calendar.SECOND
            else -> return 0
        }

        now.add(javaUnit, -number)

        return now.timeInMillis
    }

    override fun pageListParse(document: Document): List<Page> {
        val doc = document.toString()
        val obj = doc.substringAfter("var _load_pages = ").substringBefore(";")
        val pages = mutableListOf<Page>()
        val imglist = JSONArray(obj)
        for (i in 0 until imglist.length()) {
            val item = imglist.getJSONObject(i)
            var page = item.getString("u")
            if (page.startsWith("//")) {
                page = "https:$page"
            }
            pages.add(Page(i, "", page))
        }
        return pages
    }

    //Unused, we can get image urls directly from the chapter page
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
            AuthorArtistText(),
            SearchTypeFilter("Title query", "name-match"),
            SearchTypeFilter("Author/Artist query", "autart-match"),
            SortFilter(),
            GenreGroup(),
            GenreInclusionFilter(),
            ChapterCountFilter(),
            StatusFilter(),
            RatingFilter(),
            TypeFilter(),
            YearFilter()
    )

    private class SearchTypeFilter(name: String, val uriParam: String) :
            Filter.Select<String>(name, STATE_MAP), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter(uriParam, STATE_MAP[state])
        }

        companion object {
            private val STATE_MAP = arrayOf("contain", "begin", "end")
        }
    }

    private class AuthorArtistText : Filter.Text("Author/Artist"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("autart", state)
        }
    }

    private class GenreFilter(val uriParam: String, displayName: String) : Filter.TriState(displayName)

    private class GenreGroup : Filter.Group<GenreFilter>("Genres", listOf(
            GenreFilter("4-koma", "4 koma"),
            GenreFilter("action", "Action"),
            GenreFilter("adaptation", "Adaptation"),
            GenreFilter("adult", "Adult"),
            GenreFilter("adventure", "Adventure"),
            GenreFilter("aliens", "Aliens"),
            GenreFilter("animals", "Animals"),
            GenreFilter("anthology", "Anthology"),
            GenreFilter("award-winning", "Award winning"),
            GenreFilter("comedy", "Comedy"),
            GenreFilter("cooking", "Cooking"),
            GenreFilter("crime", "Crime"),
            GenreFilter("crossdressing", "Crossdressing"),
            GenreFilter("delinquents", "Delinquents"),
            GenreFilter("demons", "Demons"),
            GenreFilter("doujinshi", "Doujinshi"),
            GenreFilter("drama", "Drama"),
            GenreFilter("ecchi", "Ecchi"),
            GenreFilter("fantasy", "Fantasy"),
            GenreFilter("full-color", "Full color"),
            GenreFilter("game", "Game"),
            GenreFilter("gender-bender", "Gender bender"),
            GenreFilter("genderswap", "Genderswap"),
            GenreFilter("ghosts", "Ghosts"),
            GenreFilter("gore", "Gore"),
            GenreFilter("gossip", "Gossip"),
            GenreFilter("gyaru", "Gyaru"),
            GenreFilter("harem", "Harem"),
            GenreFilter("historical", "Historical"),
            GenreFilter("horror", "Horror"),
            GenreFilter("incest", "Incest"),
            GenreFilter("isekai", "Isekai"),
            GenreFilter("josei", "Josei"),
            GenreFilter("kids", "Kids"),
            GenreFilter("loli", "Loli"),
            GenreFilter("lolicon", "Lolicon"),
            GenreFilter("long-strip", "Long strip"),
            GenreFilter("magic", "Magic"),
            GenreFilter("magical-girls", "Magical girls"),
            GenreFilter("manhwa", "Manhwa"),
            GenreFilter("martial-arts", "Martial arts"),
            GenreFilter("mature", "Mature"),
            GenreFilter("mecha", "Mecha"),
            GenreFilter("medical", "Medical"),
            GenreFilter("military", "Military"),
            GenreFilter("monster-girls", "Monster girls"),
            GenreFilter("monsters", "Monsters"),
            GenreFilter("music", "Music"),
            GenreFilter("mystery", "Mystery"),
            GenreFilter("office-workers", "Office workers"),
            GenreFilter("official-colored", "Official colored"),
            GenreFilter("one-shot", "One shot"),
            GenreFilter("parody", "Parody"),
            GenreFilter("philosophical", "Philosophical"),
            GenreFilter("police", "Police"),
            GenreFilter("post-apocalyptic", "Post apocalyptic"),
            GenreFilter("psychological", "Psychological"),
            GenreFilter("reincarnation", "Reincarnation"),
            GenreFilter("reverse-harem", "Reverse harem"),
            GenreFilter("romance", "Romance"),
            GenreFilter("school-life", "School life"),
            GenreFilter("sci-fi", "Sci fi"),
            GenreFilter("seinen", "Seinen"),
            GenreFilter("shota", "Shota"),
            GenreFilter("shotacon", "Shotacon"),
            GenreFilter("shoujo", "Shoujo"),
            GenreFilter("shoujo-ai", "Shoujo ai"),
            GenreFilter("shounen", "Shounen"),
            GenreFilter("shounen-ai", "Shounen ai"),
            GenreFilter("slice-of-life", "Slice of life"),
            GenreFilter("smut", "Smut"),
            GenreFilter("space", "Space"),
            GenreFilter("sports", "Sports"),
            GenreFilter("super-power", "Super power"),
            GenreFilter("superhero", "Superhero"),
            GenreFilter("supernatural", "Supernatural"),
            GenreFilter("survival", "Survival"),
            GenreFilter("suspense", "Suspense"),
            GenreFilter("thriller", "Thriller"),
            GenreFilter("time-travel", "Time travel"),
            GenreFilter("tragedy", "Tragedy"),
            GenreFilter("user-created", "User created"),
            GenreFilter("vampire", "Vampire"),
            GenreFilter("vampires", "Vampires"),
            GenreFilter("video-games", "Video games"),
            GenreFilter("web-comic", "Web comic"),
            GenreFilter("webtoon", "Webtoon"),
            GenreFilter("wuxia", "Wuxia"),
            GenreFilter("yaoi", "Yaoi"),
            GenreFilter("yuri", "Yuri"),
            GenreFilter("zombies", "Zombies")
    )), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("genres", state.filter { it.isIncluded() }.joinToString(",") { it.uriParam })
            uri.appendQueryParameter("genres-exclude", state.filter { it.isExcluded() }.joinToString(",") { it.uriParam })
        }
    }

    private class GenreInclusionFilter : UriSelectFilter("Genre inclusion", "genres-mode", arrayOf(
            Pair("and", "And mode"),
            Pair("or", "Or mode")
    ))

    private class ChapterCountFilter : UriSelectFilter("Chapter count", "chapters", arrayOf(
            Pair("any", "Any"),
            Pair("1", "1 +"),
            Pair("5", "5 +"),
            Pair("10", "10 +"),
            Pair("20", "20 +"),
            Pair("30", "30 +"),
            Pair("40", "40 +"),
            Pair("50", "50 +"),
            Pair("100", "100 +"),
            Pair("150", "150 +"),
            Pair("200", "200 +")
    ))

    private class StatusFilter : UriSelectFilter("Status", "status", arrayOf(
            Pair("any", "Any"),
            Pair("completed", "Completed"),
            Pair("ongoing", "Ongoing")
    ))

    private class RatingFilter : UriSelectFilter("Rating", "rating", arrayOf(
            Pair("any", "Any"),
            Pair("5", "5 stars"),
            Pair("4", "4 stars"),
            Pair("3", "3 stars"),
            Pair("2", "2 stars"),
            Pair("1", "1 star"),
            Pair("0", "0 stars")
    ))

    private class TypeFilter : UriSelectFilter("Type", "types", arrayOf(
            Pair("any", "Any"),
            Pair("manga", "Japanese Manga"),
            Pair("manhwa", "Korean Manhwa"),
            Pair("manhua", "Chinese Manhua"),
            Pair("unknown", "Unknown")
    ))

    private class YearFilter : UriSelectFilter("Release year", "years",
            arrayOf(Pair("any", "Any"),
                    //Get all years between today and 1946
                    *(Calendar.getInstance().get(Calendar.YEAR) downTo 1946).map {
                        Pair(it.toString(), it.toString())
                    }.toTypedArray()
            )
    )

    private class SortFilter : UriSelectFilter("Sort", "orderby", arrayOf(
            Pair("a-z", "A-Z"),
            Pair("views", "Views"),
            Pair("rating", "Rating"),
            Pair("latest", "Latest"),
            Pair("add", "New manga")
    ), firstIsUnspecified = false, defaultValue = 1)

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                       val firstIsUnspecified: Boolean = true,
                                       defaultValue: Int = 0) :
            Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    // Preferences

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val myPref = androidx.preference.ListPreference(screen.context).apply {
            key = SOURCE_PREF_TITLE
            title = SOURCE_PREF_TITLE
            entries = arrayOf("All sources, all chapters", "Source with most chapters", "Smart list", "Prioritize source: Rock",
                "Prioritize source: Duck", "Prioritize source: Mini", "Prioritize source: Fox", "Prioritize source: Panda")
            entryValues = arrayOf("all", "most", "smart", "rock", "duck", "mini", "fox", "panda")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues.get(index) as String
                preferences.edit().putString(SOURCE_PREF, entry).commit()
            }
        }
        screen.addPreference(myPref)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val myPref = ListPreference(screen.context).apply {
            key = SOURCE_PREF_TITLE
            title = SOURCE_PREF_TITLE
            entries = arrayOf("All sources, all chapters", "Source with most chapters", "Smart list", "Prioritize source: Rock",
                "Prioritize source: Duck", "Prioritize source: Mini", "Prioritize source: Fox", "Prioritize source: Panda")
            entryValues = arrayOf("all", "most", "smart", "rock", "duck", "mini", "fox", "panda")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues.get(index) as String
                preferences.edit().putString(SOURCE_PREF, entry).commit()
            }
        }
        screen.addPreference(myPref)
    }
    private fun getSourcePref(): String? = preferences.getString(SOURCE_PREF, "all")
    
    companion object {
        private const val SOURCE_PREF_TITLE = "Chapter List Source Pref"
        private const val SOURCE_PREF = "Manga_Park_Source_Pref"
    }
}
