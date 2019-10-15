package eu.kanade.tachiyomi.extension.en.mangakisa

import android.net.Uri
import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.*
import java.util.concurrent.TimeUnit


class MangaKisa : ConfigurableSource, ParsedHttpSource() {

    override val name = "MangaKisa"
    override val baseUrl = "https://mangakisa.com"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()!!

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaSelector() = "div.listanimes a.an"
    override fun latestUpdatesSelector() = ".episode-box-2"
    override fun searchMangaSelector() = "div.iepbox a.an"
    override fun chapterListSelector() = ".infoepbox > a"

    override fun popularMangaNextPageSelector() = "div:containsOwn(Next Page >)"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        val page0 = page-1
        val popselect = getpoppref()
        return GET("$baseUrl/$popselect/$page0", headers)

    }
    override fun latestUpdatesRequest(page: Int): Request {
        val page0 = page-1
        val latestselect = getlastestpref()
        return GET("$baseUrl/$latestselect/$page0", headers)
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val page0 = page-1
        val uri = if (query.isNotBlank()) {
            Uri.parse("$baseUrl/search?q=$query").buildUpon()
        } else {
            val uri = Uri.parse("$baseUrl/").buildUpon()
            //Append uri filters
            filters.forEach {
                if (it is UriFilter)
                    it.addToUri(uri)
            }
            uri.appendPath("$page0")
        }
        return GET(uri.toString(), headers)
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(baseUrl + manga.url, headers)
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)
    override fun searchMangaFromElement(element: Element)= mangaFromElement(element)
        private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select(".an").first().attr("href"))
        manga.title = element.select("img").attr("alt").trim()
        manga.thumbnail_url = baseUrl + element.select("img").attr("src")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/" + element.select("a").attr("href"))
        chapter.chapter_number = element.select("[class*=infoept2] > div").text().toFloat()
        chapter.name = "Chapter " + element.select("[class*=infoept2] > div").text().trim()
        chapter.date_upload = parseRelativeDate(element.select("[class*=infoept3] > div").text()) ?:0
        return chapter
    }

    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.split(" ")
        if (trimmedDate[2] != "ago") return null
        val number = trimmedDate[0].toIntOrNull() ?: return null

        // Map English and other language units to Java units
        val javaUnit = when (trimmedDate[1].removeSuffix("s")) {
            "year" -> Calendar.YEAR
            "month" -> Calendar.MONTH
            "day" -> Calendar.DAY_OF_MONTH
            "hr" -> Calendar.HOUR
            "min" -> Calendar.MINUTE
            "second" -> Calendar.SECOND
            else -> return null
        }

        return Calendar.getInstance().apply { add(javaUnit, -number) }.timeInMillis
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.select(".infopicbox > img").attr("alt").trim()
        manga.artist = document.select(".textc > a[href*=authors]").text().trim()
        manga.author = document.select(".textc > a[href*=authors]").text().trim()
        manga.description = getpoppref() + "\n" + getlastestpref() + "\n" + document.select(".infodes2").first().text()
        val glist = document.select("a.infoan[href*=genres]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select(".textc:contains(Ongoing), .textc:contains(Completed)")?.first()?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        manga.thumbnail_url = baseUrl + "/" +  document.select(".infopicbox > img").attr("src")
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img")?.forEach {
            pages.add(Page(pages.size, "", it.attr("src")))
        }

        return pages
    }
    
    override fun imageUrlRequest(page: Page) = throw Exception("Not used")
    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    //Filter List Code
    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter()
    )

    private open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                           val firstIsUnspecified: Boolean = true,
                                           defaultValue: Int = 0) :
        Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendPath(uriParam)
                    .appendPath(vals[state].first)
        }
    }

    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    private class GenreFilter : UriSelectFilter("Genre","genres", arrayOf(
        Pair("all", "ALL"),
        Pair("action", "Action "),
        Pair("adult", "Adult "),
        Pair("adventure", "Adventure "),
        Pair("comedy", "Comedy "),
        Pair("cooking", "Cooking "),
        Pair("doujinshi", "Doujinshi "),
        Pair("drama", "Drama "),
        Pair("ecchi", "Ecchi "),
        Pair("fantasy", "Fantasy "),
        Pair("gender-bender", "Gender Bender "),
        Pair("harem", "Harem "),
        Pair("historical", "Historical "),
        Pair("horror", "Horror "),
        Pair("isekai", "Isekai "),
        Pair("josei", "Josei "),
        Pair("manhua", "Manhua "),
        Pair("manhwa", "Manhwa "),
        Pair("martial-arts", "Martial Arts "),
        Pair("mature", "Mature "),
        Pair("mecha", "Mecha "),
        Pair("medical", "Medical "),
        Pair("mystery", "Mystery "),
        Pair("one-shot", "One Shot "),
        Pair("psychological", "Psychological "),
        Pair("romance", "Romance "),
        Pair("school-life", "School Life "),
        Pair("sci-fi", "Sci Fi "),
        Pair("seinen", "Seinen "),
        Pair("shoujo", "Shoujo "),
        Pair("shoujo-ai", "Shoujo Ai "),
        Pair("shounen", "Shounen "),
        Pair("shounen-ai", "Shounen Ai "),
        Pair("slice-of-life", "Slice Of Life "),
        Pair("smut", "Smut "),
        Pair("sports", "Sports "),
        Pair("supernatural", "Supernatural "),
        Pair("tragedy", "Tragedy "),
        Pair("webtoons", "Webtoons "),
        Pair("yaoi", "Yaoi "),
        Pair("yuri", "Yuri ")
        ))
    
    // Preferences Code 
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val popularmangapref = ListPreference(screen.context).apply {
            key = BROWSE_PREF_Title
            title = BROWSE_PREF_Title
            entries = arrayOf("Weekly", "All Time")
            entryValues = arrayOf("popular", "popular-alltime")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues.get(index) as String
                preferences.edit().putString(BROWSE_PREF, entry).commit()
            }
        }
        val latestmangapref = ListPreference(screen.context).apply {
            key = LATEST_PREF_Title
            title = LATEST_PREF_Title
            entries = arrayOf("Popular Updates", "All Updates")
            entryValues = arrayOf("latest", "all-updates/latest")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues.get(index) as String
                preferences.edit().putString(LATEST_PREF, entry).commit()
            }
        }
        screen.addPreference(popularmangapref)
        screen.addPreference(latestmangapref)
    }

    private fun getpoppref() = preferences.getString(BROWSE_PREF, "popular")
    private fun getlastestpref() = preferences.getString(LATEST_PREF, "latest")


    companion object {
        private const val LATEST_PREF_Title = "Latest Manga Selector"
        private const val LATEST_PREF = "latestmangaurl"
        private const val BROWSE_PREF_Title = "Popular Manga Selector"
        private const val BROWSE_PREF = "popularmangaurl"
    }

}


