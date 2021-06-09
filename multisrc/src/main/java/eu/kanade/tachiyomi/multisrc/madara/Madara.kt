package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue
import kotlin.random.Random

abstract class Madara(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.US)
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // helps with cloudflare for some sources, makes it worse for others; override with empty string if the latter is true
    protected open val userAgentRandomizer = " ${Random.nextInt().absoluteValue}"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:77.0) Gecko/20100101 Firefox/78.0$userAgentRandomizer")

    // Popular Manga

    override fun popularMangaSelector() = "div.page-item-detail"

    open val popularMangaUrlSelector = "div.post-title a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    open fun formBuilder(page: Int, popular: Boolean) = FormBody.Builder().apply {
        add("action", "madara_load_more")
        add("page", (page - 1).toString())
        add("template", "madara-core/content/content-archive")
        add("vars[orderby]", "meta_value_num")
        add("vars[paged]", "1")
        add("vars[posts_per_page]", "20")
        add("vars[post_type]", "wp-manga")
        add("vars[post_status]", "publish")
        add("vars[meta_key]", if (popular) "_wp_manga_views" else "_latest_update")
        add("vars[order]", "desc")
        add("vars[sidebar]", if (popular) "full" else "right")
        add("vars[manga_archives_item_layout]", "big_thumbnail")
    }

    open val formHeaders: Headers by lazy { headersBuilder().build() }

    override fun popularMangaRequest(page: Int): Request {
        return POST("$baseUrl/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, true).build(), CacheControl.FORCE_NETWORK)
    }

    override fun popularMangaNextPageSelector(): String? = "body:not(:has(.no-posts))"

    // Latest Updates

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga {
        // Even if it's different from the popular manga's list, the relevant classes are the same
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return POST("$baseUrl/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, false).build(), CacheControl.FORCE_NETWORK)
    }

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mp = super.latestUpdatesParse(response)
        val mangas = mp.mangas.distinctBy { it.url }
        return MangasPage(mangas, mp.hasNextPage)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (genresList == null)
            genresList = parseGenres(client.newCall(searchMangaRequest(1,"genre", getFilterList())).execute().asJsoup())
        return super.popularMangaParse(response)
    }

    // Search Manga

    open val mangaSubString = "manga"

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)){
            val mangaUrl = "$baseUrl/$mangaSubString/${query.substringAfter(URL_SEARCH_PREFIX)}"
            return client.newCall(GET("$baseUrl/$mangaSubString/${query.substringAfter(URL_SEARCH_PREFIX)}", headers))
                .asObservable().map { response ->
                    MangasPage(listOf(mangaDetailsParse(response.asJsoup()).apply { url = "/$mangaSubString/${query.substringAfter(URL_SEARCH_PREFIX)}/" }), false)
                }
        }
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservable().doOnNext { response ->
                if (!response.isSuccessful) {
                    response.close()
                    // Error message for exceeding last page
                    if (response.code == 404)
                        error("Already on the Last Page!")
                    else throw Exception("HTTP error ${response.code}")
                }
            }
            .map { response ->
                searchMangaParse(response)
            }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (genresList == null)
            genresList = parseGenres(response.asJsoup(response.peekBody(Long.MAX_VALUE).string()))
        return super.searchMangaParse(response)
    }

    private fun parseGenres(document: Document): List<Genre>? {
        return document.selectFirst("div.checkbox-group")?.select("div.checkbox")?.map { li ->
            Genre(li.selectFirst("label").text(), li.selectFirst("input[type=checkbox]").`val`())
        }
    }

    protected open fun searchPage(page: Int): String = "page/$page/"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/${searchPage(page)}".toHttpUrlOrNull()!!.newBuilder()
        url.addQueryParameter("s", query)
        url.addQueryParameter("post_type", "wp-manga")
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("author", filter.state)
                    }
                }
                is ArtistFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("artist", filter.state)
                    }
                }
                is YearFilter -> {
                    if (filter.state.isNotBlank()) {
                        url.addQueryParameter("release", filter.state)
                    }
                }
                is StatusFilter -> {
                    filter.state.forEach {
                        if (it.state) {
                            url.addQueryParameter("status[]", it.id)
                        }
                    }
                }
                is OrderByFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("m_orderby", filter.toUriPart())
                    }
                }
                is AdultContentFilter -> {
                    url.addQueryParameter("adult", filter.toUriPart())
                }
                is GenreConditionFilter -> {
                    url.addQueryParameter("op", filter.toUriPart())
                }
                is GenreList -> {
                    filter.state
                        .filter { it.state }
                        .let { list ->
                            if (list.isNotEmpty()) { list.forEach { genre -> url.addQueryParameter("genre[]", genre.id) } }
                        }
                }
            }
        }
        return GET(url.toString(), headers)
    }

    protected class AuthorFilter : Filter.Text("Author")
    protected class ArtistFilter : Filter.Text("Artist")
    protected class YearFilter : Filter.Text("Year of Released")
    protected class StatusFilter(status: List<Tag>) : Filter.Group<Tag>("Status", status)

    protected class OrderByFilter : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("<select>", ""),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Rating", "rating"),
            Pair("Trending", "trending"),
            Pair("Most Views", "views"),
            Pair("New", "new-manga")
        )
    )

    protected class GenreConditionFilter : UriPartFilter(
        "Genre condition",
        arrayOf(
            Pair("or", ""),
            Pair("and", "1")
        )
    )

    protected class AdultContentFilter : UriPartFilter(
        "Adult Content",
        arrayOf(
            Pair("All", ""),
            Pair("None", "0"),
            Pair("Only", "1")
        )
    )

    protected class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
    class Genre(name: String, val id: String = name) : Filter.CheckBox(name)

    private var genresList: List<Genre>? = null

    protected open fun getGenreList(): List<Genre> {
        // Filters are fetched immediately once an extension loads
        // We're only able to get filters after a loading the manga directory, and resetting
        // the filters is the only thing that seems to reinflate the view
        return genresList ?: listOf(Genre("Press reset to attempt to fetch genres", ""))
    }

    override fun getFilterList() = FilterList(
        AuthorFilter(),
        ArtistFilter(),
        YearFilter(),
        StatusFilter(getStatusList()),
        OrderByFilter(),
        AdultContentFilter(),
        Filter.Separator(),
        Filter.Header("Genres may not work for all sources"),
        GenreConditionFilter(),
        GenreList(getGenreList())
    )

    protected fun getStatusList() = listOf(
        Tag("end", "Completed"),
        Tag("on-going", "Ongoing"),
        Tag("canceled", "Canceled"),
        Tag("on-hold", "On Hold")
    )

    open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    open class Tag(val id: String, name: String) : Filter.CheckBox(name)

    override fun searchMangaSelector() = "div.c-tabs-item__content"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select("div.post-title a").first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }
            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun searchMangaNextPageSelector(): String? = "div.nav-previous, nav.navigation-ajax, a.nextpostslink"

    // Manga Details Parse

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        with(document) {
            select("div.post-title h3").first()?.let {
                manga.title = it.ownText()
            }
            select("div.author-content").first()?.let {
                if (it.text().notUpdating()) manga.author = it.text()
            }
            select("div.artist-content").first()?.let {
                if (it.text().notUpdating()) manga.artist = it.text()
            }
            select("div.description-summary div.summary__content").let {
                if (it.select("p").text().isNotEmpty()) {
                    manga.description = it.select("p").joinToString(separator = "\n\n") { p ->
                        p.text().replace("<br>", "\n")
                    }
                } else {
                    manga.description = it.text()
                }
            }
            select("div.summary_image img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
            select("div.summary-content").last()?.let {
                manga.status = when (it.text()) {
                    // I don't know what's the corresponding for COMPLETED and LICENSED
                    // There's no support for "Canceled" or "On Hold"
                    "Completed", "Completo", "Concluído", "Concluido", "Terminé" -> SManga.COMPLETED
                    "OnGoing", "Продолжается", "Updating", "Em Lançamento", "Em andamento", "Em Andamento", "En cours", "Ativo", "Lançando" -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
            val genres = select("div.genres-content a")
                .map { element -> element.text().toLowerCase(Locale.ROOT) }
                .toMutableSet()

            // add tag(s) to genre
            select("div.tags-content a").forEach { element ->
                if (genres.contains(element.text()).not()) {
                    genres.add(element.text().toLowerCase(Locale.ROOT))
                }
            }

            // add manga/manhwa/manhua thinggy to genre
            document.select(seriesTypeSelector).firstOrNull()?.ownText()?.let {
                if (it.isEmpty().not() && it.notUpdating() && it != "-" && genres.contains(it).not()) {
                    genres.add(it.toLowerCase(Locale.ROOT))
                }
            }

            manga.genre = genres.toList().joinToString(", ") { it.capitalize(Locale.ROOT) }

            // add alternative name to manga description
            document.select(altNameSelector).firstOrNull()?.ownText()?.let {
                if (it.isEmpty().not() && it.notUpdating()) {
                    manga.description += when {
                        manga.description.isNullOrEmpty() -> altName + it
                        else -> "\n\n$altName" + it
                    }
                }
            }
        }

        return manga
    }

    open val seriesTypeSelector = ".post-content_item:contains(Type) .summary-content"
    open val altNameSelector = ".post-content_item:contains(Alt) .summary-content"
    open val altName = "Alternative Name" + ": "
    open val updatingRegex = "Updating|Atualizando".toRegex(RegexOption.IGNORE_CASE)

    public fun String.notUpdating(): Boolean {
        return this.contains(updatingRegex).not()
    }

    protected open fun imageFromElement(element: Element): String? {
        return when {
            element.hasAttr("data-src") -> element.attr("abs:data-src")
            element.hasAttr("data-lazy-src") -> element.attr("abs:data-lazy-src")
            element.hasAttr("srcset") -> element.attr("abs:srcset").substringBefore(" ")
            else -> element.attr("abs:src")
        }
    }

    protected fun getXhrChapters(mangaId: String): Document {
        val xhrHeaders = headersBuilder().add("Content-Type: application/x-www-form-urlencoded; charset=UTF-8")
            .add("Referer", baseUrl)
            .build()
        val body = "action=manga_get_chapters&manga=$mangaId".toRequestBody(null)
        return client.newCall(POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, body)).execute().asJsoup()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val dataIdSelector = "div[id^=manga-chapters-holder]"

        return document.select(chapterListSelector())
            .let { elements ->
                if (elements.isEmpty() && !document.select(dataIdSelector).isNullOrEmpty())
                    getXhrChapters(document.select(dataIdSelector).attr("data-id")).select(chapterListSelector())
                else elements
            }
            .map { chapterFromElement(it) }
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    open val chapterUrlSelector = "a"

    open val chapterUrlSuffix = "?style=list"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        with(element) {
            select(chapterUrlSelector).first()?.let { urlElement ->
                chapter.url = urlElement.attr("abs:href").let {
                    it.substringBefore("?style=paged") + if (!it.endsWith(chapterUrlSuffix)) chapterUrlSuffix else ""
                }
                chapter.name = urlElement.text()
            }
            // Dates can be part of a "new" graphic or plain text
            chapter.date_upload = select("img").firstOrNull()?.attr("alt")?.let { parseRelativeDate(it) }
                ?: parseChapterDate(select("span.chapter-release-date i").firstOrNull()?.text())
        }


        return chapter
    }

    open fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            date.endsWith(" ago", ignoreCase = true) -> {
                parseRelativeDate(date)
            }
            // Handle translated 'ago' in Portuguese.
            date.endsWith(" atrás", ignoreCase = true) -> {
                parseRelativeDate(date)
            }
            // Handle translated 'ago' in Turkish.
            date.endsWith(" önce", ignoreCase = true) -> {
                parseRelativeDate(date)
            }
            // Handle 'yesterday' and 'today', using midnight
            date.startsWith("year", ignoreCase = true) -> {
                Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_MONTH, -1) // yesterday
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            date.startsWith("today", ignoreCase = true) -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            date.contains(Regex("""\d(st|nd|rd|th)""")) -> {
                // Clean date (e.g. 5th December 2019 to 5 December 2019) before parsing it
                date.split(" ").map {
                    if (it.contains(Regex("""\d\D\D"""))) {
                        it.replace(Regex("""\D"""), "")
                    } else {
                        it
                    }
                }
                    .let { dateFormat.tryParse(it.joinToString(" ")) }
            }
            else -> dateFormat.tryParse(date)
        }
    }

    // Parses dates in this form:
    // 21 horas ago
    private fun parseRelativeDate(date: String): Long {
        val number = Regex("""(\d+)""").find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            WordSet("hari", "gün", "jour", "día", "dia", "day").anyWordIn(date) -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            WordSet("jam", "saat", "heure", "hora", "hour").anyWordIn(date) -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            WordSet("menit", "dakika", "min", "minute", "minuto").anyWordIn(date) -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            WordSet("detik", "segundo", "second").anyWordIn(date) -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            WordSet("month").anyWordIn(date) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            WordSet("year").anyWordIn(date) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    open val pageListParseSelector = "div.page-break, li.blocks-gallery-item"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector).mapIndexed { index, element ->
            Page(
                index,
                document.location(),
                element.select("img").first()?.let {
                    it.absUrl(if (it.hasAttr("data-src")) "data-src" else "src")
                }
            )
        }
    }

    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers.newBuilder().set("Referer", page.url).build())
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Not used")

    companion object {
        const val URL_SEARCH_PREFIX = "SLUG:"
    }
}

class WordSet(private vararg val words: String) { fun anyWordIn(dateString: String): Boolean = words.any { dateString.contains(it, ignoreCase = true) } }
