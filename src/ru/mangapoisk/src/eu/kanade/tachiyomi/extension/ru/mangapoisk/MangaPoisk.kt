package eu.kanade.tachiyomi.extension.ru.mangapoisk

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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MangaPoisk : ParsedHttpSource() {
    override val name = "MangaPoisk"

    override val baseUrl = "https://mangapoisk.ru"

    override val lang = "ru"

    override val supportsLatest = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/88.0.3987.163 Safari/537.36"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga?sortBy=popular&page=$page", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga?sortBy=-last_chapter_at&page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search?q=$query"
        } else {
            val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is OrderBy -> {
                        val ord = arrayOf("-year", "popular", "name", "-published_at", "-last_chapter_at")[filter.state!!.index]
                        val ordRev = arrayOf("year", "-popular", "-name", "published_at", "last_chapter_at")[filter.state!!.index]
                        url.addQueryParameter("sortBy", if (filter.state!!.ascending) "$ordRev" else "$ord")
                    }
                    is StatusList -> filter.state.forEach { status ->
                        if (status.state) {
                            url.addQueryParameter("translated[]", status.id)
                        }
                    }
                    is GenreList -> filter.state.forEach { genre ->
                        if (genre.state) {
                            url.addQueryParameter("genres[]", genre.id)
                        }
                    }
                }
            }
            return GET(url.toString(), headers)
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "article.card"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaParse(response: Response): MangasPage {
        return popularMangaParse(response)
    }

    private fun getImage(first: Element): String? {
        val image = first.attr("data-src")
        if (image.isNotEmpty()) {
            return image
        }
        return first.attr("src")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            thumbnail_url = getImage(element.select("a > img").first())

            element.select("a.card-about").first().let {
                setUrlWithoutDomain(it.attr("href"))
            }

            element.select("a > h2.entry-title").first().let {
                title = it.text().split("/").first()
            }
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a.page-link"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("article div.card-body").first()
        val manga = SManga.create()
        manga.genre = infoElement.select(".post-info > span:eq(10) > a").joinToString { it.text() }
        manga.description = infoElement.select(".post-info > div .manga-description.entry").text()
        manga.status = parseStatus(infoElement.select(".post-info > span:eq(7)").text())
        manga.thumbnail_url = infoElement.select("img.img-fluid").first().attr("src")
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Статус: Завершена") -> SManga.COMPLETED
        element.contains("Статус: Выпускается") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return client.newCall(chapterListRequest(manga))
            .asObservableSuccess()
            .map { response ->
                chapterListParse(response, manga)
            }
    }
    private fun chapterListParse(response: Response, manga: SManga): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it, manga) }
    }
    override fun chapterListRequest(manga: SManga): Request {
        return GET("$baseUrl${manga.url}/chaptersList", headers)
    }
    override fun chapterListSelector() = ".chapter-item"

    private fun chapterFromElement(element: Element, manga: SManga): SChapter {
        val title = element.select("span.chapter-title").first().text()
        val urlElement = element.select("a").first()
        val urlText = urlElement.text()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))

        chapter.name = urlText.trim()
        chapter.chapter_number = "Глава\\s(\\d+)".toRegex(RegexOption.IGNORE_CASE).find(title)?.groupValues?.get(1)?.toFloat() ?: -1F
        chapter.date_upload = element.select("span.chapter-date").first()?.text()?.let {
            try {
                when {
                    it.contains("минут") -> Date(System.currentTimeMillis() - it.split("\\s".toRegex())[0].toLong() * 60 * 1000).time
                    it.contains("час") -> Date(System.currentTimeMillis() - it.split("\\s".toRegex())[0].toLong() * 60 * 60 * 1000).time
                    it.contains("дня") || it.contains("дней") -> Date(System.currentTimeMillis() - it.split("\\s".toRegex())[0].toLong() * 24 * 60 * 60 * 1000).time
                    else -> SimpleDateFormat("dd MMMM yyyy", Locale("ru")).parse(it)?.time ?: 0L
                }
            } catch (e: Exception) {
                Date(System.currentTimeMillis()).time
            }
        } ?: 0
        return chapter
    }
    override fun pageListParse(document: Document): List<Page> {
        return document.select(".img-fluid.page-image").mapIndexed { index, element ->
            Page(index, "", getImage(element))
        }
    }

    private class CheckFilter(name: String, val id: String) : Filter.CheckBox(name)

    private class StatusList(statuses: List<CheckFilter>) : Filter.Group<CheckFilter>("Статус", statuses)
    private class GenreList(genres: List<CheckFilter>) : Filter.Group<CheckFilter>("Жанры", genres)
    override fun getFilterList() = FilterList(
        OrderBy(),
        StatusList(getStatusList()),
        GenreList(getGenreList())
    )

    private class OrderBy : Filter.Sort(
        "Сортировка",
        arrayOf("Год", "Популярности", "Алфавиту", "Дате добавления", "Дате обновления"),
        Selection(1, false)
    )

    private fun getStatusList() = listOf(
        CheckFilter("Выпускается", "0"),
        CheckFilter("Завершена", "1"),
    )

    private fun getGenreList() = listOf(
        CheckFilter("приключения", "1"),
        CheckFilter("романтика", "2"),
        CheckFilter("боевик", "3"),
        CheckFilter("комедия", "4"),
        CheckFilter("сверхъестественное", "5"),
        CheckFilter("драма", "6"),
        CheckFilter("фэнтези", "7"),
        CheckFilter("сёнэн", "8"),
        CheckFilter("этти", "7"),
        CheckFilter("вампиры", "10"),
        CheckFilter("школа", "11"),
        CheckFilter("сэйнэн", "12"),
        CheckFilter("повседневность", "18"),
        CheckFilter("сёнэн-ай", "19"),
        CheckFilter("гарем", "29"),
        CheckFilter("героическое фэнтези", "30"),
        CheckFilter("боевые искусства", "31"),
        CheckFilter("психология", "38"),
        CheckFilter("сёдзё", "57"),
        CheckFilter("игра", "105"),
        CheckFilter("триллер", "120"),
        CheckFilter("детектив", "121"),
        CheckFilter("трагедия", "122"),
        CheckFilter("история", "123"),
        CheckFilter("сёдзё-ай", "147"),
        CheckFilter("спорт", "160"),
        CheckFilter("научная фантастика", "171"),
        CheckFilter("гендерная интрига", "172"),
        CheckFilter("дзёсэй", "230"),
        CheckFilter("ужасы", "260"),
        CheckFilter("постапокалиптика", "310"),
        CheckFilter("киберпанк", "355"),
        CheckFilter("меха", "356"),
        CheckFilter("эротика", "380"),
        CheckFilter("яой", "612"),
        CheckFilter("самурайский боевик", "916"),
        CheckFilter("махо-сёдзё", "1472"),
        CheckFilter("додзинси", "1785"),
        CheckFilter("кодомо", "1789"),
        CheckFilter("юри", "3197"),
        CheckFilter("арт", "7332"),
        CheckFilter("омегаверс", "7514"),
        CheckFilter("бара", "8119")
    )

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun searchMangaSelector(): String = throw Exception("Not Used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not Used")
}
