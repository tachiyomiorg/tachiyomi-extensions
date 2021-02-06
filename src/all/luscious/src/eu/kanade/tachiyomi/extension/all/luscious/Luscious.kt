package eu.kanade.tachiyomi.extension.all.luscious

import com.github.salomonbrys.kotson.addProperty
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.set
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Luscious(override val lang: String, private val lusLang: String) : HttpSource() {

    override val baseUrl: String = "https://www.luscious.net"
    override val name: String = "Luscious"
    override val supportsLatest: Boolean = true

    private val apiBaseUrl: String = "https://api.luscious.net/graphql/nobatch/"

    private val gson = Gson()

    override val client: OkHttpClient = network.cloudflareClient

    // Common

    private fun buildAlbumListRequestInput(page: Int, filters: FilterList, query: String = ""): JsonObject {
        val sortByFilter = filters.findInstance<SortBySelectFilter>()!!
        val albumTypeFilter = filters.findInstance<AlbumTypeSelectFilter>()!!
        val interestsFilter = filters.findInstance<InterestGroupFilter>()!!
        val languagesFilter = filters.findInstance<LanguageGroupFilter>()!!
        val tagsFilter = filters.findInstance<TagGroupFilter>()!!
        val genreFilter = filters.findInstance<GenreGroupFilter>()!!
        val contentTypeFilter = filters.findInstance<ContentTypeSelectFilter>()!!

        return JsonObject().apply {
            add(
                "input",
                JsonObject().apply {
                    addProperty("display", sortByFilter.selected)
                    addProperty("page", page)
                    add(
                        "filters",
                        JsonArray().apply {

                            if (contentTypeFilter.selected != FILTER_VALUE_IGNORE)
                                add(contentTypeFilter.toJsonObject("content_id"))

                            if (albumTypeFilter.selected != FILTER_VALUE_IGNORE)
                                add(albumTypeFilter.toJsonObject("album_type"))

                            with(interestsFilter) {
                                if (this.selected.isEmpty()) {
                                    throw Exception("Please select an Interest")
                                }
                                add(this.toJsonObject("audience_ids"))
                            }

                            add(
                                languagesFilter.toJsonObject("language_ids").apply {
                                    set("value", "+$lusLang${get("value").asString}")
                                }
                            )

                            if (tagsFilter.anyNotIgnored()) {
                                add(tagsFilter.toJsonObject("tagged"))
                            }

                            if (genreFilter.anyNotIgnored()) {
                                add(genreFilter.toJsonObject("genre_ids"))
                            }

                            if (query != "") {
                                add(
                                    JsonObject().apply {
                                        addProperty("name", "search_query")
                                        addProperty("value", query)
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    private fun buildAlbumListRequest(page: Int, filters: FilterList, query: String = ""): Request {
        val input = buildAlbumListRequestInput(page, filters, query)
        val url = HttpUrl.parse(apiBaseUrl)!!.newBuilder()
            .addQueryParameter("operationName", "AlbumList")
            .addQueryParameter("query", ALBUM_LIST_REQUEST_GQL)
            .addQueryParameter("variables", input.toString())
            .toString()
        return GET(url, headers)
    }

    private fun parseAlbumListResponse(response: Response): MangasPage {
        val data = gson.fromJson<JsonObject>(response.body()!!.string())
        with(data["data"]["album"]["list"]) {
            return MangasPage(
                this["items"].asJsonArray.map {
                    SManga.create().apply {
                        url = it["url"].asString
                        title = it["title"].asString
                        thumbnail_url = it["cover"]["url"].asString
                    }
                },
                this["info"]["has_next_page"].asBoolean
            )
        }
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = buildAlbumListRequest(page, getSortFilters(LATEST_DEFAULT_SORT_STATE))

    override fun latestUpdatesParse(response: Response): MangasPage = parseAlbumListResponse(response)

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                url = response.request().url().toString()
                name = "Chapter"
                date_upload = document.select(".album-info-item:contains(Created:)")?.first()?.ownText()?.trim()?.let {
                    DATE_FORMATS_WITH_ORDINAL_SUFFIXES.mapNotNull { format -> format.parseOrNull(it) }.firstOrNull()?.time
                } ?: 0L
                chapter_number = 1f
            }
        )
    }

    // Pages

    private fun buildAlbumPicturesRequestInput(id: String, page: Int, sortPagesByOption: String): JsonObject {
        return JsonObject().apply {
            addProperty(
                "input",
                JsonObject().apply {
                    addProperty(
                        "filters",
                        JsonArray().apply {
                            add(
                                JsonObject().apply {
                                    addProperty("name", "album_id")
                                    addProperty("value", id)
                                }
                            )
                        }
                    )
                    addProperty("display", sortPagesByOption)
                    addProperty("page", page)
                }
            )
        }
    }

    private fun buildAlbumPicturesPageUrl(id: String, page: Int, sortPagesByOption: String): String {
        val input = buildAlbumPicturesRequestInput(id, page, sortPagesByOption)
        return HttpUrl.parse(apiBaseUrl)!!.newBuilder()
            .addQueryParameter("operationName", "AlbumListOwnPictures")
            .addQueryParameter("query", ALBUM_PICTURES_REQUEST_GQL)
            .addQueryParameter("variables", input.toString())
            .toString()
    }

    private fun parseAlbumPicturesResponse(response: Response, sortPagesByOption: String): List<Page> {

        val id = response.request().url().queryParameter("variables").toString()
            .let { gson.fromJson<JsonObject>(it)["input"]["filters"].asJsonArray }
            .let { it.first { f -> f["name"].asString == "album_id" } }
            .let { it["value"].asString }

        val data = gson.fromJson<JsonObject>(response.body()!!.string())
            .let { it["data"]["picture"]["list"].asJsonObject }

        return data["items"].asJsonArray.mapIndexed { index, it ->
            Page(index, imageUrl = it["url_to_original"].asString)
        } + if (data["info"]["total_pages"].asInt > 1) { // get 2nd page onwards
            (ITEMS_PER_PAGE until data["info"]["total_items"].asInt).chunked(ITEMS_PER_PAGE).mapIndexed { page, indices ->
                indices.map { Page(it, url = buildAlbumPicturesPageUrl(id, page + 2, sortPagesByOption)) }
            }.flatten()
        } else emptyList()
    }

    private fun getAlbumSortPagesOption(chapter: SChapter): Observable<String> {
        return client.newCall(GET(chapter.url))
            .asObservableSuccess()
            .map {
                val sortByKey = it.asJsoup().select(".o-input-select:contains(Sorted By) .o-select-value")?.text() ?: ""
                ALBUM_PICTURES_SORT_OPTIONS.getValue(sortByKey)
            }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val id = chapter.url.substringAfterLast("_").removeSuffix("/")

        return getAlbumSortPagesOption(chapter)
            .concatMap { sortPagesByOption ->
                client.newCall(GET(buildAlbumPicturesPageUrl(id, 1, sortPagesByOption)))
                    .asObservableSuccess()
                    .map { parseAlbumPicturesResponse(it, sortPagesByOption) }
            }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun fetchImageUrl(page: Page): Observable<String> {
        if (page.imageUrl != null) {
            return Observable.just(page.imageUrl)
        }

        return client.newCall(GET(page.url, headers))
            .asObservableSuccess()
            .map {
                val data = gson.fromJson<JsonObject>(it.body()!!.string()).let { data ->
                    data["data"]["picture"]["list"].asJsonObject
                }
                data["items"].asJsonArray[page.index % 50].asJsonObject["url_to_original"].asString
            }
    }

    // Details

    private fun parseMangaGenre(document: Document): String {
        return listOf(
            document.select(".o-tag--secondary").map { it.text().substringBefore("(").trim() },
            document.select(".o-tag:not([href *= /tags/artist])").map { it.text() },
            document.select(".album-info-item:contains(Content:) .o-tag").map { it.text() }
        ).flatten().joinToString()
    }

    private fun parseMangaDescription(document: Document): String {
        val pageCount: String? = (
            document.select(".album-info-item:contains(pictures)").firstOrNull()
                ?: document.select(".album-info-item:contains(gifs)").firstOrNull()
            )?.text()

        return listOf(
            Pair("Description", document.select(".album-description:last-of-type")?.text()),
            Pair("Pages", pageCount)
        ).let {
            it + listOf("Parody", "Character", "Ethnicity")
                .map { key -> key to document.select(".o-tag--category:contains($key) .o-tag").joinToString { t -> t.text() } }
        }.filter { desc -> !desc.second.isNullOrBlank() }
            .joinToString("\n\n") { "${it.first}:\n${it.second}" }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {

            artist = document.select(".o-tag--category:contains(Artist:) .o-tag")?.joinToString() { it.text() }
            author = artist

            genre = parseMangaGenre(document)

            title = document.select("a[title]").text()
            status = when {
                title.contains("ongoing", true) -> SManga.ONGOING
                else -> SManga.COMPLETED
            }

            description = parseMangaDescription(document)
        }
    }

    // Popular

    override fun popularMangaParse(response: Response): MangasPage = parseAlbumListResponse(response)

    override fun popularMangaRequest(page: Int): Request = buildAlbumListRequest(page, getSortFilters(POPULAR_DEFAULT_SORT_STATE))

    // Search

    override fun searchMangaParse(response: Response): MangasPage = parseAlbumListResponse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = buildAlbumListRequest(
        page,
        filters.let {
            if (it.isEmpty()) getSortFilters(SEARCH_DEFAULT_SORT_STATE)
            else it
        },
        query
    )

    class TriStateFilterOption(name: String, val value: String) : Filter.TriState(name)
    abstract class TriStateGroupFilter(name: String, options: List<TriStateFilterOption>) : Filter.Group<TriStateFilterOption>(name, options) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.value }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.value }

        fun anyNotIgnored(): Boolean = state.any { !it.isIgnored() }

        override fun toString(): String = (included.map { "+$it" } + excluded.map { "-$it" }).joinToString("")
    }

    private fun Filter<*>.toJsonObject(key: String): JsonObject {
        val value = this.toString()
        return JsonObject().apply {
            addProperty("name", key)
            addProperty("value", value)
        }
    }

    private class TagGroupFilter(filters: List<TriStateFilterOption>) : TriStateGroupFilter("Tags", filters)
    private class GenreGroupFilter(filters: List<TriStateFilterOption>) : TriStateGroupFilter("Genres", filters)

    class CheckboxFilterOption(name: String, val value: String, default: Boolean = true) : Filter.CheckBox(name, default)
    abstract class CheckboxGroupFilter(name: String, options: List<CheckboxFilterOption>) : Filter.Group<CheckboxFilterOption>(name, options) {
        val selected: List<String>
            get() = state.filter { it.state }.map { it.value }

        override fun toString(): String = selected.joinToString("") { "+$it" }
    }

    private class InterestGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Interests", options)
    private class LanguageGroupFilter(options: List<CheckboxFilterOption>) : CheckboxGroupFilter("Languages", options)

    class SelectFilterOption(val name: String, val value: String)

    abstract class SelectFilter(name: String, private val options: List<SelectFilterOption>, default: Int = 0) : Filter.Select<String>(name, options.map { it.name }.toTypedArray(), default) {
        val selected: String
            get() = options[state].value

        override fun toString(): String = selected
    }
    class SortBySelectFilter(options: List<SelectFilterOption>, default: Int) : SelectFilter("Sort By", options, default)
    class AlbumTypeSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Album Type", options)
    class ContentTypeSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Content Type", options)

    override fun getFilterList(): FilterList = getSortFilters(POPULAR_DEFAULT_SORT_STATE)

    private fun getSortFilters(sortState: Int) = FilterList(
        SortBySelectFilter(getSortFilters(), sortState),
        AlbumTypeSelectFilter(getAlbumTypeFilters()),
        ContentTypeSelectFilter(getContentTypeFilters()),
        InterestGroupFilter(getInterestFilters()),
        LanguageGroupFilter(getLanguageFilters()),
        TagGroupFilter(getTagFilters()),
        GenreGroupFilter(getGenreFilters())
    )

    fun getSortFilters() = listOf(
        SelectFilterOption("Rating - All Time", "rating_all_time"),
        SelectFilterOption("Rating - Last 7 Days", "rating_7_days"),
        SelectFilterOption("Rating - Last 14 Days", "rating_14_days"),
        SelectFilterOption("Rating - Last 30 Days", "rating_30_days"),
        SelectFilterOption("Rating - Last 90 Days", "rating_90_days"),
        SelectFilterOption("Rating - Last Year", "rating_1_year"),
        SelectFilterOption("Rating - Last Year", "rating_1_year"),
        SelectFilterOption("Date - Newest First", "date_newest"),
        SelectFilterOption("Date - 2020", "date_2020"),
        SelectFilterOption("Date - 2019", "date_2019"),
        SelectFilterOption("Date - 2018", "date_2018"),
        SelectFilterOption("Date - 2017", "date_2017"),
        SelectFilterOption("Date - 2016", "date_2016"),
        SelectFilterOption("Date - 2015", "date_2015"),
        SelectFilterOption("Date - 2014", "date_2014"),
        SelectFilterOption("Date - 2013", "date_2013"),
        SelectFilterOption("Date - Oldest First", "date_oldest"),
        SelectFilterOption("Date - Upcoming", "date_upcoming"),
        SelectFilterOption("Date - Trending", "date_trending"),
        SelectFilterOption("Date - Featured", "date_featured"),
        SelectFilterOption("Date - Last Viewed", "date_last_interaction"),
    )

    fun getAlbumTypeFilters() = listOf(
        SelectFilterOption("Manga", "manga"),
        SelectFilterOption("All", FILTER_VALUE_IGNORE),
        SelectFilterOption("Pictures", "pictures")
    )

    fun getContentTypeFilters() = listOf(
        SelectFilterOption("All", FILTER_VALUE_IGNORE),
        SelectFilterOption("Hentai", "0"),
        SelectFilterOption("Non-Erotic", "5"),
        SelectFilterOption("Real People", "6")
    )

    fun getInterestFilters() = listOf(
        CheckboxFilterOption("Straight Sex", "1"),
        CheckboxFilterOption("Trans x Girl", "10", false),
        CheckboxFilterOption("Gay / Yaoi", "2"),
        CheckboxFilterOption("Lesbian / Yuri", "3"),
        CheckboxFilterOption("Trans", "5"),
        CheckboxFilterOption("Solo Girl", "6"),
        CheckboxFilterOption("Trans x Trans", "8"),
        CheckboxFilterOption("Trans x Guy", "9")
    )

    fun getLanguageFilters() = listOf(
        CheckboxFilterOption("English", ENGLISH_LUS_LANG_VAL, false),
        CheckboxFilterOption("Japanese", JAPANESE_LUS_LANG_VAL, false),
        CheckboxFilterOption("Spanish", SPANISH_LUS_LANG_VAL, false),
        CheckboxFilterOption("Italian", ITALIAN_LUS_LANG_VAL, false),
        CheckboxFilterOption("German", GERMAN_LUS_LANG_VAL, false),
        CheckboxFilterOption("French", FRENCH_LUS_LANG_VAL, false),
        CheckboxFilterOption("Chinese", CHINESE_LUS_LANG_VAL, false),
        CheckboxFilterOption("Korean", KOREAN_LUS_LANG_VAL, false),
        CheckboxFilterOption("Others", OTHERS_LUS_LANG_VAL, false),
        CheckboxFilterOption("Portugese", PORTUGESE_LUS_LANG_VAL, false),
        CheckboxFilterOption("Thai", THAI_LUS_LANG_VAL, false)
    ).filterNot { it.value == lusLang }

    fun getTagFilters() = listOf(
        TriStateFilterOption("Big Breasts", "big_breasts"),
        TriStateFilterOption("Blowjob", "blowjob"),
        TriStateFilterOption("Anal", "anal"),
        TriStateFilterOption("Group", "group"),
        TriStateFilterOption("Big Ass", "big_ass"),
        TriStateFilterOption("Full Color", "full_color"),
        TriStateFilterOption("Schoolgirl", "schoolgirl"),
        TriStateFilterOption("Rape", "rape"),
        TriStateFilterOption("Glasses", "glasses"),
        TriStateFilterOption("Nakadashi", "nakadashi"),
        TriStateFilterOption("Yuri", "yuri"),
        TriStateFilterOption("Paizuri", "paizuri"),
        TriStateFilterOption("Ahegao", "ahegao"),
        TriStateFilterOption("Group: metart", "group%3A_metart"),
        TriStateFilterOption("Brunette", "brunette"),
        TriStateFilterOption("Solo", "solo"),
        TriStateFilterOption("Blonde", "blonde"),
        TriStateFilterOption("Shaved Pussy", "shaved_pussy"),
        TriStateFilterOption("Small Breasts", "small_breasts"),
        TriStateFilterOption("Cum", "cum"),
        TriStateFilterOption("Stockings", "stockings"),
        TriStateFilterOption("Yuri", "yuri"),
        TriStateFilterOption("Ass", "ass"),
        TriStateFilterOption("Creampie", "creampie"),
        TriStateFilterOption("Rape", "rape"),
        TriStateFilterOption("Oral Sex", "oral_sex"),
        TriStateFilterOption("Bondage", "bondage"),
        TriStateFilterOption("Futanari", "futanari"),
        TriStateFilterOption("Double Penetration", "double_penetration"),
        TriStateFilterOption("Threesome", "threesome"),
        TriStateFilterOption("Anal Sex", "anal_sex"),
        TriStateFilterOption("Big Cock", "big_cock"),
        TriStateFilterOption("Straight Sex", "straight_sex"),
        TriStateFilterOption("Yaoi", "yaoi")
    )

    fun getGenreFilters() = listOf(
        TriStateFilterOption("3D / Digital Art", "25"),
        TriStateFilterOption("Amateurs", "20"),
        TriStateFilterOption("Artist Collection", "19"),
        TriStateFilterOption("Asian Girls", "12"),
        TriStateFilterOption("Cosplay", "22"),
        TriStateFilterOption("BDSM", "27"),
        TriStateFilterOption("Cross-Dressing", "30"),
        TriStateFilterOption("Defloration / First Time", "59"),
        TriStateFilterOption("Ebony Girls", "32"),
        TriStateFilterOption("European Girls", "46"),
        TriStateFilterOption("Fantasy / Monster Girls", "10"),
        TriStateFilterOption("Fetish", "2"),
        TriStateFilterOption("Furries", "8"),
        TriStateFilterOption("Futanari", "31"),
        TriStateFilterOption("Group Sex", "36"),
        TriStateFilterOption("Harem", "56"),
        TriStateFilterOption("Humor", "41"),
        TriStateFilterOption("Interracial", "28"),
        TriStateFilterOption("Kemonomimi / Animal Ears", "39"),
        TriStateFilterOption("Latina Girls", "33"),
        TriStateFilterOption("Mature", "13"),
        TriStateFilterOption("Members: Original Art", "18"),
        TriStateFilterOption("Members: Verified Selfies", "21"),
        TriStateFilterOption("Military", "48"),
        TriStateFilterOption("Mind Control", "34"),
        TriStateFilterOption("Monsters & Tentacles", "38"),
        TriStateFilterOption("Netorare / Cheating", "40"),
        TriStateFilterOption("No Genre Given", "1"),
        TriStateFilterOption("Nonconsent / Reluctance", "37"),
        TriStateFilterOption("Other Ethnicity Girls", "57"),
        TriStateFilterOption("Public Sex", "43"),
        TriStateFilterOption("Romance", "42"),
        TriStateFilterOption("School / College", "35"),
        TriStateFilterOption("Sex Workers", "47"),
        TriStateFilterOption("Softcore / Ecchi", "9"),
        TriStateFilterOption("Superheroes", "17"),
        TriStateFilterOption("Tankobon", "45"),
        TriStateFilterOption("TV / Movies", "51"),
        TriStateFilterOption("Trans", "14"),
        TriStateFilterOption("Video Games", "15"),
        TriStateFilterOption("Vintage", "58"),
        TriStateFilterOption("Western", "11"),
        TriStateFilterOption("Workplace Sex", "50")
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun SimpleDateFormat.parseOrNull(string: String): Date? {
        return try {
            parse(string)
        } catch (e: ParseException) {
            null
        }
    }

    companion object {

        private val ALBUM_PICTURES_SORT_OPTIONS = hashMapOf(
            Pair("Sort By Newest", "date_newest"),
            Pair("Sort By Rating", "rating_all_time")
        ).withDefault { "position" }

        private const val ITEMS_PER_PAGE = 50

        private val ORDINAL_SUFFIXES = listOf("st", "nd", "rd", "th")
        private val DATE_FORMATS_WITH_ORDINAL_SUFFIXES = ORDINAL_SUFFIXES.map {
            SimpleDateFormat("MMMM dd'$it', yyyy", Locale.US)
        }

        const val ENGLISH_LUS_LANG_VAL = "1"
        const val JAPANESE_LUS_LANG_VAL = "2"
        const val SPANISH_LUS_LANG_VAL = "3"
        const val ITALIAN_LUS_LANG_VAL = "4"
        const val GERMAN_LUS_LANG_VAL = "5"
        const val FRENCH_LUS_LANG_VAL = "6"
        const val CHINESE_LUS_LANG_VAL = "8"
        const val KOREAN_LUS_LANG_VAL = "9"
        const val OTHERS_LUS_LANG_VAL = "99"
        const val PORTUGESE_LUS_LANG_VAL = "100"
        const val THAI_LUS_LANG_VAL = "101"

        private const val POPULAR_DEFAULT_SORT_STATE = 0
        private const val LATEST_DEFAULT_SORT_STATE = 7
        private const val SEARCH_DEFAULT_SORT_STATE = 0

        private const val FILTER_VALUE_IGNORE = "<ignore>"

        private val ALBUM_LIST_REQUEST_GQL = """
            query AlbumList(${'$'}input: AlbumListInput!) {
                album {
                    list(input: ${'$'}input) {
                        info {
                            page
                            has_next_page
                        }
                        items
                    }
                }
            }
        """.replace("\n", " ").replace("\\s+".toRegex(), " ")

        private val ALBUM_PICTURES_REQUEST_GQL = """
            query AlbumListOwnPictures(${'$'}input: PictureListInput!) {
                picture {
                    list(input: ${'$'}input) {
                        info {
                            total_items
                            total_pages
                            page
                            has_next_page
                        }
                    items {
                        url_to_original
                    }
                }
              }
            }
        """.replace("\n", " ").replace("\\s+".toRegex(), " ")
    }
}
