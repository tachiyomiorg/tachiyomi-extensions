package eu.kanade.tachiyomi.extension.en.rawlh

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Rawlh : ParsedHttpSource() {

    override val name = "RawLH"

    override val baseUrl = "http://rawlh.com"

    override val lang = "en"

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request =
            GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&ungenre=&sort=views&sort_type=DESC", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/manga-list.html?m_status=&author=&group=&name=$query", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request =
            GET("$baseUrl/manga-list.html?listType=pagination&page=$page&artist=&author=&group=&m_status=&name=&genre=&sort=last_update&sort_type=DESC")

    override fun popularMangaSelector() = "div.media"

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun searchMangaSelector() = popularMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("h3 > a").first().let {
            manga.setUrlWithoutDomain("/" + it.attr("href"))
            manga.title = it.text()
        }
        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga =
            popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun popularMangaNextPageSelector() = "a:contains(»)"

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    private fun searchGenresNextPageSelector() = popularMangaNextPageSelector()


    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.row").first()
        manga.author = infoElement.select("small a.btn.btn-xs.btn-info").first()?.text()
        manga.genre = infoElement.select("ul.manga-info li:nth-child(3) small").first()?.text()
        manga.status = parseStatus(infoElement.select("a.btn.btn-xs.btn-success").first().text())

        manga.description = document.select("div.row > p").first()?.text()
        var imgUrl = document.select("img.thumbnail").first()?.attr("src")
        if (imgUrl!!.startsWith("app/")) {
            manga.thumbnail_url = "$baseUrl/$imgUrl"
        } else {
            manga.thumbnail_url = imgUrl
        }
        return manga
    }

    private fun parseStatus(element: String): Int = when {
        element.contains("Completed") -> SManga.COMPLETED
        element.contains("Ongoing") -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = " table.table.table-hover tbody tr"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("td a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/" + urlElement.attr("href"))
        chapter.name = urlElement.text()
//        TODO:
//        chapter.date_upload = element.select("div.date").first()?.text()?.let {
//            SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it).time
//        } ?: 0
        chapter.date_upload = 0
        return chapter
    }


    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select("img.chapter-img").forEach {
            var url = it.attr("src")
            if (url != "") {
                pages.add(Page(pages.size, "", url))
            }
        }
        return pages
    }

    override fun imageUrlParse(document: Document) = ""



//    private class OrderBy : Filter.Sort("Сортировка",
//            arrayOf("Дата", "Популярность", "Имя", "Главы"),
//            Filter.Sort.Selection(1, false))


    private class Status : Filter.Select<String>("Status", arrayOf("Any", "Ongoing", "Completed"))
    private class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)
    private class Genre(name: String, val id: String = name.replace(' ', '_')) : Filter.TriState(name)


    // TODO: Country
    override fun getFilterList() = FilterList(
            Status(),
            GenreList(getGenreList())
    )


    /* [...document.querySelectorAll("div.panel-body a")].map((el,i) =>
    * { return `Genre("${el.innerHTML}")` }).join(',\n')
    *  on http://rawlh.com/search
    */
    private fun getGenreList() = listOf(
            Genre("4-Koma"),
            Genre("Action"),
            Genre("Adult"),
            Genre("Adventure"),
            Genre("Isekai"),
            Genre("Comedy"),
            Genre("Comic"),
            Genre("Cooking"),
            Genre("Doujinshi"),
            Genre("Drama"),
            Genre("Ecchi"),
            Genre("Fantasy"),
            Genre("Gender Bender"),
            Genre("Harem"),
            Genre("Historical"),
            Genre("Horror"),
            Genre("Josei"),
            Genre("Lolicon"),
            Genre("Manga"),
            Genre("Manhua"),
            Genre("Manhwa"),
            Genre("Martial Art"),
            Genre("Mature"),
            Genre("Mecha"),
            Genre("Medical"),
            Genre("Music"),
            Genre("Mystery"),
            Genre("One shot"),
            Genre("Psychological"),
            Genre("Romance"),
            Genre("School Life"),
            Genre("Sci-fi"),
            Genre("Seinen"),
            Genre("Shoujo"),
            Genre("Shojou Ai"),
            Genre("Shounen"),
            Genre("Shounen Ai"),
            Genre("Slice of Life"),
            Genre("Smut"),
            Genre("Sports"),
            Genre("Supernatural"),
            Genre("Tragedy"),
            Genre("Webtoon"),
            Genre("Yaoi"),
            Genre("Yuri")
    )
}
