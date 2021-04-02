package eu.kanade.tachiyomi.extension.ru.mangapoisk

import com.github.salomonbrys.kotson.forEach
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
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
            val url = HttpUrl.parse("$baseUrl/manga")!!.newBuilder()
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

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            var img = element.select("a > img").first().attr("data-src")
            if (img.isEmpty()) {
                img = element.select("a > img").first().attr("src")
            }
            thumbnail_url = img

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
    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body()!!.string()

        val jsonData = html.split("App.Collection.MangaChapter(").last().split("]);").first() + "]"
        val mangaName = html.split("mangaName: '").last().split("' });").first()
        val json = JsonParser().parse(jsonData).asJsonArray
        val chapterList = mutableListOf<SChapter>()
  /*      json.forEach {
            chapterList.add(chapterFromElement(mangaName, it.asJsonObject))
        }*/
        return chapterList
    }

    override fun chapterListSelector() = "chapter-item > a"

    private fun chapterFromElement(element: Element, manga: SManga): SChapter {
        val urlElement = element.select("a").first()
        val urlText = urlElement.text()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))

        chapter.name = urlText.removeSuffix(" новое").trim()
        if (manga.title.length > 25) {
            for (word in manga.title.split(' ')) {
                chapter.name = chapter.name.removePrefix(word).trim()
            }
        }
        val dots = chapter.name.indexOf("…")
        val numbers = chapter.name.findAnyOf(IntRange(0, 9).map { it.toString() })?.first ?: 0

        if (dots in 0 until numbers) {
            chapter.name = chapter.name.substringAfter("…").trim()
        }

        chapter.date_upload = element.select("td.hidden-xxs").last()?.text()?.let {
            try {
                SimpleDateFormat("dd.MM.yy", Locale.US).parse(it)?.time ?: 0L
            } catch (e: ParseException) {
                SimpleDateFormat("dd/MM/yy", Locale.US).parse(it)?.time ?: 0L
            }
        } ?: 0
        return chapter
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body()!!.string()
        val jsonData = html.split("new App.Router.Chapter(").last().split("});").first() + "}"
        val json = JsonParser().parse(jsonData).asJsonObject
        val cdnUrl = json.get("srcBaseUrl").string
        val pages = json.get("pages").asJsonObject
        val resPages = mutableListOf<Page>()
        pages.forEach { page, jsonElement ->
            resPages.add(Page(page.toInt(), imageUrl = "$cdnUrl/${jsonElement.asJsonObject.get("src").string}"))
        }
        return resPages
    }

    private class SearchFilter(name: String, val id: String) : Filter.TriState(name)
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
    )

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun searchMangaSelector(): String = throw Exception("Not Used")

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not Used")

    override fun pageListParse(document: Document): List<Page> = throw Exception("Not Used")
}
