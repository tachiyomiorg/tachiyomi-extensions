package eu.kanade.tachiyomi.extension.all.wpmangastream

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class WPMangaStreamFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Kiryuu(),
        KomikAV(),
        KomikStation(),
        KomikCast(),
        WestManga(),
        KomikGo(),
        KomikIndo(),
        MaidManga(),
        SekteKomik()
    )
}

class SekteKomik() : WPMangaStream("Sekte Komik (WP Manga Stream)", "https://sektekomik.com", "id")
class Kiryuu : WPMangaStream("Kiryuu (WP Manga Stream)", "https://kiryuu.co", "id")
class KomikAV : WPMangaStream("Komik AV (WP Manga Stream)", "https://komikav.com", "id")
class KomikStation : WPMangaStream("Komik Station (WP Manga Stream)", "https://komikstation.com", "id")
class KomikCast : WPMangaStream("Komik Cast (WP Manga Stream)", "https://komikcast.com", "id") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/komik/page/$page/", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
            val pattern = "\\s+".toRegex()
            val q = query.replace(pattern, "+")
            if (query.isNotEmpty()) {
                url.addQueryParameter("s", q)
            } else {
                url.addQueryParameter("s", "")
            }
            url.toString()
        } else {
            val url = HttpUrl.parse("$baseUrl/daftar-komik/page/$page")!!.newBuilder()
            var orderBy: String
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is StatusFilter -> url.addQueryParameter("status", arrayOf("", "ongoing", "completed")[filter.state])
                    is GenreListFilter -> {
                        val genreInclude = mutableListOf<String>()
                        filter.state.forEach {
                            if (it.state == 1) {
                                genreInclude.add(it.id)
                            }
                        }
                        if (genreInclude.isNotEmpty()) {
                            genreInclude.forEach { genre ->
                                url.addQueryParameter("genre[]", genre)
                            }
                        }
                    }
                    is SortByFilter -> {
                        orderBy = filter.toUriPart()
                        url.addQueryParameter("order", orderBy)
                    }
                }
            }
            url.toString()
        }
        return GET(url, headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.limit img").attr("src")
        element.select("div.bigor > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.spe").first()
        val sepName = infoElement.select(".spe > span:nth-child(4)").last()
        val manga = SManga.create()
        manga.author = sepName.ownText()
        manga.artist = sepName.ownText()
        val genres = mutableListOf<String>()
        infoElement.select(".spe > span:nth-child(1) > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select(".spe > span:nth-child(2)").text())
        manga.description = document.select("div[^itemprop]").last().text()
        manga.thumbnail_url = document.select(".thumb > img:nth-child(1)").attr("src")

        return manga
    }

    override fun chapterListSelector() = "div.cl ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val timeElement = element.select("span.rightoff").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        return when {
            "mins" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hours" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "days" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "weeks" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "months" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "years" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            "min" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hour" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "day" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "week" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "month" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "year" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortByFilter(),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        GenreListFilter(getGenreList())
    )
}
class WestManga : WPMangaStream("West Manga (WP Manga Stream)", "https://westmanga.info", "id") {
    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/manga-list/?popular" else "$baseUrl/manga-list/page/$page/?popular"
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/manga-list/?latest" else "$baseUrl/manga-list/page/$page/?latest"
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var builtUrl = if (page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page/"
        if (query != "") {
            builtUrl = if (page == 1) "$baseUrl/?s=$query&post_type=manga" else "$baseUrl/page/2/?s=$query&post_type=manga"
        } else if (filters.size > 0) {
            filters.forEach { filter ->
                when (filter) {
                    is SortByFilter -> {
                        builtUrl = if (page == 1) "$baseUrl/manga-list/?" + filter.toUriPart() else "$baseUrl/manga-list/page/$page/?" + filter.toUriPart()
                    }
                    is GenreListFilter -> {
                        builtUrl = if (page == 1) "$baseUrl/genre/" + filter.toUriPart() else "$baseUrl/genre/" + filter.toUriPart() + "/page/$page/"
                    }
                }
            }
        }
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = "div.result-search"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.fletch > .img_search > img").attr("src")
        element.select(".kanan_search > .search_title > .titlex > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun popularMangaNextPageSelector() = ".paginado>ul>li.dd + li.a"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("table.attr").first()
        val descElement = document.select("div.sin").first()
        val sepName = infoElement.select("tr:nth-child(5)>td").first()
        val manga = SManga.create()
        manga.author = sepName.text()
        manga.artist = sepName.text()
        val genres = mutableListOf<String>()
        infoElement.select("tr:nth-child(6)>td > a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("tr:nth-child(4)>td").text())
        manga.description = descElement.select("p").text()
        manga.thumbnail_url = document.select(".topinfo > img").attr("src")
        return manga
    }

    @SuppressLint("DefaultLocale")
    override fun parseStatus(element: String): Int = when {
        element.toLowerCase().contains("publishing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.cl ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".leftoff > a").first()
        val chapter = SChapter.create()
        val timeElement = element.select("span.rightoff").first()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        val sdf = SimpleDateFormat("MMM dd, yyyy")
        val parse = sdf.parse(date)
        val cal = Calendar.getInstance()
        cal.time = parse
        return cal.timeInMillis
    }

    private class SortByFilter : UriPartFilter("Sort By", arrayOf(
        Pair("Default", ""),
        Pair("A-Z", "A-Z"),
        Pair("Latest Added", "latest"),
        Pair("Popular", "popular")
    ))

    private class GenreListFilter : UriPartFilter("Genre", arrayOf(
        Pair("Default", ""),
        Pair("4-Koma", "4-koma"),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Cooking", "cooking"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("FantasyAction", "fantasyaction"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Gore", "gore"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horro", "horro"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Isekai Action", "isekai-action"),
        Pair("Josei", "josei"),
        Pair("Magic", "magic"),
        Pair("Manga", "manga"),
        Pair("Manhua", "manhua"),
        Pair("Martial arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Medical", "medical"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Oneshot", "oneshot"),
        Pair("Project", "project"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School", "school"),
        Pair("School life", "school-life"),
        Pair("Sci fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shoujo Ai", "shoujo-ai"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "sports"),
        Pair("Super Power", "super-power"),
        Pair("Supernatural", "supernatural"),
        Pair("Suspense", "suspense"),
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
        Pair("Vampire", "vampire"),
        Pair("Webtoons", "webtoons"),
        Pair("Yuri", "yuri")
    ))

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: sort and genre can't be combined and ignored when using text search!"),
        Filter.Separator(),
        SortByFilter(),
        GenreListFilter()
    )
}
class KomikGo : WPMangaStream("Komik GO (WP Manga Stream)", "https://komikgo.com", "id") {

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    }

    override fun popularMangaSelector() = "div.c-tabs-item__content"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.tab-thumb > a > img").attr("data-src")
        element.select("div.tab-thumb > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
        url.addQueryParameter("post_type", "wp-manga")
        val pattern = "\\s+".toRegex()
        val q = query.replace(pattern, "+")
        if (query.isNotEmpty()) {
            url.addQueryParameter("s", q)
        } else {
            url.addQueryParameter("s", "")
        }

        var orderBy: String

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
//                is Status -> url.addQueryParameter("manga_status", arrayOf("", "completed", "ongoing")[filter.state])
                is GenreListFilter -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("genre[]", genre)
                        }
                    }
                }
                is StatusList -> {
                    val statuses = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            statuses.add(it.id)
                        }
                    }
                    if (statuses.isNotEmpty()) {
                        statuses.forEach { status ->
                            url.addQueryParameter("status[]", status)
                        }
                    }
                }

                is SortBy -> {
                    orderBy = filter.toUriPart()
                    url.addQueryParameter("m_orderby", orderBy)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }

        return GET(url.toString(), headers)
    }

    override fun popularMangaNextPageSelector() = "#navigation-ajax"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.site-content").first()

        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content")?.text()
        manga.artist = infoElement.select("div.artist-content")?.text()

        val genres = mutableListOf<String>()
        infoElement.select("div.genres-content a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("div.post-status > div:nth-child(2)  div").text())

        manga.description = document.select("div.description-summary")?.text()
        manga.thumbnail_url = document.select("div.summary_image > a > img").attr("data-src")

        return manga
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"


    private fun parseChapterDate(date: String): Long {
        if (date.contains(",")) {
            return try {
                SimpleDateFormat("MMM d, yyyy", Locale.US).parse(date).time
            } catch (e: ParseException) {
                0
            }
        } else {
            val value = date.split(' ')[0].toInt()

            return when {
                "mins" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "hours" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "days" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "weeks" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "months" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "years" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                }.timeInMillis
                "min" in date -> Calendar.getInstance().apply {
                    add(Calendar.MINUTE, value * -1)
                }.timeInMillis
                "hour" in date -> Calendar.getInstance().apply {
                    add(Calendar.HOUR_OF_DAY, value * -1)
                }.timeInMillis
                "day" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * -1)
                }.timeInMillis
                "week" in date -> Calendar.getInstance().apply {
                    add(Calendar.DATE, value * 7 * -1)
                }.timeInMillis
                "month" in date -> Calendar.getInstance().apply {
                    add(Calendar.MONTH, value * -1)
                }.timeInMillis
                "year" in date -> Calendar.getInstance().apply {
                    add(Calendar.YEAR, value * -1)
                }.timeInMillis
                else -> {
                    return 0
                }
            }
        }
    }

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(element.select("span.chapter-release-date i").text())
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        var i = 0
        document.select("div.reading-content * img").forEach { element ->
            val url = element.attr("src")
            i++
            if (url.isNotEmpty()) {
                pages.add(Page(i, "", url))
            }
        }
        return pages
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)

    private class SortBy : UriPartFilter("Sort by", arrayOf(
        Pair("Relevance", ""),
        Pair("Latest", "latest"),
        Pair("A-Z", "alphabet"),
        Pair("Rating", "rating"),
        Pair("Trending", "trending"),
        Pair("Most View", "views"),
        Pair("New", "new-manga")
    ))

    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

    override fun getFilterList() = FilterList(
        TextField("Author", "author"),
        TextField("Year", "release"),
        SortBy(),
        StatusList(getStatusList()),
        GenreListFilter(getGenreList())
    )

    private fun getStatusList() = listOf(
        Status("Completed", "end"),
        Status("Ongoing", "on-going"),
        Status("Canceled", "canceled"),
        Status("Onhold", "on-hold")
    )

    override fun getGenreList(): List<Genre> = listOf(
        Genre("Adventure", "Adventure"),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Cars", "cars"),
        Genre("4-Koma", "4-koma"),
        Genre("Comedy", "comedy"),
        Genre("Completed", "completed"),
        Genre("Cooking", "cooking"),
        Genre("Dementia", "dementia"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kids", "kids"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Old Comic", "old-comic"),
        Genre("One Shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Parodi", "parodi"),
        Genre("Parody", "parody"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("Samurai", "samurai"),
        Genre("School", "school"),
        Genre("School Life", "school-life"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoons", "webtoons"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}
class KomikIndo : WPMangaStream("Komik Indo (WP Manga Stream)", "https://www.komikindo.web.id", "id") {

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page"
        return GET(url, headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page"
        return GET(url, headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        var builtUrl = if (page == 1) "$baseUrl/manga/" else "$baseUrl/manga/page/$page/"
        if (query != "") {
            builtUrl = if (page == 1) "$baseUrl/search/$query/" else "$baseUrl/search/$query/page/$page/"
        } else if (filters.size > 0) {
            filters.forEach { filter ->
                when (filter) {
                    is GenreListFilter -> {
                        builtUrl = if (page == 1) "$baseUrl/genres/" + filter.toUriPart() else "$baseUrl/genres/" + filter.toUriPart() + "/page/$page/"
                    }
                }
            }
        }
        val url = HttpUrl.parse(builtUrl)!!.newBuilder()
        return GET(url.build().toString(), headers)
    }

    override fun popularMangaSelector() = "div.lchap > .lch > .ch"
    override fun latestUpdatesSelector() = "div.ctf > div.lsmin > div.chl"
    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.thumbnail img").first().attr("src")
        element.select("div.l > h3 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.thumbnail img").first().attr("src")
        element.select("div.chlf > h2 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    @SuppressLint("DefaultLocale")
    override fun mangaDetailsParse(document: Document): SManga {
        val infoElm = document.select(".listinfo > ul > li")
        val manga = SManga.create()
        infoElm.forEach { elmt ->
            val infoTitle = elmt.select("b").text().toLowerCase()
            val infoContent = elmt.text()
            when {
                infoTitle.contains("status") -> manga.status = parseStatus(infoContent)
                infoTitle.contains("author") -> manga.author = infoContent
                infoTitle.contains("artist") -> manga.artist = infoContent
                infoTitle.contains("genres") -> {
                    val genres = mutableListOf<String>()
                    elmt.select("a").forEach {
                        val genre = it.text()
                        genres.add(genre)
                    }
                    manga.genre = genres.joinToString(", ")
                }
            }
        }
        manga.description = document.select("div.rm > span > p:first-child").text()
        manga.thumbnail_url = document.select("div.animeinfo .lm .imgdesc img:first-child").attr("src")
        return manga
    }

    override fun chapterListSelector() = "div.cl ul li"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".leftoff > a").first()
        val chapter = SChapter.create()
        val timeElement = element.select("span.rightoff").first()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(timeElement.text())
        return chapter
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        val sdf = SimpleDateFormat("MMM dd, yyyy")
        val parse = sdf.parse(date)
        val cal = Calendar.getInstance()
        cal.time = parse
        return cal.timeInMillis
    }

    private class GenreListFilter : UriPartFilter("Genre", arrayOf(
        Pair("Default", ""),
        Pair("4-Koma", "4-koma"),
        Pair("Action", "action"),
        Pair("Adventure", "adventure"),
        Pair("Comedy", "comedy"),
        Pair("Cooking", "cooking"),
        Pair("Crime", "crime"),
        Pair("Dark Fantasy", "dark-fantasy"),
        Pair("Demons", "demons"),
        Pair("Drama", "drama"),
        Pair("Ecchi", "ecchi"),
        Pair("Fantasy", "fantasy"),
        Pair("Game", "game"),
        Pair("Gender Bender", "gender-bender"),
        Pair("Harem", "harem"),
        Pair("Historical", "historical"),
        Pair("Horor", "horor"),
        Pair("Horror", "horror"),
        Pair("Isekai", "isekai"),
        Pair("Josei", "josei"),
        Pair("Komik Tamat", "komik-tamat"),
        Pair("Life", "life"),
        Pair("Magic", "magic"),
        Pair("Manhua", "manhua"),
        Pair("Martial Art", "martial-art"),
        Pair("Martial Arts", "martial-arts"),
        Pair("Mature", "mature"),
        Pair("Mecha", "mecha"),
        Pair("Military", "military"),
        Pair("Music", "music"),
        Pair("Mystery", "mystery"),
        Pair("Post-Apocalyptic", "post-apocalyptic"),
        Pair("Psychological", "psychological"),
        Pair("Romance", "romance"),
        Pair("School", "school"),
        Pair("School Life", "school-life"),
        Pair("Sci-Fi", "sci-fi"),
        Pair("Seinen", "seinen"),
        Pair("Shonen", "shonen"),
        Pair("Shoujo", "shoujo"),
        Pair("Shounen", "shounen"),
        Pair("Slice of Life", "slice-of-life"),
        Pair("Sports", "sports"),
        Pair("Super Power", "super-power"),
        Pair("Superheroes", "superheroes"),
        Pair("Supernatural", "supernatural"),
        Pair("Survival", "survival"),
        Pair("Thriller", "thriller"),
        Pair("Tragedy", "tragedy"),
        Pair("Zombies", "zombies")
    ))

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: filter will be ignored when using text search!"),
        GenreListFilter()
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
class MaidManga : WPMangaStream("Maid Manga (WP Manga Stream)", "https://www.maid.my.id", "id") {
    override fun latestUpdatesSelector() = "h2:contains(Update Chapter) + div.row div.col-12"
    override fun latestUpdatesRequest(page: Int): Request {
        val builtUrl = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        return GET(builtUrl)
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("h3 a")
        val imgurl = element.select("div.limit img").attr("src").replace("?resize=100,140", "")
        manga.url = item.attr("href")
        manga.title = item.text()
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "a:containsOwn(Berikutnya)"

    override fun popularMangaRequest(page: Int): Request {
        val builtUrl = if (page == 1) "$baseUrl/advanced-search/?order=popular" else "$baseUrl/advanced-search/page/$page/?order=popular"
        return GET(builtUrl)
    }

    override fun popularMangaSelector() = "div.row div.col-6"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val imgurl = element.select("div.card img").attr("src").replace("?resize=165,225", "")
        manga.url = element.select("div.card a").attr("href")
        manga.title = element.select("div.card img").attr("title")
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl = if (page == 1) "$baseUrl/advanced-search/" else "$baseUrl/advanced-search/page/$page/"
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

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val stringBuilder = StringBuilder()
        val infoElement = document.select("div.infox")
        val author = document.select("span:contains(author)").text().substringAfter("Author: ").substringBefore(" (")
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = document.select("span:contains(Status)").text()
        val desc = document.select("div.sinopsis p")
        infoElement.select("div.gnr a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        if (desc.size > 0) {
            desc.forEach {
                stringBuilder.append(it.text())
                if (it != desc.last())
                    stringBuilder.append("\n\n")
            }
            manga.description = stringBuilder.toString()
        } else
            manga.description = document.select("div.sinopsis").text()

        manga.title = infoElement.select("h1").text()
        manga.author = author
        manga.artist = author
        manga.status = parseStatus(status)
        manga.genre = genres.joinToString(", ")
        manga.description = stringBuilder.toString()
        manga.thumbnail_url = document.select("div.bigcontent img").attr("src")
        return manga
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        // Add date for latest chapter only
        document.select("script.yoast-schema-graph").html()
            .let {
                val date = JSONObject(it).getJSONArray("@graph").getJSONObject(3).getString("dateModified")
                chapters[0].date_upload = parseDate(date)
            }
        return chapters
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX").parse(date).time
    }

    override fun chapterListSelector() = "ul#chapter_list li a:contains(chapter)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a:contains(chapter)")
        val chapter = SChapter.create()
        chapter.url = urlElement.attr("href")
        chapter.name = urlElement.text()
        return chapter
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("div#readerarea img").forEach {
            val url = it.attr("src")
            pages.add(Page(pages.size, "", url))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = throw  UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList(
        Filter.Header("You can combine filter."),
        Filter.Separator(),
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreListFilter(getGenreList())
    )

    private class AuthorFilter : Filter.Text("Author")

    private class YearFilter : Filter.Text("Year")

    private class StatusFilter : Filter.TriState("Completed")

    private class TypeFilter : UriPartFilter("Type", arrayOf(
        Pair("All", ""),
        Pair("Manga", "Manga"),
        Pair("Manhua", "Manhua"),
        Pair("Manhwa", "Manhwa"),
        Pair("One-Shot", "One-Shot"),
        Pair("Doujin", "Doujin")
    ))
}
