package eu.kanade.tachiyomi.extension.all.wpmangastream

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.ListPreference
import android.support.v7.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

abstract class WPMangaStream(override val name: String, override val baseUrl: String, override val lang: String) : ConfigurableSource, ParsedHttpSource() {
    override val supportsLatest = true

    companion object {
        private const val MID_QUALITY = 1
        private const val LOW_QUALITY = 2

        private const val SHOW_THUMBNAIL_PREF_Title = "Default thumbnail quality"
        private const val SHOW_THUMBNAIL_PREF = "showThumbnailDefault"
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {

        val thumbsPref = ListPreference(screen.context).apply {
            key = SHOW_THUMBNAIL_PREF_Title
            title = SHOW_THUMBNAIL_PREF_Title
            entries = arrayOf("Show high quality", "Show mid quality", "Show low quality")
            entryValues = arrayOf("0", "1", "2")
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                preferences.edit().putInt(SHOW_THUMBNAIL_PREF, index).commit()
            }
        }
        screen.addPreference(thumbsPref)
    }

    private fun getShowThumbnail(): Int = preferences.getInt(SHOW_THUMBNAIL_PREF, 0)

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()


    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page/?order=latest", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl = if (page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page/"
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        url.addQueryParameter("title", query)
        url.addQueryParameter("page", page.toString())
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is SortByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = "div.bs"
    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.limit img").attr("src")
        element.select("div.bsx > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.next.page-numbers"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.spe").first()
        val descElement = document.select(".infox > div.desc").first()
        var author = ""
        var artist = ""
        try {
            val authors = infoElement.select(".spe > span:contains(Author)").last().ownText()
            if (authors.contains(",")) {
                val tmp = authors.split(",")
                if (tmp.size >= 2) {
                    author = ""
                    artist = ""
                    if(authors.contains("Art") && authors.contains("Story")) {
                        tmp.forEach { s ->
                            val ss = s.replace("(", "")
                                .replace(")", "")
                                .replace("Art", "")
                                .replace("Story", "")
                                .replace("&", "")
                                .replace(" and ", "")
                                .trim()
                            if (s.contains("Story") && s.contains("Art")) {
                                if (author != "") author += ", "
                                if (artist != "") artist += ", "
                                author += ss
                                artist += ss
                            } else if (s.contains("Story")) {
                                if (author != "") author += ", "
                                author += ss
                            } else if (s.contains("Story")) {
                                if (artist != "") artist += ", "
                                artist += ss
                            } else {
                                if (artist != "") artist += ", "
                                artist += ss
                            }
                        }
                    }else {
                        tmp.forEachIndexed { i, s ->
                            val ss =s.replace("(", "")
                                .replace(")", "")
                                .replace("Art", "")
                                .replace("Story", "")
                                .replace("&", "")
                                .trim()
                            if(i == 0) author += ss
                            else {
                                if (artist != "") artist += ", "
                                artist += ss
                            }
                        }
                    }
                    if(author == "" && artist != ""){
                        author = artist
                    }

                    if(author != "" && artist == ""){
                        artist = author
                    }

                    if(author == "" && artist == "") {
                        author = authors
                        artist = authors
                    }
                }
            }else{
                author = authors.replace("(", "")
                    .replace(")", "")
                    .replace("Art", "")
                    .replace("Story", "")
                    .replace("&", "")
                    .trim()
                artist = author
            }
        } catch( ex: NullPointerException ){ }
        val manga = SManga.create()
        manga.author = author
        manga.artist = artist
        val genres = mutableListOf<String>()
        infoElement.select(".spe > span:nth-child(1) > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select(".spe > span:nth-child(2)").text())
        manga.description = descElement.select("p").text()
        manga.thumbnail_url = document.select(".thumb > img:nth-child(1)").attr("src")
        return manga
    }

    @SuppressLint("DefaultLocale")
    internal open fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("ongoing") -> SManga.ONGOING
        element.toLowerCase().contains("completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.bxcl ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".lchx > a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = System.currentTimeMillis()
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Chapter\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div#readerarea img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val headers = Headers.Builder()
        headers.apply {
            add("Referer", baseUrl)
            add("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.4.2; en-us; LGMS323 Build/KOT49I.MS32310c) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/76.0.3809.100 Mobile Safari/537.36")
        }

        if (page.imageUrl!!.contains("i0.wp.com")) {
            headers.apply {
                add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")
            }
        }

        return GET(getImageUrl(page.imageUrl!!, getShowThumbnail()), headers.build())
    }

    private fun getImageUrl(baseUrl: String, quality: Int): String {
        var url = baseUrl
        when(quality){
            LOW_QUALITY -> {
                url = url.replace("https://", "")
                url = "https://images.weserv.nl/?w=300&q=70&url=$url"
            }
            MID_QUALITY -> {
                url = url.replace("https://", "")
                url = "http://images.weserv.nl/?w=600&q=70&url=$url"
            }
        }
        return url
    }

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class TypeFilter : UriPartFilter("Type", arrayOf(
        Pair("Default", ""),
        Pair("Manga", "Manga"),
        Pair("Manhwa", "Manhwa"),
        Pair("Manhua", "Manhua"),
        Pair("Comic", "Comic")
    ))

    protected class SortByFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Default", ""),
        Pair("A-Z", "title"),
        Pair("Z-A", "titlereverse"),
        Pair("Latest Update", "update"),
        Pair("Latest Added", "latest"),
        Pair("Popular", "popular")
    ))

    protected class StatusFilter : UriPartFilter("Status", arrayOf(
        Pair("All", ""),
        Pair("Ongoing", "ongoing"),
        Pair("Completed", "completed")
    ))

    protected class Genre(name: String, val id: String = name) : Filter.TriState(name)
    protected class GenreListFilter(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreListFilter(getGenreList())
    )

    protected open fun getGenreList(): List<Genre> = listOf(
        Genre("4 Koma", "4-koma"),
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Completed", "completed"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Demon", "demon"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Games", "games"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gore", "gore"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Art", "martial-art"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Military", "military"),
        Genre("Monster", "monster"),
        Genre("Monster Girls", "monster-girls"),
        Genre("Monsters", "monsters"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("One-shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Police", "police"),
        Genre("Pshycological", "pshycological"),
        Genre("Psychological", "psychological"),
        Genre("Reincarnation", "reincarnation"),
        Genre("Reverse Harem", "reverse-harem"),
        Genre("Romancce", "romancce"),
        Genre("Romance", "romance"),
        Genre("Samurai", "samurai"),
        Genre("School", "school"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Time Travel", "time-travel"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoon", "webtoon"),
        Genre("Webtoons", "webtoons"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
        Genre("Zombies", "zombies")
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
