package eu.kanade.tachiyomi.extension.ru.mangahub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

open class Mangahub : ParsedHttpSource() {

    override val name = "Mangahub"

    override val baseUrl = "http://mangahub.ru"

    override val lang = "ru"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/explore?search%5Bsort%5D=rating&search%5BdateStart%5D%5Bleft_number%5D=1972&search%5BdateStart%5D%5Bright_number%5D=2018&page=${page + 1}", headers)

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/explore?search%5Bsort%5D=update&search%5BdateStart%5D%5Bleft_number%5D=1972&search%5BdateStart%5D%5Bright_number%5D=2018&page=${page + 1}", headers)

    override fun popularMangaSelector() = "div.list-element"

    override fun latestUpdatesSelector() = "div.list-element"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.list-element__image-back").attr("style").removeSuffix("')").removePrefix("background-image:url('")
        manga.title = element.select("div.list-element__name").text()
        manga.setUrlWithoutDomain(element.select("div.list-element__name > a").attr("href"))
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = ".next"

    override fun latestUpdatesNextPageSelector() = ".next"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
//        val url = HttpUrl.parse("$baseUrl/search/advanced")!!.newBuilder()
//
//        if (!query.isEmpty()) {
//            url.addQueryParameter("q", query)
//        }
//        return GET(url.toString().replace("=%3D", "="), headers)

        return GET("$baseUrl/search/manga?query=$query&page=${page + 1}")
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // max 200 results
    override fun searchMangaNextPageSelector(): Nothing? = null

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.author = if(document.select("[itemprop=\"author\"]") != null) document.select("[itemprop=\"author\"]").text() else null
        manga.genre = document.select("div.b-dtl-desc__labels")[0].text().replace(" ", ", ")
        manga.description = if (document.select("div.b-dtl-desc__desc-info > p").last() != null) document.select("div.b-dtl-desc__desc-info > p").last().text() else null
        manga.status = parseStatus(document)
        manga.thumbnail_url = document.select("div.manga-section-image__img > [itemprop=\"image\"]").attr("src")
        return manga
    }

    private fun parseStatus(element: Document): Int = when {
        element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-completed").size != 0 -> SManga.COMPLETED
        element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-translated").size != 0 && element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-updated").size == 0 -> SManga.COMPLETED
        element.select("div.b-status-label__one > span.b-status-label__name.b-status-label__name-updated").size != 0 -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.b-catalog-list__elem"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("div.b-ovf-table__elem > a").first()
        val chapter = SChapter.create()
        chapter.name = urlElement.text()
        chapter.date_upload = element.select("div.b-catalog-el__date-val").text()?.let {
            SimpleDateFormat("dd.MM.yyyy", Locale.US).parse(it).time
        } ?: 0
        val url = element.select("div.b-ovf-table__elem a").first().attr("href")
        chapter.setUrlWithoutDomain(url)
        return chapter
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        val basic = Regex("""Глава\s([0-9]+)""")
        when {
            basic.containsMatchIn(chapter.name) -> {
                basic.find(chapter.name)?.let {
                    chapter.chapter_number = it.groups[1]?.value!!.toFloat()
                }
            }
        }
    }

//    override fun pageListParse(response: Response): List<Page> {
//        val html = response.body()!!.string()
//        val beginIndex = html.indexOf("rm_h.init( [")
//        val endIndex = html.indexOf("], 0, false);", beginIndex)
//        val trimmedHtml = html.substring(beginIndex, endIndex)
//
//        val p = Pattern.compile("'.*?','.*?',\".*?\"")
//        val m = p.matcher(trimmedHtml)
//
//        val pages = mutableListOf<Page>()
//
//        var i = 0
//        while (m.find()) {
//            val urlParts = m.group().replace("[\"\']+".toRegex(), "").split(',')
//            val url = if (urlParts[1].isEmpty() && urlParts[2].startsWith("/static/")) {
//                baseUrl + urlParts[2]
//            } else {
//                urlParts[1] + urlParts[0] + urlParts[2]
//            }
//            pages.add(Page(i++, "", url))
//        }
//        return pages
//    }

    override fun pageListParse(document: Document): List<Page> {
        val pictures = document.select("div.b-reader.b-reader__full").attr("data-js-scans").replace("&quot;", "\"").replace("\\/", "/")
        val r = Regex("""\/\/([\w\.\/])+""")
        val pages = mutableListOf<Page>()
        var index = 0
        r.findAll(pictures).forEach {
            pages.add(Page(index=index, imageUrl="http:${it.value}"))
            index++
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val imgHeader = Headers.Builder().apply {
            add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
            add("Referer", baseUrl)
        }.build()
        return GET(page.imageUrl!!, imgHeader)
    }

// private class Genre(name: String, val id: String) : Filter.TriState(name)
// private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)
// private class Category(categories: List<Genre>) : Filter.Group<Genre>("Category", categories)

    /* [...document.querySelectorAll("tr.advanced_option:nth-child(1) > td:nth-child(3) span.js-link")]
    *  .map(el => `Genre("${el.textContent.trim()}", "${el.getAttribute('onclick')
    *  .substr(31,el.getAttribute('onclick').length-33)"})`).join(',\n')
    *  on http://mintmanga.com/search/advanced
    */
//    override fun getFilterList() = FilterList(
//            Category(getCategoryList()),
//            GenreList(getGenreList())
//    )

//    private fun getCategoryList() = listOf(
//            Genre("В цвете", "el_4614"),
//            Genre("Веб", "el_1355"),
//            Genre("Выпуск приостановлен", "el_5232"),
//            Genre("Ёнкома", "el_2741"),
//            Genre("Комикс западный", "el_1903"),
//            Genre("Комикс русский", "el_2173"),
//            Genre("Манхва", "el_1873"),
//            Genre("Маньхуа", "el_1875"),
//            Genre("Не Яой", "el_1874"),
//            Genre("Ранобэ", "el_5688"),
//            Genre("Сборник", "el_1348")
//    )
//
//    private fun getGenreList() = listOf(
//            Genre("арт", "el_2220"),
//            Genre("бара", "el_1353"),
//            Genre("боевик", "el_1346"),
//            Genre("боевые искусства", "el_1334"),
//            Genre("вампиры", "el_1339"),
//            Genre("гарем", "el_1333"),
//            Genre("гендерная интрига", "el_1347"),
//            Genre("героическое фэнтези", "el_1337"),
//            Genre("детектив", "el_1343"),
//            Genre("дзёсэй", "el_1349"),
//            Genre("додзинси", "el_1332"),
//            Genre("драма", "el_1310"),
//            Genre("игра", "el_5229"),
//            Genre("история", "el_1311"),
//            Genre("киберпанк", "el_1351"),
//            Genre("комедия", "el_1328"),
//            Genre("меха", "el_1318"),
//            Genre("мистика", "el_1324"),
//            Genre("научная фантастика", "el_1325"),
//            Genre("омегаверс", "el_5676"),
//            Genre("повседневность", "el_1327"),
//            Genre("постапокалиптика", "el_1342"),
//            Genre("приключения", "el_1322"),
//            Genre("психология", "el_1335"),
//            Genre("романтика", "el_1313"),
//            Genre("самурайский боевик", "el_1316"),
//            Genre("сверхъестественное", "el_1350"),
//            Genre("сёдзё", "el_1314"),
//            Genre("сёдзё-ай", "el_1320"),
//            Genre("сёнэн", "el_1326"),
//            Genre("сёнэн-ай", "el_1330"),
//            Genre("спорт", "el_1321"),
//            Genre("сэйнэн", "el_1329"),
//            Genre("трагедия", "el_1344"),
//            Genre("триллер", "el_1341"),
//            Genre("ужасы", "el_1317"),
//            Genre("фантастика", "el_1331"),
//            Genre("фэнтези", "el_1323"),
//            Genre("школа", "el_1319"),
//            Genre("эротика", "el_1340"),
//            Genre("этти", "el_1354"),
//            Genre("юри", "el_1315"),
//            Genre("яой", "el_1336")
//    )
}