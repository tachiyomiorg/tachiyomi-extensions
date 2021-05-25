package eu.kanade.tachiyomi.extension.all.comickfun

import android.os.Build
import android.text.Html
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import kotlin.math.pow
import kotlin.math.truncate

const val SEARCH_PAGE_LIMIT = 100

abstract class ComickFun(override val lang: String, private val comickFunLang: String) : HttpSource() {
    override val name = "Comick.fun"
    final override val baseUrl = "https://comick.fun"
    private val apiBase = "$baseUrl/api"
    override val supportsLatest = true

    private val mangaIdCache = mutableMapOf<String, Int>()

    final override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
    }

    final override val client: OkHttpClient

    init {
        val rateLimiter = RateLimitInterceptor(2)
        val builder = super.client.newBuilder()
        if (comickFunLang != "all")
        // Add interceptor to enforce language
            builder.addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    when {
                        request.url.toString().contains(Regex("""$apiBase/(?:get_chapters|get_newest_chapters)""")) ->
                            chain.proceed(request.newBuilder().url(request.url.newBuilder().addQueryParameter("lang", comickFunLang).build()).build())
                        else -> chain.proceed(request)
                    }
                }
            )

        /** Rate Limiter, shamelessly ~stolen from~ inspired by MangaDex
         * Rate limits all requests that go to the baseurl
         */
        builder.addNetworkInterceptor(
            Interceptor { chain ->
                return@Interceptor when (chain.request().url.toString().startsWith(baseUrl)) {
                    false -> chain.proceed(chain.request())
                    true -> rateLimiter.intercept(chain)
                }
            }
        )
        this.client = builder.build()
    }

    /**  Utils **/

    /**
     * Parses a json object with information suitable for showing an entry of a manga within a
     * catalogue
     *
     * Attempts to cache the manga's numerical Id
     *
     * @return SManga - with url, thumbnail_url and title set
     */
    private fun parseMangaObj(it: JsonElement) = it.asJsonObject.let { info ->
        info["id"]?.asInt?.let { mangaIdCache.getOrPut(info["slug"].asString, { it }) }
        val thumbnail = info["coverURL"]?.nullString
            ?: info["md_covers"]?.asJsonArray?.get(0)?.asJsonObject?.let { cover ->
                cover["gpurl"]?.nullString ?: "$baseUrl${cover["url"].asString}"
            }

        SManga.create().apply {
            url = "/comic/${info["slug"].asString}"
            thumbnail_url = thumbnail
            title = info["title"].asString
        }
    }

    /** Returns an observable which emits a single value -> the manga's id **/
    private fun chapterId(manga: SManga): Observable<Int> {
        val mangaSlug = slug(manga)
        return mangaIdCache[mangaSlug]?.let { Observable.just(it) }
            ?: fetchMangaDetails(manga).map { mangaIdCache[mangaSlug] }
    }

    private fun parseStatus(status: Int) = when (status) {
        1 -> SManga.ONGOING
        2 -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    /** Attempts to parse an ISO-8601 compliant Date Time string with offset to epoch.
     * @returns epochtime on success, 0 on failure
     **/
    private fun parseISO8601(s: String): Long {
        var fractionalPart_ms: Long = 0
        val sNoFraction = Regex("""\.\d+""").replace(s) { match ->
            fractionalPart_ms = truncate(
                match.value.substringAfter(".").toFloat() * 10.0f.pow(-(match.value.length - 1)) * // seconds
                    1000 // milliseconds
            ).toLong()
            ""
        }

        val ret = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ").parse(sNoFraction)?.let {
            fractionalPart_ms + it.time
        } ?: 0
        return ret
    }

    /** Returns an identifier referred to as `hid` for chapter **/
    private fun hid(chapter: SChapter) = "$baseUrl${chapter.url}".toHttpUrl().pathSegments[2].substringBefore("-")

    /** Returns an identifier referred to as a  `slug` for manga **/
    private fun slug(manga: SManga) = "$baseUrl${manga.url}".toHttpUrl().pathSegments[1]

    private fun formatChapterTitle(title: String?, chap: String?, vol: String?): String {
        val numNonNull = listOfNotNull(title.takeIf { !it.isNullOrBlank() }, chap, vol).size
        if (numNonNull == 0) throw RuntimeException("formatChapterTitle requires at least one non-null argument")

        var formattedTitle = StringBuilder()
        if (vol != null) formattedTitle.append("${numNonNull.takeIf { it > 1 }?.let { "Vol." } ?: "Volume"} $vol")
        if (vol != null && chap != null) formattedTitle.append(", ")
        if (chap != null) formattedTitle.append("${numNonNull.takeIf { it > 1 }?.let { "Ch." } ?: "Chapter"} $chap")
        if (!title.isNullOrBlank()) formattedTitle.append("${numNonNull.takeIf { it > 1 }?.let { ": " } ?: ""} $title")
        return formattedTitle.toString()
    }

    /** Popular Manga **/

    override fun fetchPopularManga(page: Int) = fetchSearchManga(page, "", FilterList(emptyList()))
    override fun popularMangaRequest(page: Int) = throw UnsupportedOperationException("Not used")
    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException("Not used")

    /** Latest Manga **/
    override fun latestUpdatesParse(response: Response): MangasPage {
        val noResults = MangasPage(emptyList(), false)
        if (response.code == 204)
            return noResults
        return JsonParser.parseString(response.body!!.string()).obj["data"]?.array?.let { manga ->
            MangasPage(manga.map { parseMangaObj(it["md_comics"]) }, true)
        } ?: noResults
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiBase/get_newest_chapters".toHttpUrl().newBuilder()
            .addQueryParameter("page", "${page - 1}")
            .addQueryParameter("device-memory", "8")
        return GET("$url", headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SLUG_SEARCH_PREFIX))
            return super.fetchSearchManga(page, query, filters)

        // deeplinking
        val potentialUrl = "/comic/${query.substringAfter(SLUG_SEARCH_PREFIX)}"
        return fetchMangaDetails(SManga.create().apply { this.url = potentialUrl })
            .map { MangasPage(listOf(it.apply { this.url = potentialUrl }), false) }
            .onErrorReturn { MangasPage(emptyList(), false) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiBase.toHttpUrl().newBuilder()
        if (query.isNotEmpty()) {
            url.addPathSegment("search_title")
                .addQueryParameter("t", "1")
                .addQueryParameter("q", query)
        } else {
            url.addPathSegment("search")
                .addQueryParameter("page", "$page")
                .addQueryParameter("limit", "$SEARCH_PAGE_LIMIT")
            filters.forEach { filter ->
                when (filter) {
                    is UrlEncoded -> filter.encode(url)
                }
            }
        }
        return GET("$url", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = JsonParser.parseString(response.body!!.string()).let {
        if (it.isJsonObject)
            MangasPage(it["comics"].array.map(::parseMangaObj), it["comics"].array.size() == SEARCH_PAGE_LIMIT)
        else // search_title isn't paginated
            MangasPage(it.array.map(::parseMangaObj), false)
    }

    /** Manga Details **/

    private fun apiMangaDetailsRequest(manga: SManga): Request {
        return GET("$apiBase/get_comic?slug=${slug(manga)}", headers)
    }

    // Shenanigans to allow "open in webview" to show a webpage instead of JSON
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(apiMangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    override fun mangaDetailsParse(response: Response) = JsonParser.parseString(response.body!!.string())["data"].let { data ->
        fun cleanDesc(s: String) = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(s)
            ).toString()

        fun nameList(e: JsonElement?) = e?.array?.asSequence()?.map { it["name"].asString }
        data["comic"]["id"].asInt.let { mangaIdCache.getOrPut(response.request.url.queryParameter("slug")!!, { it }) }
        SManga.create().apply {
            title = data["comic"]["title"].asString
            thumbnail_url = data["coverURL"].asString
            description = cleanDesc(data["comic"]["desc"].asString)
            status = parseStatus(data["comic"]["status"].asInt)
            artist = nameList(data["artists"])?.joinToString(", ")
            author = nameList(data["authors"])?.joinToString(", ")
            genre = (
                (nameList(data["genres"]) ?: sequenceOf()) + sequence {
                    data["demographic"].nullString?.let { yield(it) }
                    mapOf("kr" to "Manhwa", "jp" to "Manga", "cn" to "Manhua")[data["comic"]["country"].nullString]
                        ?.let { yield(it) }
                }
                ).joinToString(", ")
        }
    }

    /** Chapter List **/

    private fun chapterListRequest(page: Int, mangaId: Int) =
        GET("$apiBase/get_chapters?comicid=$mangaId&page=$page&limit=$SEARCH_PAGE_LIMIT", headers)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            chapterId(manga).concatMap { id ->
                /**
                 * Returns an observable which emits the list of chapters found on a page,
                 * for every page starting from specified page
                 */
                fun getAllPagesFrom(page: Int, pred: Observable<List<SChapter>> = Observable.just(emptyList())): Observable<List<SChapter>> =
                    client.newCall(chapterListRequest(page, id))
                        .asObservableSuccess()
                        .concatMap { response ->
                            val cp = chapterListParse(response).map { it.apply { this.url = "${manga.url}${this.url}" } }
                            if (cp.size == SEARCH_PAGE_LIMIT)
                                getAllPagesFrom(page + 1, pred = pred.concatWith(Observable.just(cp))) // tail call to avoid blowing the stack
                            else // by the pigeon-hole principle
                                pred.concatWith(Observable.just(cp))
                        }
                getAllPagesFrom(1).reduce(List<SChapter>::plus)
            }
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    override fun chapterListParse(response: Response) = JsonParser.parseString(response.body!!.string()).obj["data"]["chapters"].array.map { elem ->
        val chapter = elem.asJsonObject
        val num = chapter["chap"].nullString ?: "-1"
        SChapter.create().apply {
            date_upload = parseISO8601(chapter["created_at"].asString)
            name = formatChapterTitle(chapter["title"].nullString, chapter["chap"].nullString, chapter["vol"].nullString)
            chapter_number = num.toFloat()
            url = "/${chapter["hid"].asString}-chapter-${chapter["chap"].nullString}-${chapter["iso639_1"].asString}" // incomplete, is finished in fetchChapterList
            scanlator = chapter.get("md_groups")?.array?.get(0)?.obj?.get("title")?.asString
        }
    }

    /** Page List **/

    override fun pageListRequest(chapter: SChapter) = GET("$apiBase/get_chapter?hid=${hid(chapter)}", headers, CacheControl.FORCE_NETWORK)

    override fun pageListParse(response: Response) = JsonParser.parseString(response.body!!.string())["data"]["chapter"]["images"].array.mapIndexed { i, url ->
        Page(i, imageUrl = url.asString)
    }

    override fun imageUrlParse(response: Response) = "" // idk what this does, leave me alone kotlin

    /** Filters **/

    private interface UrlEncoded {
        fun encode(url: HttpUrl.Builder)
    }

    private interface ArrayUrlParam : UrlEncoded {
        val paramName: String
        val selected: Sequence<LabeledValue>
        override fun encode(url: HttpUrl.Builder) {
            url.addQueryParameter(paramName, selected.joinToString(",") { it.value })
        }
    }

    private interface QueryParam : UrlEncoded {
        val paramName: String
        val selected: LabeledValue
        override fun encode(url: HttpUrl.Builder) {
            url.addQueryParameter(paramName, selected.value)
        }
    }

    // essentially a named pair
    protected class LabeledValue(private val displayname: String, private val _value: String?) {
        val value: String get() = _value ?: displayname
        override fun toString(): String = displayname
    }

    private open class Select<T>(header: String, values: Array<T>, state: Int = 0) : Filter.Select<T>(header, values, state) {
        val selected: T
            get() = this.values[this.state]
    }

    private open class MultiSelect<T>(header: String, val elems: List<T>) :
        Filter.Group<Filter.CheckBox>(header, elems.map { object : Filter.CheckBox("$it") {} }) {
        val selected: Sequence<T>
            get() = this.elems.asSequence().filterIndexed { i, _ -> this.state[i].state }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        GenreFilter(),
        DemographicFilter(),
        TypesFilter(),
        CreatedAtFilter(),
        MinChaptersFilter()
    )

    private fun GenreFilter() = object : MultiSelect<LabeledValue>("Genre", getGenreList()), ArrayUrlParam {
        override val paramName = "genres"
    }

    private fun DemographicFilter() = object : MultiSelect<LabeledValue>("Demographic", getDemographics()), ArrayUrlParam {
        override val paramName = "demographic"
    }

    private fun TypesFilter() = object : MultiSelect<LabeledValue>("Type", getContentType()), ArrayUrlParam {
        override val paramName = "country"
    }

    private fun CreatedAtFilter() = object : Select<LabeledValue>("Created At", getCreatedAt()), QueryParam {
        override val paramName = "time"
    }

    private fun MinChaptersFilter() = object : Filter.Text("Minimum Chapters", ""), UrlEncoded {
        override fun encode(url: HttpUrl.Builder) {
            if (state.isBlank()) return
            state.toIntOrNull()?.takeUnless { it < 0 }?.let {
                url.addQueryParameter("minimum", "$it")
            } ?: throw RuntimeException("Minimum must be an integer greater than 0")
        }
    }

    protected fun getGenreList() = listOf(
        LabeledValue("4-Koma", "4-koma"),
        LabeledValue("Action", "action"),
        LabeledValue("Adaptation", "adaptation"),
        LabeledValue("Adult", "adult"),
        LabeledValue("Adventure", "adventure"),
        LabeledValue("Aliens", "aliens"),
        LabeledValue("Animals", "animals"),
        LabeledValue("Anthology", "anthology"),
        LabeledValue("Award Winning", "award-winning"),
        LabeledValue("Comedy", "comedy"),
        LabeledValue("Cooking", "cooking"),
        LabeledValue("Crime", "crime"),
        LabeledValue("Crossdressing", "crossdressing"),
        LabeledValue("Delinquents", "delinquents"),
        LabeledValue("Demons", "demons"),
        LabeledValue("Doujinshi", "doujinshi"),
        LabeledValue("Drama", "drama"),
        LabeledValue("Ecchi", "ecchi"),
        LabeledValue("Fan Colored", "fan-colored"),
        LabeledValue("Fantasy", "fantasy"),
        LabeledValue("Full Color", "full-color"),
        LabeledValue("Gender Bender", "gender-bender"),
        LabeledValue("Genderswap", "genderswap"),
        LabeledValue("Ghosts", "ghosts"),
        LabeledValue("Gore", "gore"),
        LabeledValue("Gyaru", "gyaru"),
        LabeledValue("Harem", "harem"),
        LabeledValue("Historical", "historical"),
        LabeledValue("Horror", "horror"),
        LabeledValue("Incest", "incest"),
        LabeledValue("Isekai", "isekai"),
        LabeledValue("Loli", "loli"),
        LabeledValue("Long Strip", "long-strip"),
        LabeledValue("Mafia", "mafia"),
        LabeledValue("Magic", "magic"),
        LabeledValue("Magical Girls", "magical-girls"),
        LabeledValue("Martial Arts", "martial-arts"),
        LabeledValue("Mature", "mature"),
        LabeledValue("Mecha", "mecha"),
        LabeledValue("Medical", "medical"),
        LabeledValue("Military", "military"),
        LabeledValue("Monster Girls", "monster-girls"),
        LabeledValue("Monsters", "monsters"),
        LabeledValue("Music", "music"),
        LabeledValue("Mystery", "mystery"),
        LabeledValue("Ninja", "ninja"),
        LabeledValue("Office Workers", "office-workers"),
        LabeledValue("Official Colored", "official-colored"),
        LabeledValue("Oneshot", "oneshot"),
        LabeledValue("Philosophical", "philosophical"),
        LabeledValue("Police", "police"),
        LabeledValue("Post-Apocalyptic", "post-apocalyptic"),
        LabeledValue("Psychological", "psychological"),
        LabeledValue("Reincarnation", "reincarnation"),
        LabeledValue("Reverse Harem", "reverse-harem"),
        LabeledValue("Romance", "romance"),
        LabeledValue("Samurai", "samurai"),
        LabeledValue("School Life", "school-life"),
        LabeledValue("Sci-Fi", "sci-fi"),
        LabeledValue("Sexual Violence", "sexual-violence"),
        LabeledValue("Shota", "shota"),
        LabeledValue("Shoujo Ai", "shoujo-ai"),
        LabeledValue("Shounen Ai", "shounen-ai"),
        LabeledValue("Slice of Life", "slice-of-life"),
        LabeledValue("Smut", "smut"),
        LabeledValue("Sports", "sports"),
        LabeledValue("Superhero", "superhero"),
        LabeledValue("Supernatural", "supernatural"),
        LabeledValue("Survival", "survival"),
        LabeledValue("Thriller", "thriller"),
        LabeledValue("Time Travel", "time-travel"),
        LabeledValue("Traditional Games", "traditional-games"),
        LabeledValue("Tragedy", "tragedy"),
        LabeledValue("User Created", "user-created"),
        LabeledValue("Vampires", "vampires"),
        LabeledValue("Video Games", "video-games"),
        LabeledValue("Villainess", "villainess"),
        LabeledValue("Virtual Reality", "virtual-reality"),
        LabeledValue("Web Comic", "web-comic"),
        LabeledValue("Wuxia", "wuxia"),
        LabeledValue("Yaoi", "yaoi"),
        LabeledValue("Yuri", "yuri"),
        LabeledValue("Zombies", "zombies")
    )

    private fun getDemographics() = listOf(
        LabeledValue("Shonen", "1"),
        LabeledValue("Shoujo", "2"),
        LabeledValue("Seinen", "3"),
        LabeledValue("Josei", "4"),

    )

    private fun getContentType() = listOf(
        LabeledValue("Manga", "jp"),
        LabeledValue("Manhwa", "kr"),
        LabeledValue("Manhua", "cn"),
    )

    private fun getCreatedAt() = arrayOf(
        LabeledValue("", ""),
        LabeledValue("30 days", "30"),
        LabeledValue("3 months", "90"),
        LabeledValue("6 months", "180"),
        LabeledValue("1 year", "365"),
    )

    companion object {
        const val SLUG_SEARCH_PREFIX = "id:"
    }
}
