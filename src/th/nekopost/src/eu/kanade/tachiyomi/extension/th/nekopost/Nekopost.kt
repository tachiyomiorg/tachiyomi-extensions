package eu.kanade.tachiyomi.extension.th.nekopost

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

class Nekopost() : ParsedHttpSource() {
    override val baseUrl: String = "https://www.nekopost.net/manga/"

    private val mangaListUrl: String = "https://www.nekopost.net/project/ajax_load_update/m/"
    private val chapterContentUrl: String = "https://www.nekopost.net/reader/loadChapterContent/"
    private val chapterImageUrl: String = "https://www.nekopost.net/file_server/collectManga/"
    private val searchUrl: String = "https://www.nekopost.net/search/"

    private val fallbackImageUrl: String = "https://www.nekopost.net/images/no_image.jpg"

    override val lang: String = "th"
    override val name: String = "Nekopost"

    override val supportsLatest: Boolean = true

    private var latestMangaList: HashSet<String> = HashSet()
    private var popularMangaList: HashSet<String> = HashSet()

    override fun chapterListSelector(): String = ".bg-card.card.pb-2 tr"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.select("a").first().let {
            setUrlWithoutDomain(NPUtils.getMangaOrChapterAlias(it.attr("href")))
            name = it.text()
        }
        date_upload = NPUtils.convertDateStringToEpoch(element.select("b").last().nextSibling().toString().trim())
        scanlator = element.select("a").last().text()
    }

    override fun imageUrlParse(document: Document): String = ".bg-card.card .p-3.text-white img"

    private var latestUpdatePageOffset: Int = 0

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page + latestUpdatePageOffset))
            .asObservableSuccess()
            .concatMap { response ->
                latestUpdatesParse(response).let {
                    if ((it.mangas as NPArrayList<SManga>).isListEmpty() && it.mangas.isNotEmpty()) {
                        latestUpdatePageOffset++
                        fetchLatestUpdates(page)
                    } else Observable.just(it)
                }
            }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(latestUpdatesSelector()).filter { element ->
            val dateText = element.select(".date").text().trim()
            val currentDate = Calendar.getInstance(Locale("th"))

            dateText.contains(currentDate.get(Calendar.DATE).toString()) && dateText.contains(NPUtils.monthList[currentDate.get(Calendar.MONTH)])
        }

        val mangas = NPArrayList(
            mangaList.map { element -> latestUpdatesFromElement(element) }.filter { manga ->
                if (!latestMangaList.contains(manga.url)) {
                    latestMangaList.add(manga.url)
                    true
                } else false
            },
            mangaList)

        val hasNextPage = mangaList.isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(NPUtils.getMangaOrChapterAlias(element.select("a").attr("href")))
        title = element.select(".info > b").text().trim()
        thumbnail_url = element.select(".img img").first().attr("src").replace("preview", "cover").let { url ->
            if (url === "") fallbackImageUrl
            else url
        }
    }

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Unused")

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) {
            latestMangaList = HashSet()
            latestUpdatePageOffset = 0
        }
        return GET("$mangaListUrl/${page - 1}")
    }

    override fun latestUpdatesSelector(): String = "a[href]"

    @ExperimentalStdlibApi
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        document.select(".bg-card.card").first().let {
            title = it.select(".card-title.text-silver").text()
            thumbnail_url = it.select(".bg-card.card").select(".p-3.text-white").select("img").first().attr("src").let { url ->
                if (url === "") fallbackImageUrl
                else url
            }

            it.select("table.mt-1").select("tr").let { tr ->
                author = tr[0].select("td").last().text()
                artist = tr[1].select("td").last().text()
                status = when (tr[3].select("td").last().text()) {
                    "Active" -> SManga.ONGOING
                    "Completed" -> SManga.COMPLETED
                    "Licensed" -> SManga.LICENSED
                    else -> SManga.UNKNOWN
                }
            }

            description = it.select(".bg-secondary").text().trim()
            genre = it.select("td[colspan='2'][valign='top']").first().text()
                .replace("Category:", "")
                .split(",").joinToString(", ") { genre ->
                    genre.trim().split("_").joinToString(" ") { str ->
                        if (str.toLowerCase(Locale.getDefault()) == "of") str else str.capitalize(Locale.getDefault())
                    }
                }
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return JSONArray(URL("$chapterContentUrl${NPUtils.getMangaOrChapterAlias(document.location())}").readText()).let { chapterContentJSON ->
            try {
                val pageListJSON = chapterContentJSON.getJSONArray(3)
                val chapterDataJson = chapterContentJSON.getJSONObject(1)

                val pageList: ArrayList<Page> = ArrayList()

                for (i in 0 until pageListJSON.length()) {
                    pageList.add(
                        Page(i, "", pageListJSON.getJSONObject(i).let { pageJSON ->
                            "$chapterImageUrl${chapterDataJson.getString("nc_project_id")}/${pageJSON.getString("chapter_id")}/${pageJSON.getString("value_url")}"
                        })
                    )
                }

                pageList
            } catch (e: JSONException) {
                val pageListNameJSON = chapterContentJSON.getString(3)
                val chapterDataJson = chapterContentJSON.getJSONObject(1)

                val pageListDataJSON = JSONObject(URL("$chapterImageUrl${chapterDataJson.getString("nc_project_id")}/${chapterDataJson.getString("nc_chapter_id")}/$pageListNameJSON").readText())
                val pageListJSON = pageListDataJSON.getJSONArray("pageItem")

                val pageList: ArrayList<Page> = ArrayList()

                for (i in 0 until pageListJSON.length()) {
                    pageList.add(
                        Page(i, "", pageListJSON.getJSONObject(i).let { pageJSON ->
                            "$chapterImageUrl${chapterDataJson.getString("nc_project_id")}/${chapterDataJson.getString("nc_chapter_id")}/${pageJSON.getString("fileName")}"
                        })
                    )
                }

                pageList
            }
        }
    }

    private var popularMangaPageOffset: Int = 0

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page + popularMangaPageOffset))
            .asObservableSuccess()
            .concatMap { response ->
                popularMangaParse(response).let {
                    if ((it.mangas as NPArrayList<SManga>).isListEmpty() && it.mangas.isNotEmpty()) {
                        popularMangaPageOffset++
                        fetchPopularManga(page)
                    } else Observable.just(it)
                }
            }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(popularMangaSelector())

        val mangas = NPArrayList(
            mangaList.map { element -> popularMangaFromElement(element) }.filter { manga ->
                if (!popularMangaList.contains(manga.url)) {
                    popularMangaList.add(manga.url)
                    true
                } else false
            },
            mangaList)

        val hasNextPage = mangaList.isNotEmpty()

        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector(): String? = latestUpdatesNextPageSelector()

    override fun popularMangaRequest(page: Int): Request {
        if (page == 1) {
            popularMangaList = HashSet()
            popularMangaPageOffset = 0
        }
        return GET("$mangaListUrl/${page - 1}")
    }

    override fun popularMangaSelector(): String = latestUpdatesSelector()

    override fun getFilterList(): FilterList = FilterList(
        GenreFilter(),
        StatusFilter()
    )

    private class GenreFilter : Filter.Group<GenreCheckbox>("Genre", NPUtils.Genre.map { genre -> GenreCheckbox(genre.first) })

    private class GenreCheckbox(genre: String) : Filter.CheckBox(genre, false)

    private class StatusFilter : Filter.Group<StatusCheckbox>("Status", NPUtils.Status.map { status -> StatusCheckbox(status.first) })

    private class StatusCheckbox(status: String) : Filter.CheckBox(status, false)

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select(".project_info").select("a").let {
            title = it.text()
            setUrlWithoutDomain(NPUtils.getMangaOrChapterAlias(it.attr("href")))
        }
        thumbnail_url = element.select("img").attr("data-original").let { url ->
            if (url === "") fallbackImageUrl
            else url
        }

        status = when (element.select(".status").text()) {
            "On Going" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Licensed" -> SManga.LICENSED
            else -> SManga.UNKNOWN
        }
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (page > 1) throw Error("No more page")

        var queryString = query

        val genreList: Array<String> = try {
            (filters.find { filter -> filter is GenreFilter } as GenreFilter).state.filter { checkbox -> checkbox.state }.map { checkbox -> checkbox.name }.toTypedArray()
        } catch (e: Exception) {
            emptyArray<String>()
        }.let {
            when {
                it.isNotEmpty() -> it
                NPUtils.getValueOf(NPUtils.Genre, query) == null -> it
                else -> {
                    queryString = ""
                    arrayOf(query)
                }
            }
        }

        val statusList: Array<String> = try {
            (filters.find { filter -> filter is StatusFilter } as StatusFilter).state.filter { checkbox -> checkbox.state }.map { checkbox -> checkbox.name }.toTypedArray()
        } catch (e: Exception) {
            emptyArray()
        }

        return GET("$searchUrl?${NPUtils.getSearchQuery(queryString, genreList, statusList)}")
    }

    override fun searchMangaSelector(): String = ".list_project .item"
}
