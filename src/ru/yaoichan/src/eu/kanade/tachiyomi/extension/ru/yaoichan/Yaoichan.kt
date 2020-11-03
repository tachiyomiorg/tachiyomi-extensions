package eu.kanade.tachiyomi.extension.ru.yaoichan

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

@Nsfw
class Yaoichan : ParsedHttpSource() {

    override val name = "Yaoichan"

    override val baseUrl = "https://yaoi-chan.me"

    override val lang = "ru"

    override val supportsLatest = true

    private val rateLimitInterceptor = RateLimitInterceptor(2)

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor).build()

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/mostfavorites?offset=${20 * (page - 1)}", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga/new?offset=${20 * (page - 1)}", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/?do=search&subaction=search&story=$query&search_start=$page"
        } else {

            var genres = ""
            var order = ""
            var statusParam = true
            var status = ""
            for (filter in if (filters.isEmpty()) getFilterList() else filters) {
                when (filter) {
                    is GenreList -> {
                        filter.state.forEach { f ->
                            if (!f.isIgnored()) {
                                genres += (if (f.isExcluded()) "-" else "") + f.id + '+'
                            }
                        }
                    }
                    is OrderBy -> {
                        if (filter.state!!.ascending && filter.state!!.index == 0) {
                            statusParam = false
                        }
                    }
                    is Status -> status = arrayOf("", "all_done", "end", "ongoing", "new_ch")[filter.state]
                }
            }

            if (genres.isNotEmpty()) {
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("", "&n=favasc", "&n=abcdesc", "&n=chasc")[filter.state!!.index]
                            } else {
                                arrayOf("&n=dateasc", "&n=favdesc", "&n=abcasc", "&n=chdesc")[filter.state!!.index]
                            }
                        }
                    }
                }
                if (statusParam) {
                    "$baseUrl/tags/${genres.dropLast(1)}$order?offset=${20 * (page - 1)}&status=$status"
                } else {
                    "$baseUrl/tags/$status/${genres.dropLast(1)}/$order?offset=${20 * (page - 1)}"
                }
            } else {
                for (filter in filters) {
                    when (filter) {
                        is OrderBy -> {
                            order = if (filter.state!!.ascending) {
                                arrayOf("manga/new", "manga/new&n=favasc", "manga/new&n=abcdesc", "manga/new&n=chasc")[filter.state!!.index]
                            } else {
                                arrayOf("manga/new&n=dateasc", "mostfavorites", "catalog", "sortch")[filter.state!!.index]
                            }
                        }
                    }
                }
                if (statusParam) {
                    "$baseUrl/$order?offset=${20 * (page - 1)}&status=$status"
                } else {
                    "$baseUrl/$order/$status?offset=${20 * (page - 1)}"
                }
            }
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "div.content_row"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.manga_images img").first().attr("src")
        element.select("h2 > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a:contains(Вперед)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = "a:contains(Далее), ${popularMangaNextPageSelector()}"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("table.mangatitle").first()
        val descElement = document.select("div#description").first()
        val imgElement = document.select("img#cover").first()
        val rawCategory = infoElement.select("tr:eq(1) > td:eq(1)").text()
        val category = if (rawCategory.isNotEmpty()) {
            rawCategory.toLowerCase()
        } else {
            "манга"
        }
        val manga = SManga.create()
        manga.author = infoElement.select("tr:eq(2) > td:eq(1)").text()
        manga.genre = infoElement.select("tr:eq(5) > td:eq(1)").text().split(",").plusElement(category).joinToString { it.trim() }
        manga.status = parseStatus(infoElement.select("tr:eq(4) > td:eq(1)").text())
        manga.description = descElement.textNodes().first().text()
        manga.thumbnail_url = imgElement.attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("перевод завершен") -> SManga.COMPLETED
        element.contains("перевод продолжается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "table.table_cha tr:gt(1)"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.date").first()?.text()?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time ?: 0L
        } ?: 0
        return chapter
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body()!!.string()
        val beginIndex = html.indexOf("fullimg\":[") + 10
        val endIndex = html.indexOf(",]", beginIndex)
        val trimmedHtml = html.substring(beginIndex, endIndex).replace("\"", "")
        val pageUrls = trimmedHtml.split(',')

        return pageUrls.mapIndexed { i, url -> Page(i, "", url) }
    }

    override fun pageListParse(document: Document): List<Page> {
        throw Exception("Not used")
    }

    override fun imageUrlParse(document: Document) = ""

    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Тэги", genres)
    private class Genre(name: String, val id: String = name.replace(' ', '_')) : Filter.TriState(name)
    private class Status : Filter.Select<String>("Статус", arrayOf("Все", "Перевод завершен", "Выпуск завершен", "Онгоинг", "Новые главы"))
    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("Дата", "Популярность", "Имя", "Главы"),
        Selection(1, false)
    )

    override fun getFilterList() = FilterList(
        Status(),
        OrderBy(),
        GenreList(getGenreList())
    )

    /* [...document.querySelectorAll("li.sidetag > a:nth-child(1)")]
    *  .map(el => `Genre("${el.getAttribute('href').substr(6)}")`).join(',\n')
    *  on https://yaoi-chan.me/catalog
    */
    private fun getGenreList() = listOf(
        Genre("18 плюс"),
        Genre("bdsm"),
        Genre("арт"),
        Genre("бара"),
        Genre("боевик"),
        Genre("боевые искусства"),
        Genre("вампиры"),
        Genre("веб"),
        Genre("гарем"),
        Genre("гендерная интрига"),
        Genre("героическое фэнтези"),
        Genre("групповой секс"),
        Genre("детектив"),
        Genre("дзёсэй"),
        Genre("додзинси"),
        Genre("драма"),
        Genre("игра"),
        Genre("инцест"),
        Genre("искусство"),
        Genre("история"),
        Genre("киберпанк"),
        Genre("комедия"),
        Genre("литРПГ"),
        Genre("махо-сёдзё"),
        Genre("меха"),
        Genre("мистика"),
        Genre("мужская беременность"),
        Genre("музыка"),
        Genre("научная фантастика"),
        Genre("омегаверс"),
        Genre("переодевание"),
        Genre("повседневность"),
        Genre("постапокалиптика"),
        Genre("приключения"),
        Genre("психология"),
        Genre("романтика"),
        Genre("самурайский боевик"),
        Genre("сборник"),
        Genre("сверхъестественное"),
        Genre("сетакон"),
        Genre("сказка"),
        Genre("спорт"),
        Genre("супергерои"),
        Genre("сэйнэн"),
        Genre("сёдзё"),
        Genre("сёдзё-ай"),
        Genre("сёнэн"),
        Genre("сёнэн-ай"),
        Genre("тентакли"),
        Genre("трагедия"),
        Genre("триллер"),
        Genre("ужасы"),
        Genre("фантастика"),
        Genre("фурри"),
        Genre("фэнтези"),
        Genre("школа"),
        Genre("эротика"),
        Genre("юмор"),
        Genre("юри"),
        Genre("яой"),
        Genre("ёнкома")
    )
}
