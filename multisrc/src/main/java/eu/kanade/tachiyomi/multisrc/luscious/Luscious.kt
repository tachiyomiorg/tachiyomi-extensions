package eu.kanade.tachiyomi.multisrc.luscious

import android.app.Application
import android.content.SharedPreferences
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import com.github.salomonbrys.kotson.addProperty
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.set
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar

abstract class Luscious(
    override val name: String,
    override val baseUrl: String,
    override val lang: String ) : ConfigurableSource, HttpSource() {

    //Based on Luscios single source extension form https://github.com/tachiyomiorg/tachiyomi-extensions/commit/aacf56d0c0ddb173372aac69d798ae998f178377
    //with modifiaction to make it support multisrc

    override val supportsLatest: Boolean = true
    private val apiBaseUrl: String = "$baseUrl/graphql/nobatch/"
    private val gson = Gson()
    override val client: OkHttpClient = network.cloudflareClient
    private val lusLang: String = toLusLang(lang)
    private fun toLusLang (lang: String): String {
        return when (lang) {
            "all" -> FILTER_VALUE_IGNORE
            "en" -> "1"
            "ja" -> "2"
            "es" -> "3"
            "it" -> "4"
            "de" -> "5"
            "fr" -> "6"
            "zh" -> "8"
            "ko" -> "9"
            "pt" -> "100"
            "th" -> "101"
            else -> "99"
        }
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }


    // Common

    private fun buildAlbumListRequestInput(page: Int, filters: FilterList, query: String = ""): JsonObject {
        val sortByFilter = filters.findInstance<SortBySelectFilter>()!!
        val albumTypeFilter = filters.findInstance<AlbumTypeSelectFilter>()!!
        val interestsFilter = filters.findInstance<InterestGroupFilter>()!!
        val languagesFilter = filters.findInstance<LanguageGroupFilter>()!!
        val tagsFilter = filters.findInstance<TagTextFilters>()!!
        val creatorFilter = filters.findInstance<CreatorTextFilters>()!!
        val favoriteFilter = filters.findInstance<FavoriteTextFilters>()!!
        val genreFilter = filters.findInstance<GenreGroupFilter>()!!
        val contentTypeFilter = filters.findInstance<ContentTypeSelectFilter>()!!
        val albumSizeFilter = filters.findInstance<AlbumSizeSelectFilter>()!!
        val restrictGenresFilter = filters.findInstance<RestrictGenresSelectFilter>()!!

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

                            if (albumSizeFilter.selected != FILTER_VALUE_IGNORE)
                                add(albumSizeFilter.toJsonObject("picture_count_rank"))

                            if (restrictGenresFilter.selected != FILTER_VALUE_IGNORE)
                                add(restrictGenresFilter.toJsonObject("restrict_genres"))

                            with(interestsFilter) {
                                if (this.selected.isEmpty()) {
                                    throw Exception("Please select an Interest")
                                }
                                add(this.toJsonObject("audience_ids"))
                            }

                            if (lusLang != FILTER_VALUE_IGNORE){
                                add(
                                    languagesFilter.toJsonObject("language_ids").apply {
                                        set("value", "+$lusLang${get("value").asString}")
                                    }
                                )
                            }

                            if (tagsFilter.state.isNotEmpty()) {
                                val tags = "+${tagsFilter.state.toLowerCase()}".replace(" ", "_").replace("_,", "+").replace(",_", "+").replace(",", "+").replace("+-", "-").replace("-_", "-").trim()
                                add(
                                    JsonObject().apply {
                                        addProperty("name", "tagged")
                                        addProperty("value", tags)
                                    }
                                )
                            }

                            if (creatorFilter.state.isNotEmpty()) {
                                add(
                                    JsonObject().apply {
                                        addProperty("name", "created_by_id")
                                        addProperty("value", creatorFilter.state)
                                    }
                                )
                            }

                            if (favoriteFilter.state.isNotEmpty()) {
                                add(
                                    JsonObject().apply {
                                        addProperty("name", "favorite_by_user_id")
                                        addProperty("value", favoriteFilter.state)
                                    }
                                )
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
        val url = apiBaseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("operationName", "AlbumList")
            .addQueryParameter("query", ALBUM_LIST_REQUEST_GQL)
            .addQueryParameter("variables", input.toString())
            .toString()
        return GET(url, headers)
    }

    private fun parseAlbumListResponse(response: Response): MangasPage {
        val data = gson.fromJson<JsonObject>(response.body!!.string())
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

    private fun buildAlbumInfoRequestInput(id: String): JsonObject {
        return JsonObject().apply {
            addProperty("id", id)
        }
    }

    private fun buildAlbumInfoRequest(id: String): Request {
        val input = buildAlbumInfoRequestInput(id)
        val url = apiBaseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("operationName", "AlbumGet")
            .addQueryParameter("query", albumInfoQuery)
            .addQueryParameter("variables", input.toString())
            .toString()
        return GET(url, headers)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = buildAlbumListRequest(page, getSortFilters(LATEST_DEFAULT_SORT_STATE))

    override fun latestUpdatesParse(response: Response): MangasPage = parseAlbumListResponse(response)

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")

        return client.newCall(GET(buildAlbumPicturesPageUrl(id, 1)))
            .asObservableSuccess()
            .map { parseAlbumPicturesResponse(it, manga.url) }
    }

    private fun parseAlbumPicturesResponse(response: Response, mangaUrl: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        when (getMergeChapterPref()){
            true -> {
                val chapter = SChapter.create()
                chapter.url = mangaUrl
                chapter.name = "Merged Chapter"
                //chapter.date_upload = it["created"].asLong // not parsing correctly for some reason
                chapter.chapter_number = 1F
                chapters.add(chapter)
            }
            false -> {
                var nextPage = true
                var page = 2
                val id = response.request.url.queryParameter("variables").toString()
                    .let { gson.fromJson<JsonObject>(it)["input"]["filters"].asJsonArray }
                    .let { it.first { f -> f["name"].asString == "album_id" } }
                    .let { it["value"].asString }

                var data = gson.fromJson<JsonObject>(response.body!!.string())
                    .let { it["data"]["picture"]["list"].asJsonObject }

                while (nextPage) {
                    nextPage = data["info"]["has_next_page"].asBoolean
                    data["items"].asJsonArray.map {
                        val chapter = SChapter.create()
                        chapter.url = when (getResolutionPref()){
                            "-1" -> it["url_to_original"].asString
                            else -> it["thumbnails"][getResolutionPref()?.toInt()!!]["url"].asString
                        }
                        chapter.name = it["title"].asString
                        //chapter.date_upload = it["created"].asLong // not parsing correctly for some reason
                        chapter.chapter_number = it["position"].asInt.toFloat()
                        chapters.add(chapter)
                    }
                    if (nextPage) {
                        val newPage = client.newCall(GET(buildAlbumPicturesPageUrl(id, page))).execute()
                        data = gson.fromJson<JsonObject>(newPage.body!!.string())
                            .let { it["data"]["picture"]["list"].asJsonObject }
                    }
                    page++
                }
            }

        }

        return chapters.reversed()
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")

    // Pages

    private fun buildAlbumPicturesRequestInput(id: String, page: Int): JsonObject {
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
                    addProperty("display", getSortPref())
                    addProperty("page", page)
                }
            )
        }
    }

    private fun buildAlbumPicturesPageUrl(id: String, page: Int): String {
        val input = buildAlbumPicturesRequestInput(id, page)
        return apiBaseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("operationName", "AlbumListOwnPictures")
            .addQueryParameter("query", ALBUM_PICTURES_REQUEST_GQL)
            .addQueryParameter("variables", input.toString())
            .toString()
    }

    private fun parseAlbumPicturesResponseMergeChapter(response: Response): List<Page> {
        val pages = mutableListOf<Page>()
        var nextPage = true
        var page = 2
        val id = response.request.url.queryParameter("variables").toString()
            .let { gson.fromJson<JsonObject>(it)["input"]["filters"].asJsonArray }
            .let { it.first { f -> f["name"].asString == "album_id" } }
            .let { it["value"].asString }

        var data = gson.fromJson<JsonObject>(response.body!!.string())
            .let { it["data"]["picture"]["list"].asJsonObject }

        while (nextPage) {
            nextPage = data["info"]["has_next_page"].asBoolean
            data["items"].asJsonArray.map {
                val index = it["position"].asInt
                val url = when (getResolutionPref()){
                    "-1" -> it["url_to_original"].asString
                    else -> it["thumbnails"][getResolutionPref()?.toInt()!!]["url"].asString
                }


                pages.add(Page(index, url, url))
            }
            if (nextPage) {
                val newPage = client.newCall(GET(buildAlbumPicturesPageUrl(id, page))).execute()
                data = gson.fromJson<JsonObject>(newPage.body!!.string())
                    .let { it["data"]["picture"]["list"].asJsonObject }
            }
            page++
        }
        return pages
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return when (getMergeChapterPref()) {
            true -> {
                val id = chapter.url.substringAfterLast("_").removeSuffix("/")

                client.newCall(GET(buildAlbumPicturesPageUrl(id, 1)))
                    .asObservableSuccess()
                    .map { parseAlbumPicturesResponseMergeChapter(it) }
            }
            false -> {
                Observable.just(listOf(Page(0, chapter.url, chapter.url)))
            }
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
                val data = gson.fromJson<JsonObject>(it.body!!.string()).let { data ->
                    data["data"]["picture"]["list"].asJsonObject
                }
                when (getResolutionPref()){
                    "-1" -> data["items"].asJsonArray[page.index % 50].asJsonObject["url_to_original"].asString
                    else -> data["items"].asJsonArray[page.index % 50].asJsonObject["thumbnails"][getResolutionPref()?.toInt()!!]["url"].asString
                }

            }
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}", headers)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val id = manga.url.substringAfterLast("_").removeSuffix("/")
        return client.newCall(buildAlbumInfoRequest(id))
            .asObservableSuccess()
            .map { detailsParse(it) }
    }

    private fun detailsParse(response: Response): SManga {
        val data = gson.fromJson<JsonObject>(response.body!!.string())
        with(data["data"]["album"]["get"]) {
            val manga = SManga.create()
            manga.url = this["url"].asString
            manga.title = this["title"].asString
            manga.thumbnail_url = this["cover"]["url"].asString
            manga.status = 0
            manga.description = "${this["description"].asString}\n\nPictures: ${this["number_of_pictures"].asString}\nAnimated Pictures: ${this["number_of_animated_pictures"].asString}"
            var genreList = this["language"]["title"].asString
            for ((i, _) in this["labels"].asJsonArray.withIndex()) {
                genreList = "$genreList, ${this["labels"][i].asString}"
            }
            for ((i, _) in this["genres"].asJsonArray.withIndex()) {
                genreList = "$genreList, ${this["genres"][i]["title"].asString}"
            }
            for ((i, _) in this["audiences"].asJsonArray.withIndex()) {
                genreList = "$genreList, ${this["audiences"][i]["title"].asString}"
            }
            for ((i, _) in this["tags"].asJsonArray.withIndex()) {
                genreList = "$genreList, ${this["tags"][i]["text"].asString}"
                if (this["tags"][i]["text"].asString.contains("Artist:")){
                    manga.artist = this["tags"][i]["text"].asString.substringAfter(":").trim()
                    manga.author = manga.artist
                }
            }
            genreList = "$genreList, ${this["content"]["title"].asString}"
            manga.genre = genreList

            return manga
        }
    }
    override fun mangaDetailsParse(response: Response): SManga = throw UnsupportedOperationException("Not used")

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

    open class TextFilter(name: String) : Filter.Text(name)

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
    class RestrictGenresSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Restrict Genres", options)
    class AlbumSizeSelectFilter(options: List<SelectFilterOption>) : SelectFilter("Album Size", options)
    class TagTextFilters : TextFilter("Tags")
    class CreatorTextFilters : TextFilter("Uploader")
    class FavoriteTextFilters : TextFilter("Favorite by User")
    override fun getFilterList(): FilterList = getSortFilters(POPULAR_DEFAULT_SORT_STATE)

    private fun getSortFilters(sortState: Int) = FilterList(
        SortBySelectFilter(getSortFilters(), sortState),
        AlbumTypeSelectFilter(getAlbumTypeFilters()),
        ContentTypeSelectFilter(getContentTypeFilters()),
        AlbumSizeSelectFilter(getAlbumSizeFilters()),
        RestrictGenresSelectFilter(getRestrictGenresFilters()),
        InterestGroupFilter(getInterestFilters()),
        LanguageGroupFilter(getLanguageFilters()),
        GenreGroupFilter(getGenreFilters()),
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TagTextFilters(),
        Filter.Header("The following require username or ID"),
        CreatorTextFilters(),
        FavoriteTextFilters(),
    )


    private fun getSortFilters(): List<SelectFilterOption> {
        val sortOptions = mutableListOf<SelectFilterOption>()
        listOf(
            SelectFilterOption("Rating - All Time", "rating_all_time"),
            SelectFilterOption("Rating - Last 7 Days", "rating_7_days"),
            SelectFilterOption("Rating - Last 14 Days", "rating_14_days"),
            SelectFilterOption("Rating - Last 30 Days", "rating_30_days"),
            SelectFilterOption("Rating - Last 90 Days", "rating_90_days"),
            SelectFilterOption("Rating - Last Year", "rating_1_year"),
            SelectFilterOption("Date - Newest First", "date_newest"),
            SelectFilterOption("Date - Oldest First", "date_oldest"),
            SelectFilterOption("Date - Upcoming", "date_upcoming"),
            SelectFilterOption("Date - Trending", "date_trending"),
            SelectFilterOption("Date - Featured", "date_featured"),
            SelectFilterOption("Date - Last Viewed", "date_last_interaction"),
        ).forEach {
            sortOptions.add(it)
        }
        validYears().map {
            sortOptions.add(SelectFilterOption("Date - $it", "date_$it"))
        }
        listOf(
            SelectFilterOption("First Letter - Any", "alpha_any"),
            SelectFilterOption("First Letter - A", "alpha_a"),
            SelectFilterOption("First Letter - B", "alpha_b"),
            SelectFilterOption("First Letter - C", "alpha_c"),
            SelectFilterOption("First Letter - D", "alpha_d"),
            SelectFilterOption("First Letter - E", "alpha_e"),
            SelectFilterOption("First Letter - F", "alpha_f"),
            SelectFilterOption("First Letter - G", "alpha_g"),
            SelectFilterOption("First Letter - H", "alpha_h"),
            SelectFilterOption("First Letter - I", "alpha_i"),
            SelectFilterOption("First Letter - J", "alpha_j"),
            SelectFilterOption("First Letter - K", "alpha_k"),
            SelectFilterOption("First Letter - L", "alpha_l"),
            SelectFilterOption("First Letter - M", "alpha_m"),
            SelectFilterOption("First Letter - N", "alpha_n"),
            SelectFilterOption("First Letter - O", "alpha_o"),
            SelectFilterOption("First Letter - P", "alpha_p"),
            SelectFilterOption("First Letter - Q", "alpha_q"),
            SelectFilterOption("First Letter - R", "alpha_r"),
            SelectFilterOption("First Letter - S", "alpha_s"),
            SelectFilterOption("First Letter - T", "alpha_t"),
            SelectFilterOption("First Letter - U", "alpha_u"),
            SelectFilterOption("First Letter - V", "alpha_v"),
            SelectFilterOption("First Letter - W", "alpha_w"),
            SelectFilterOption("First Letter - X", "alpha_x"),
            SelectFilterOption("First Letter - Y", "alpha_y"),
            SelectFilterOption("First Letter - Z", "alpha_z"),
        ).forEach {
            sortOptions.add(it)
        }
        return sortOptions
    }

    private fun getAlbumTypeFilters() = listOf(
        SelectFilterOption("All", FILTER_VALUE_IGNORE),
        SelectFilterOption("Manga", "manga"),
        SelectFilterOption("Pictures", "pictures")
    )

    private fun getRestrictGenresFilters() = listOf(
        SelectFilterOption("None", FILTER_VALUE_IGNORE),
        SelectFilterOption("Loose", "loose"),
        SelectFilterOption("Strict", "strict")
    )

    private fun getContentTypeFilters() = listOf(
        SelectFilterOption("All", FILTER_VALUE_IGNORE),
        SelectFilterOption("Hentai", "0"),
        SelectFilterOption("Non-Erotic", "5"),
        SelectFilterOption("Real People", "6")
    )

    private fun getAlbumSizeFilters() = listOf(
        SelectFilterOption("All", FILTER_VALUE_IGNORE),
        SelectFilterOption("0-25", "0"),
        SelectFilterOption("0-50", "1"),
        SelectFilterOption("50-100", "2"),
        SelectFilterOption("100-200", "3"),
        SelectFilterOption("200-800", "4"),
        SelectFilterOption("800-3200", "5"),
        SelectFilterOption("3200-12800", "6"),
    )

    private fun getInterestFilters() = listOf(
        CheckboxFilterOption("Straight Sex", "1"),
        CheckboxFilterOption("Trans x Girl", "10"),
        CheckboxFilterOption("Gay / Yaoi", "2"),
        CheckboxFilterOption("Lesbian / Yuri", "3"),
        CheckboxFilterOption("Trans", "5"),
        CheckboxFilterOption("Solo Girl", "6"),
        CheckboxFilterOption("Trans x Trans", "8"),
        CheckboxFilterOption("Trans x Guy", "9")
    )

    private fun getLanguageFilters() = listOf(
        CheckboxFilterOption("English", toLusLang("en"), false),
        CheckboxFilterOption("Japanese", toLusLang("ja"), false),
        CheckboxFilterOption("Spanish", toLusLang("es"), false),
        CheckboxFilterOption("Italian", toLusLang("it"), false),
        CheckboxFilterOption("German", toLusLang("de"), false),
        CheckboxFilterOption("French", toLusLang("fr"), false),
        CheckboxFilterOption("Chinese", toLusLang("zh"), false),
        CheckboxFilterOption("Korean", toLusLang("ko"), false),
        CheckboxFilterOption("Others", toLusLang("other"), false),
        CheckboxFilterOption("Portugese", toLusLang("pt"), false),
        CheckboxFilterOption("Thai", toLusLang("th"), false)
    ).filterNot { it.value == lusLang }


    private fun getGenreFilters() = listOf(
        TriStateFilterOption("3D / Digital Art", "25"),
        TriStateFilterOption("Amateurs", "20"),
        TriStateFilterOption("Artist Collection", "19"),
        TriStateFilterOption("Asian Girls", "12"),
        TriStateFilterOption("BDSM", "27"),
        TriStateFilterOption("Bestiality Hentai", "5"),
        TriStateFilterOption("Casting", "44"),
        TriStateFilterOption("Celebrity Fakes", "16"),
        TriStateFilterOption("Cosplay", "22"),
        TriStateFilterOption("Cross-Dressing", "30"),
        TriStateFilterOption("Cumshot", "26"),
        TriStateFilterOption("Defloration / First Time", "59"),
        TriStateFilterOption("Ebony Girls", "32"),
        TriStateFilterOption("European Girls", "46"),
        TriStateFilterOption("Extreme Gore", "60"),
        TriStateFilterOption("Extreme Scat", "61"),
        TriStateFilterOption("Fantasy / Monster Girls", "10"),
        TriStateFilterOption("Fetish", "2"),
        TriStateFilterOption("Furries", "8"),
        TriStateFilterOption("Futanari", "31"),
        TriStateFilterOption("Group Sex", "36"),
        TriStateFilterOption("Harem", "56"),
        TriStateFilterOption("Humor", "41"),
        TriStateFilterOption("Incest", "24"),
        TriStateFilterOption("Interracial", "28"),
        TriStateFilterOption("Kemonomimi / Animal Ears", "39"),
        TriStateFilterOption("Latina Girls", "33"),
        TriStateFilterOption("Lolicon", "3"),
        TriStateFilterOption("Mature", "13"),
        TriStateFilterOption("Members: Original Art", "18"),
        TriStateFilterOption("Members: Verified Selfies", "21"),
        TriStateFilterOption("Military", "48"),
        TriStateFilterOption("Mind Control", "34"),
        TriStateFilterOption("Monsters & Tentacles", "38"),
        TriStateFilterOption("Music", "45"),
        TriStateFilterOption("Netorare / Cheating", "40"),
        TriStateFilterOption("No Genre Given", "1"),
        TriStateFilterOption("Nonconsent / Reluctance", "37"),
        TriStateFilterOption("Other Ethnicity Girls", "57"),
        TriStateFilterOption("Private to Luscious", "55"),
        TriStateFilterOption("Public Sex", "43"),
        TriStateFilterOption("Romance", "42"),
        TriStateFilterOption("School / College", "35"),
        TriStateFilterOption("Sex Workers", "47"),
        TriStateFilterOption("SFW", "23"),
        TriStateFilterOption("Shotacon", "4"),
        TriStateFilterOption("Softcore / Ecchi", "9"),
        TriStateFilterOption("Superheroes", "17"),
        TriStateFilterOption("Swimsuit", "49"),
        TriStateFilterOption("Tankōbon", "45"),
        TriStateFilterOption("Trans", "14"),
        TriStateFilterOption("TV / Movies", "51"),
        TriStateFilterOption("Video Games", "15"),
        TriStateFilterOption("Vintage", "58"),
        TriStateFilterOption("Western", "11"),
        TriStateFilterOption("Workplace Sex", "50")
    )

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    private fun validYears(): List<Int>{
        val years = mutableListOf<Int>()
        val current = Calendar.getInstance()
        val currentYear = current.get(Calendar.YEAR)
        var firstYear = 2013
        while (currentYear != firstYear -1) {
            years.add(firstYear)
            firstYear++
        }
        return years.reversed()
    }

    companion object {

        private const val POPULAR_DEFAULT_SORT_STATE = 0
        private const val LATEST_DEFAULT_SORT_STATE = 6
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
                            items_per_page
                        }
                    items {
                        created
                        title
                        url_to_original
                        position
                        thumbnails {
                            url
                        }
                    }
                }
              }
            }
        """.replace("\n", " ").replace("\\s+".toRegex(), " ")

        val albumInfoQuery = """
        query AlbumGet(${"$"}id: ID!) {
            album {
                get(id: ${"$"}id) {
                    ... on Album { ...AlbumStandard }
                    ... on MutationError {
                        errors {
                            code message
                         }
                    }
                }
            }
        }
        fragment AlbumStandard on Album {
            __typename id title labels description created modified like_status number_of_favorites number_of_dislikes rating moderation_status marked_for_deletion marked_for_processing number_of_pictures number_of_animated_pictures number_of_duplicates slug is_manga url download_url permissions cover { width height size url } created_by { id url name display_name user_title avatar { url size } } content { id title url } language { id title url } tags { category text url count } genres { id title slug url } audiences { id title url url } last_viewed_picture { id position url } is_featured featured_date featured_by { id url name display_name user_title avatar { url size } }
        }
        """.trimIndent()

        private const val MERGE_CHAPTER_PREF_KEY = "MERGE_CHAPTER"
        private const val MERGE_CHAPTER_PREF_TITLE = "Merge Chapter"
        private const val MERGE_CHAPTER_PREF_SUMMARY = "If checked, merges all content of one Album into one Chapter"
        private const val MERGE_CHAPTER_PREF_DEFAULT_VALUE = false

        private const val RESOLUTION_PREF_KEY = "RESOLUTION"
        private const val RESOLUTION_PREF_TITLE = "Image resolution"
        private val RESOLUTION_PREF_ENTRIES = arrayOf("Low", "Medium", "High", "Original")
        private val RESOLUTION_PREF_ENTRY_VALUES = arrayOf("2", "1", "0", "-1")
        private val RESOLUTION_PREF_DEFAULT_VALUE = RESOLUTION_PREF_ENTRY_VALUES[2]

        private const val SORT_PREF_KEY = "SORT"
        private const val SORT_PREF_TITLE = "Page Sort"
        private val SORT_PREF_ENTRIES = arrayOf("Position", "Date", "Rating")
        private val SORT_PREF_ENTRY_VALUES = arrayOf("position", "date_newest", "rating_all_time")
        private val SORT_PREF_DEFAULT_VALUE = SORT_PREF_ENTRY_VALUES[0]
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val resolutionPref = ListPreference(screen.context).apply {
            key = "${RESOLUTION_PREF_KEY}_$lang"
            title = RESOLUTION_PREF_TITLE
            entries = RESOLUTION_PREF_ENTRIES
            entryValues = RESOLUTION_PREF_ENTRY_VALUES
            setDefaultValue(RESOLUTION_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${RESOLUTION_PREF_KEY}_$lang", entry).commit()
            }
        }
        val sortPref = ListPreference(screen.context).apply {
            key = "${SORT_PREF_KEY}_$lang"
            title = SORT_PREF_TITLE
            entries = SORT_PREF_ENTRIES
            entryValues = SORT_PREF_ENTRY_VALUES
            setDefaultValue(SORT_PREF_DEFAULT_VALUE)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString("${SORT_PREF_KEY}_$lang", entry).commit()
            }
        }
        val mergeChapterPref = CheckBoxPreference(screen.context).apply {
            key = "${MERGE_CHAPTER_PREF_KEY}_$lang"
            title = MERGE_CHAPTER_PREF_TITLE
            summary = MERGE_CHAPTER_PREF_SUMMARY
            setDefaultValue(MERGE_CHAPTER_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${MERGE_CHAPTER_PREF_KEY}_$lang", checkValue).commit()
            }
        }
        screen.addPreference(resolutionPref)
        screen.addPreference(sortPref)
        screen.addPreference(mergeChapterPref)
    }

    private fun getMergeChapterPref(): Boolean = preferences.getBoolean("${MERGE_CHAPTER_PREF_KEY}_$lang", MERGE_CHAPTER_PREF_DEFAULT_VALUE)
    private fun getResolutionPref(): String? = preferences.getString("${RESOLUTION_PREF_KEY}_$lang", RESOLUTION_PREF_DEFAULT_VALUE)
    private fun getSortPref(): String? = preferences.getString("${SORT_PREF_KEY}_$lang", SORT_PREF_DEFAULT_VALUE)
}
