package eu.kanade.tachiyomi.extension.all.mangapark

import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.concurrent.TimeUnit
import okhttp3.Response
import rx.Observable

open class MangaPark(
    override val lang: String,
    private val siteLang: String
) : ParsedHttpSource() {

    override val name: String = "MangaPark v3"
    override val baseUrl: String = "https://mangapark.net"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/browse?sort=update&page=$page")
    }

    override fun latestUpdatesSelector(): String {
        return "div#subject-list div.col"
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("a.position-relative")
        val imgurl = item.select("img").attr("abs:src")
        manga.setUrlWithoutDomain(item.attr("href"))
        manga.title = element.select("a.fw-bold").text()
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "div#mainer nav.d-none .pagination .page-item:last-of-type:not(.disabled)"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse?sort=d007&page=$page")
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith("ID:") -> {
                val id = query.substringAfter("ID:")
                client.newCall(GET("$baseUrl/comic/$id", headers)).asObservableSuccess()
                    .map { response ->
                        queryIDParse(response, id)
                    }
            }

            query.isNotBlank() -> {
                val url = "$baseUrl/search?word=$query&page=$page"
                client.newCall(GET(url, headers)).asObservableSuccess()
                    .map { response ->
                        queryParse(response)
                    }
            }

            else -> {
                val sortFilter = filters.findInstance<SortFilter>()!!
                val reverseSortFilter = filters.findInstance<ReverseSortFilter>()!!
                val statusFilter = filters.findInstance<StatusFilter>()!!
                val genreFilter = filters.findInstance<GenreGroupFilter>()!!
                val minChapterFilter = filters.findInstance<MinChapterTextFilter>()!!
                val maxChapterFilter = filters.findInstance<MaxChapterTextFilter>()!!
                val url = "$baseUrl/browse".toHttpUrlOrNull()!!.newBuilder()
                url.addQueryParameter("page", page.toString())


                with (sortFilter) {
                    if (reverseSortFilter.state) {
                        url.addQueryParameter("sort","${this.selected}.az")
                    } else {
                        url.addQueryParameter("sort","${this.selected}.za")
                    }
                }

                with (genreFilter) {
                    url.addQueryParameter("genres", included.joinToString(",") + "|" + excluded.joinToString(",")
                    )
                }

                with (statusFilter) {
                    url.addQueryParameter("release", this.selected)
                }

                if (maxChapterFilter.state.isNotEmpty() or minChapterFilter.state.isNotEmpty()) {
                    url.addQueryParameter("chapters", minChapterFilter.state + "-" + maxChapterFilter.state)
                }

                client.newCall(GET(url.build().toString(), headers)).asObservableSuccess()
                    .map { response ->
                        queryParse(response)
                    }
            }
        }
    }

    private fun queryIDParse(response: Response, id: String): MangasPage {
        val document = response.asJsoup()
        val infoElement = document.select("div#mainer div.container-fluid")
        val manga = SManga.create()
        manga.title = infoElement.select("h3").text()
        manga.thumbnail_url = document.select("div.attr-cover img")
            .attr("abs:src")
        manga.url = infoElement.select("h3 a").attr("abs:href")
        return MangasPage(listOf(manga), false)
    }

    private fun queryParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()
        val document = response.asJsoup()
        document.select("div#search-list div.col").forEach { element ->
            mangas.add(latestUpdatesFromElement(element))
        }
        val nextPage = document.select(latestUpdatesNextPageSelector()).first() != null
        return MangasPage(mangas, nextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")
    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#mainer div.container-fluid")
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = infoElement.select("div.attr-item:contains(status) span").text()
        infoElement.select("div.attr-item:contains(genres) span").text().split(
            " / "
                .toRegex()
        ).forEach { element ->
            genres.add(element)
        }
        manga.title = infoElement.select("h3").text()
        manga.author = infoElement.select("div.attr-item:contains(author) a:first-child").text().split(" / ".toRegex()).joinToString(", ")
        manga.status = parseStatus(status)
        manga.genre = infoElement.select("div.attr-item:contains(genre) span").text()
        manga.description = infoElement.select("div.limit-height-body").text() //Needs to put new line before extra and notice and stuff.
        manga.thumbnail_url = document.select("div.attr-cover img")
            .attr("abs:src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Hiatus") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "div#mainer div.container-fluid div.episode-list div#episodes-lang-$siteLang div.p-2"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val urlElement = element.select("a.chapt")
        val url = urlElement.attr("href")
        val time = element.select("div.extra > i.ps-2").text()
        chapter.setUrlWithoutDomain(url)
        chapter.name = urlElement.text()
        chapter.chapter_number = url.split("/".toRegex()).last().toFloat()

        if (time != "") {
            chapter.date_upload = parseChapterDate(time)
        }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        return when {
            "secs" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, value * -1)
            }.timeInMillis
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
            "sec" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, value * -1)
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

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val viewer = document.select("div#viewer")
        val pages = mutableListOf<Page>()
        viewer.select("div.item").forEach() { i ->
            val index = i.select("span").text().split("/".toRegex())[0].toInt() - 1
            val imageUrl = i.select("img").attr("abs:src")
            pages.add(Page(index, "", imageUrl))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    // Done Down

    override fun getFilterList() = FilterList(
        //LetterFilter(),
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortFilter(getSortFilter(), 10),
        ReverseSortFilter(),
        StatusFilter(getStatusFilter(), 0),
        MinChapterTextFilter(),
        MaxChapterTextFilter(),
        GenreGroupFilter(getGenreFilter()),
    )

    class SelectFilterOption(val name: String, val value: String)
    class CheckboxFilterOption(val value: String, name: String, default: Boolean = false) : Filter.CheckBox(name, default)
    class TriStateFilterOption(val value: String, name: String, default: Int = 0) : Filter.TriState(name, default)

    abstract class SelectFilter(name: String, private val options: List<SelectFilterOption>, default: Int = 0) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
        val selected: String
            get() = options[state].value
    }
    abstract class CheckboxGroupFilter(name: String, options: List<CheckboxFilterOption>) : Filter.Group<CheckboxFilterOption>(name, options) {
        val selected: List<String>
            get() = state.filter { it.state }.map { it.value }
    }
    abstract class TriStateGroupFilter(name: String, options: List<TriStateFilterOption>) : Filter.Group<TriStateFilterOption>(name, options) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.value }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.value }
    }
    abstract class TextFilter(name: String) : Filter.Text(name)

    class SortFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Sort By", options, default)
    class ReverseSortFilter(default: Boolean = false) : Filter.CheckBox("Revers Sort", default)
    class StatusFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Status", options, default)
    class GenreGroupFilter(options: List<TriStateFilterOption>) : TriStateGroupFilter("Genre", options)
    class MinChapterTextFilter : TextFilter("Min. Chapters")
    class MaxChapterTextFilter : TextFilter("Max. Chapters")

    private fun getSortFilter() = listOf(
        SelectFilterOption("Rating", "rating"),
        SelectFilterOption("Comments", "comments"),
        SelectFilterOption("Discuss", "discuss"),
        SelectFilterOption("Update", "update"),
        SelectFilterOption("Create", "create"),
        SelectFilterOption("Name", "name"),
        SelectFilterOption("Total Views", "d000"),
        SelectFilterOption("Most Views 360 days", "d360"),
        SelectFilterOption("Most Views 180 days", "d180"),
        SelectFilterOption("Most Views 90 days", "d090"),
        SelectFilterOption("Most Views 30 days", "d030"),
        SelectFilterOption("Most Views 7 days", "d007"),
        SelectFilterOption("Most Views 24 hours", "h024"),
        SelectFilterOption("Most Views 12 hours", "h012"),
        SelectFilterOption("Most Views 6 hours", "h006"),
        SelectFilterOption("Most Views 60 minutes", "h001"),
    )

    private fun getStatusFilter() = listOf(
        SelectFilterOption("All", ""),
        SelectFilterOption("Pending", "pending"),
        SelectFilterOption("Ongoing", "ongoing"),
        SelectFilterOption("Completed", "completed"),
        SelectFilterOption("Hiatus", "hiatus"),
        SelectFilterOption("Cancelled", "cancelled"),
    )

    private fun getGenreFilter() = listOf(
        TriStateFilterOption("artbook","Artbook"),
        TriStateFilterOption("cartoon","Cartoon"),
        TriStateFilterOption("comic","Comic"),
        TriStateFilterOption("doujinshi","Doujinshi"),
        TriStateFilterOption("imageset","Imageset"),
        TriStateFilterOption("manga","Manga"),
        TriStateFilterOption("manhua","Manhua"),
        TriStateFilterOption("manhwa","Manhwa"),
        TriStateFilterOption("webtoon","Webtoon"),
        TriStateFilterOption("western","Western"),
        TriStateFilterOption("josei","Josei"),
        TriStateFilterOption("seinen","Seinen"),
        TriStateFilterOption("shoujo","Shoujo"),
        TriStateFilterOption("shoujo_ai","Shoujo ai"),
        TriStateFilterOption("shounen","Shounen"),
        TriStateFilterOption("shounen_ai","Shounen ai"),
        TriStateFilterOption("yaoi","Yaoi"),
        TriStateFilterOption("yuri","Yuri"),
        TriStateFilterOption("ecchi","Ecchi"),
        TriStateFilterOption("mature","Mature"),
        TriStateFilterOption("adult","Adult"),
        TriStateFilterOption("gore","Gore"),
        TriStateFilterOption("violence","Violence"),
        TriStateFilterOption("smut","Smut"),
        TriStateFilterOption("hentai","Hentai"),
        TriStateFilterOption("_4_koma","4-Koma"),
        TriStateFilterOption("action","Action"),
        TriStateFilterOption("adaptation","Adaptation"),
        TriStateFilterOption("adventure","Adventure"),
        TriStateFilterOption("aliens","Aliens"),
        TriStateFilterOption("animals","Animals"),
        TriStateFilterOption("anthology","Anthology"),
        TriStateFilterOption("cars","cars"),
        TriStateFilterOption("comedy","Comedy"),
        TriStateFilterOption("cooking","Cooking"),
        TriStateFilterOption("crime","crime"),
        TriStateFilterOption("crossdressing","Crossdressing"),
        TriStateFilterOption("delinquents","Delinquents"),
        TriStateFilterOption("dementia","Dementia"),
        TriStateFilterOption("demons","Demons"),
        TriStateFilterOption("drama","Drama"),
        TriStateFilterOption("fantasy","Fantasy"),
        TriStateFilterOption("fan_colored","Fan-Colored"),
        TriStateFilterOption("full_color","Full Color"),
        TriStateFilterOption("game","Game"),
        TriStateFilterOption("gender_bender","Gender Bender"),
        TriStateFilterOption("genderswap","Genderswap"),
        TriStateFilterOption("ghosts","Ghosts"),
        TriStateFilterOption("gyaru","Gyaru"),
        TriStateFilterOption("harem","Harem"),
        TriStateFilterOption("harlequin","Harlequin"),
        TriStateFilterOption("historical","Historical"),
        TriStateFilterOption("horror","Horror"),
        TriStateFilterOption("incest","Incest"),
        TriStateFilterOption("isekai","Isekai"),
        TriStateFilterOption("kids","Kids"),
        TriStateFilterOption("loli","Loli"),
        TriStateFilterOption("lolicon","lolicon"),
        TriStateFilterOption("magic","Magic"),
        TriStateFilterOption("magical_girls","Magical Girls"),
        TriStateFilterOption("martial_arts","Martial Arts"),
        TriStateFilterOption("mecha","Mecha"),
        TriStateFilterOption("medical","Medical"),
        TriStateFilterOption("military","Military"),
        TriStateFilterOption("monster_girls","Monster Girls"),
        TriStateFilterOption("monsters","Monsters"),
        TriStateFilterOption("music","Music"),
        TriStateFilterOption("mystery","Mystery"),
        TriStateFilterOption("netorare","Netorare/NTR"),
        TriStateFilterOption("ninja","Ninja"),
        TriStateFilterOption("office_workers","Office Workers"),
        TriStateFilterOption("oneshot","Oneshot"),
        TriStateFilterOption("parody","parody"),
        TriStateFilterOption("philosophical","Philosophical"),
        TriStateFilterOption("police","Police"),
        TriStateFilterOption("post_apocalyptic","Post-Apocalyptic"),
        TriStateFilterOption("psychological","Psychological"),
        TriStateFilterOption("reincarnation","Reincarnation"),
        TriStateFilterOption("reverse_harem","Reverse Harem"),
        TriStateFilterOption("romance","Romance"),
        TriStateFilterOption("samurai","Samurai"),
        TriStateFilterOption("school_life","School Life"),
        TriStateFilterOption("sci_fi","Sci-Fi"),
        TriStateFilterOption("shota","Shota"),
        TriStateFilterOption("shotacon","shotacon"),
        TriStateFilterOption("slice_of_life","Slice of Life"),
        TriStateFilterOption("sm_bdsm","SM/BDSM"),
        TriStateFilterOption("space","Space"),
        TriStateFilterOption("sports","Sports"),
        TriStateFilterOption("super_power","Super Power"),
        TriStateFilterOption("superhero","Superhero"),
        TriStateFilterOption("supernatural","Supernatural"),
        TriStateFilterOption("survival","Survival"),
        TriStateFilterOption("thriller","Thriller"),
        TriStateFilterOption("time_travel","Time Travel"),
        TriStateFilterOption("traditional_games","Traditional Games"),
        TriStateFilterOption("tragedy","Tragedy"),
        TriStateFilterOption("vampires","Vampires"),
        TriStateFilterOption("video_games","Video Games"),
        TriStateFilterOption("virtual_reality","Virtual Reality"),
        TriStateFilterOption("wuxia","Wuxia"),
        TriStateFilterOption("xianxia","Xianxia"),
        TriStateFilterOption("xuanhuan","Xuanhuan"),
        TriStateFilterOption("zombies","Zombies"),
        // Hidden Genres
        TriStateFilterOption("award_winning", "Award Winning"),
        TriStateFilterOption("youkai", "Youkai"),
        TriStateFilterOption("uncategorized", "Uncategorized")
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
