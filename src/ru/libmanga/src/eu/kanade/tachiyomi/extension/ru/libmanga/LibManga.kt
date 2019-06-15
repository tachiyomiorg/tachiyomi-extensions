package eu.kanade.tachiyomi.extension.ru.libmanga

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*
import android.util.Base64.decode as base64Decode


open class LibManga(override val name: String, override val baseUrl: String, private val staticUrl: String) : ParsedHttpSource() {

    override val lang = "ru"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val jsonParser = JsonParser()

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/manga-list?dir=desc&sort=views&page=$page", headers)

    override fun popularMangaSelector() = "div.manga-block-item"

    override fun popularMangaFromElement(element: Element): SManga {
        val item = element.select("a.manga-block-item__content").first()
        val manga = SManga.create()
        manga.thumbnail_url = item.attr("data-src")
        manga.setUrlWithoutDomain(item.attr("href"))
        manga.title = item.select("h3.manga-block-item__name").first().text()
        return manga
    }

    override fun popularMangaNextPageSelector() = "a[rel=\"next\"]"

    override fun mangaDetailsParse(document: Document): SManga {
        val body = document.select("div.section__body").first()
        val manga = SManga.create()
        manga.title = body.select(".manga__title").text()
        manga.thumbnail_url = body.select(".manga__cover").attr("src")
        manga.author = body.select(".info-list__row:nth-child(2) > a").text()
        manga.artist = body.select(".info-list__row:nth-child(3) > a").text()
        manga.status = when (
            body.select(".info-list__row:has(strong:contains(Перевод))")
                .first()
                .select("span.m-label_info")
                .text())
        {
            "продолжается" -> SManga.ONGOING
            "завершен" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        manga.genre = body.select(".info-list__row:has(strong:contains(Жанры)) > a").joinToString { it.text() }
        manga.description = body.select(".info-desc__content").text()
        return manga
    }

    override fun chapterListSelector() = "div.chapter-item"

    override fun chapterFromElement(element: Element): SChapter {
        val chapterLink = element.select("div.chapter-item__name > a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(chapterLink.attr("href"))
        chapter.name = chapterLink.text()
        chapter.date_upload = SimpleDateFormat("dd.MM.yyyy", Locale.US)
                .parse(element.select("div.chapter-item__date").text()).time
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        """Глава\s(\d+)""".toRegex().find(chapter.name)?.let {
            val number = it.groups[1]?.value!!
            chapter.chapter_number = number.toFloat()
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val chapInfo = document
            .select("script:containsData(window.__info)")
            .first()
            .html()
            .replace("window.__info = ", "")
            .replace(";", "")

        val chapInfoJson = jsonParser.parse(chapInfo).obj

        // Get pages
        val baseStr = document.select("span.pp")
            .first()
            .html()
            .replace("<!--", "")
            .replace("-->", "")
            .trim()

        val decodedArr = base64Decode(baseStr, android.util.Base64.DEFAULT)
        val pagesJson = jsonParser.parse(String(decodedArr)).array

        val pages = mutableListOf<Page>()
        pagesJson.forEach { page ->
            pages.add(Page(page["p"].int, "", staticUrl + chapInfoJson["imgUrl"].string + page["u"].string))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.updates__left"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val link = element.select("a").first()
        val img = link.select("img").first()
        val manga = SManga.create()
        manga.thumbnail_url = img.attr("data-src")
            .replace("cover_thumb", "cover_250x350")
        manga.setUrlWithoutDomain(link.attr("href"))
        manga.title = img.attr("alt")
        return manga
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/manga-list?page=$page")!!.newBuilder()
        if (query.isNotEmpty()) {
            url.addQueryParameter("name", query)
        }
        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
                is CategoryList -> filter.state.forEach { category ->
                    if (category.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter("types[]", category.id)
                    }
                }
                is StatusList -> filter.state.forEach { status ->
                    if (status.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter("status[]", status.id)
                    }
                }
                is GenreList -> filter.state.forEach { genre ->
                    if (genre.state != Filter.TriState.STATE_IGNORE) {
                        url.addQueryParameter(if (genre.isIncluded()) "includeGenres[]" else "excludeGenres[]", genre.id)
                    }
                }
                is OrderBy -> {
                    url.addQueryParameter("dir", if (filter.state!!.ascending) "asc" else "desc")
                    url.addQueryParameter("sort", arrayOf("rate", "name", "views", "created_at")[filter.state!!.index])
                }
            }
        }
        return GET(url.toString(), headers)
    }

    // Hack search method to add some results from search popup
    override fun searchMangaParse(response: Response): MangasPage {
        val searchRequest = response.request().url().queryParameter("name")
        val mangas = mutableListOf<SManga>()
        if (!searchRequest.isNullOrEmpty()) {
            val popupSearchHeaders = headers
                    .newBuilder()
                    .add("Accept", "application/json, text/plain, */*")
                    .add("X-Requested-With", "XMLHttpRequest")
                    .build()

            // +200ms
            val popup = client.newCall(
                    GET("https://mangalib.me/search?query=$searchRequest", headers = popupSearchHeaders)
            ).execute().body()!!.string()
            val jsonList = jsonParser.parse(popup).asJsonArray
            jsonList.forEach {
                val element = it.asJsonObject
                val manga = SManga.create()
                manga.setUrlWithoutDomain("/" + element.get("slug").string)
                manga.description = element.get("summary").nullString
                manga.author = element.get("author").nullString
                manga.title = element.get("name").string
                val status = element.get("status_id").int
                manga.status = if (status > 2) 2 else status
                mangas.add(manga)
            }

        }
        val document = response.asJsoup()

        val searchedMangas = document.select(searchMangaSelector()).map { element ->
            searchMangaFromElement(element)
        }

        // Filtered out what find in popup search
        mangas.addAll(searchedMangas.filter { search ->
            mangas.find { search.title == it.title } == null
        })


        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)

    private class CategoryList(categories: List<SearchFilter>) : Filter.Group<SearchFilter>("Категории", categories)
    private class StatusList(statuses: List<SearchFilter>) : Filter.Group<SearchFilter>("Статус", statuses)
    private class GenreList(genres: List<SearchFilter>) : Filter.Group<SearchFilter>("Жанры", genres)

    override fun getFilterList() = FilterList(
            CategoryList(getCategoryList()),
            StatusList(getStatusList()),
            GenreList(getGenreList()),
            OrderBy()
    )

    private class OrderBy : Filter.Sort("Сортировка",
            arrayOf("Рейтинг", "Имя", "Просмотры", "Дата"),
            Filter.Sort.Selection(0, false))

    /*
    * Use console
    * Object.entries(__FILTER_ITEMS__.types).map(([k, v]) => `SearchFilter("${v.label}", "${v.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getCategoryList() = listOf(
            SearchFilter("Манга", "1"),
            SearchFilter("OEL-манга", "4"),
            SearchFilter("Манхва", "5"),
            SearchFilter("Маньхуа", "6"),
            SearchFilter("Сингл", "7"),
            SearchFilter("Руманга", "8"),
            SearchFilter("Комикс западный", "9")
    )

    /*
    * Use console
    * Object.entries(__FILTER_ITEMS__.status).map(([k, v]) => `SearchFilter("${v.label}", "${v.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getStatusList() = listOf(
            SearchFilter("Продолжается", "1"),
            SearchFilter("Завершен", "2"),
            SearchFilter("Заморожен", "3")
    )

    /*
    * Use console
    * __FILTER_ITEMS__.genres.map(it => `SearchFilter("${it.name}", "${it.id}")`).join(',\n')
    * on /manga-list
    */
    private fun getGenreList() = listOf(
            SearchFilter("арт", "32"),
            SearchFilter("бара", "33"),
            SearchFilter("боевик", "34"),
            SearchFilter("боевые искусства", "35"),
            SearchFilter("вампиры", "36"),
            SearchFilter("гарем", "37"),
            SearchFilter("гендерная интрига", "38"),
            SearchFilter("героическое фэнтези", "39"),
            SearchFilter("детектив", "40"),
            SearchFilter("дзёсэй", "41"),
            SearchFilter("додзинси", "42"),
            SearchFilter("драма", "43"),
            SearchFilter("игра", "44"),
            SearchFilter("история", "45"),
            SearchFilter("киберпанк", "46"),
            SearchFilter("комедия", "47"),
            SearchFilter("махо-сёдзё", "48"),
            SearchFilter("меха", "49"),
            SearchFilter("мистика", "50"),
            SearchFilter("научная фантастика", "51"),
            SearchFilter("повседневность", "52"),
            SearchFilter("постапокалиптика", "53"),
            SearchFilter("приключения", "54"),
            SearchFilter("психология", "55"),
            SearchFilter("романтика", "56"),
            SearchFilter("самурайский боевик", "57"),
            SearchFilter("сверхъестественное", "58"),
            SearchFilter("сёдзё", "59"),
            SearchFilter("сёдзё-ай", "60"),
            SearchFilter("сёнэн", "61"),
            SearchFilter("сёнэн-ай", "62"),
            SearchFilter("спорт", "63"),
            SearchFilter("сэйнэн", "64"),
            SearchFilter("трагедия", "65"),
            SearchFilter("триллер", "66"),
            SearchFilter("ужасы", "67"),
            SearchFilter("фантастика", "68"),
            SearchFilter("фэнтези", "69"),
            SearchFilter("школа", "70"),
            SearchFilter("эротика", "71"),
            SearchFilter("этти", "72"),
            SearchFilter("юри", "73"),
            SearchFilter("яой", "74"),
            SearchFilter("ёнкома", "75"),
            SearchFilter("кодомо", "76"),
            SearchFilter("омегаверс", "77")
    )
}
