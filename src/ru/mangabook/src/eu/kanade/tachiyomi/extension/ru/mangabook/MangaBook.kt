package eu.kanade.tachiyomi.extension.ru.mangabook

import com.github.salomonbrys.kotson.forEach
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaBook : ParsedHttpSource() {
    override val name = "MangaBook"

    override val baseUrl = "https://mangabook.org"

    override val lang = "ru"

    override val supportsLatest = true

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.163 Safari/537.36"

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", baseUrl)

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/filterList?page=$page&ftype[]=0&status[]=0&sortBy=rate", headers)

    override fun latestUpdatesRequest(page: Int) = GET(baseUrl, headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            "$baseUrl/search-ajax/?query=$query"
        } else {
            var ret = String()
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        ret = "$baseUrl/genre/${filter.values[filter.state].id}/page/$page"
                    }
                }
            }
            ret
        }
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "article.short .short-in"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaParse(response: Response): MangasPage = throw Exception("Not Used")

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select(".sh-desc a").first().let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.select("div.sh-title").text().split(" / ").last()
        }
        thumbnail_url = element.select(".short-poster.img-box > img").attr("abs:src")
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not Used")

    override fun popularMangaNextPageSelector() = "a.page-link[rel=next]"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = throw Exception("Not Used")

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("article.full .fmid").first()
        val manga = SManga.create()
        manga.genre = infoElement.select(".vis:contains(Категории:)").text().removePrefix("Категории:").split(",").plusElement("Комикс").joinToString { it.trim() }
        manga.description = infoElement.select(".fdesc.slice-this").text()
        manga.thumbnail_url = infoElement.select("img.img-responsive").first().attr("src")

        return manga
    }

    override fun chapterListSelector(): String = ".chapters li:not(.volume )"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val link = element.select("h5 a")
        name = link.text().trim()
        chapter_number = name.substringAfter("Глава №").substringBefore(":").trim().toFloat()
        setUrlWithoutDomain(link.attr("abs:href") + "/1")
        date_upload = parseDate(element.select(".date-chapter-title-rtl").text().trim())
    }
    private fun parseDate(date: String): Long {
        return SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(date)?.time ?: 0
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".reader-images img.img-responsive").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src").replace(" ", ""))
        }
    }

    private class Genre(name: String, val id: String) : Filter.CheckBox(name) {
        override fun toString(): String {
            return name
        }
    }

    private class GenreList(genres: Array<Genre>) : Filter.Select<Genre>("Genres", genres, 0)

    override fun getFilterList() = FilterList(
        GenreList(getGenreList())
    )

    /*  [...document.querySelectorAll(".categories .item")]
    *     .map(el => `Genre("${el.textContent.trim()}", "${el.getAttribute('href')}")`).join(',\n')
    *   on https://manga-online.biz/genre/all/
    */
    private fun getGenreList() = arrayOf(
        Genre("Все", "all"),
        Genre("Боевик", "boevik"),
        Genre("Боевые искусства", "boevye_iskusstva"),
        Genre("Вампиры", "vampiry"),
        Genre("Гарем", "garem"),
        Genre("Гендерная интрига", "gendernaya_intriga"),
        Genre("Героическое фэнтези", "geroicheskoe_fehntezi"),
        Genre("Детектив", "detektiv"),
        Genre("Дзёсэй", "dzyosehj"),
        Genre("Додзинси", "dodzinsi"),
        Genre("Драма", "drama"),
        Genre("Игра", "igra"),
        Genre("История", "istoriya"),
        Genre("Меха", "mekha"),
        Genre("Мистика", "mistika"),
        Genre("Научная фантастика", "nauchnaya_fantastika"),
        Genre("Повседневность", "povsednevnost"),
        Genre("Постапокалиптика", "postapokaliptika"),
        Genre("Приключения", "priklyucheniya"),
        Genre("Психология", "psihologiya"),
        Genre("Романтика", "romantika"),
        Genre("Самурайский боевик", "samurajskij_boevik"),
        Genre("Сверхъестественное", "sverhestestvennoe"),
        Genre("Сёдзё", "syodzyo"),
        Genre("Сёдзё-ай", "syodzyo-aj"),
        Genre("Сёнэн", "syonen"),
        Genre("Спорт", "sport"),
        Genre("Сэйнэн", "sejnen"),
        Genre("Трагедия", "tragediya"),
        Genre("Триллер", "triller"),
        Genre("Ужасы", "uzhasy"),
        Genre("Фантастика", "fantastika"),
        Genre("Фэнтези", "fentezi"),
        Genre("Школа", "shkola"),
        Genre("Этти", "etti"),
        Genre("Юри", "yuri"),
        Genre("Военный", "voennyj"),
        Genre("Жосей", "zhosej"),
        Genre("Магия", "magiya"),
        Genre("Полиция", "policiya"),
        Genre("Смена пола", "smena-pola"),
        Genre("Супер сила", "super-sila"),
        Genre("Эччи", "echchi"),
        Genre("Яой", "yaoj"),
        Genre("Сёнэн-ай", "syonen-aj")
    )

    override fun imageUrlParse(document: Document) = throw Exception("Not Used")

    override fun searchMangaSelector(): String = throw Exception("Not Used")
}
