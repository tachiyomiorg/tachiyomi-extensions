package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

open class NineManga(override val name: String, override val baseUrl: String, override val lang: String) : ParsedHttpSource() {

    override val supportsLatest: Boolean = true

    private fun newHeaders() = super.headersBuilder()
        .add("Accept-Language", "es-ES,es;q=0.9,en;q=0.8,gl;q=0.7")
        .add("Host", baseUrl.substringAfterLast("/")) // like: es.ninemanga.com
        .add("Connection", "keep-alive")
        .add("Upgrade-Insecure-Requests", "1")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) Gecko/20100101 Firefox/60")
        .build()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/list/New-Update/", headers) // "$baseUrl/category/updated_$page.html"

    override fun latestUpdatesSelector() = "dl.bookinfo"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("a.bookname").let {
            url = it.attr("href").substringAfter(baseUrl)
            title = it.text()
        }
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun latestUpdatesNextPageSelector() = "ul.pageList > li:last-child > a.l"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/index_$page.html", headers)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.select("div.bookintro").let {
            title = it.select("li > span:not([class])").text()
            genre = it.select("li[itemprop=genre] a").joinToString { e -> e.text() }
            author = it.select("li a[itemprop=author]").text()
            status = parseStatus(it.select("li a.red").first().text())
            description = it.select("p[itemprop=description]").text()
            thumbnail_url = it.select("img[itemprop=image]").attr("abs:src")
        }
    }

    open fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET(baseUrl + manga.url + "?waring=1", headers) // Bypasses adult content warning
    }

    override fun chapterListSelector() = "ul.sub_vol_ul > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.select("a.chapter_list_a").let {
            name = it.text()
            url = it.attr("href").substringAfter(baseUrl).replace("%20", " ")
        }
        date_upload = parseChapterDate(element.select("span").text())
    }

    open fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")

        if (dateWords.size == 3) {
            if (dateWords[1].contains(",")) {
                return try {
                    SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
                } catch (e: ParseException) {
                    0L
                }
            } else {
                val timeAgo = Integer.parseInt(dateWords[0])
                return Calendar.getInstance().apply {
                    when (dateWords[1]) {
                        "minutes" -> Calendar.MINUTE
                        "hours" -> Calendar.HOUR
                        else -> null
                    }?.let {
                        add(it, -timeAgo)
                    }
                }.timeInMillis
            }
        }
        return 0L
    }

    override fun pageListRequest(chapter: SChapter) = GET(baseUrl + chapter.url, newHeaders())

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select("select#page").first().select("option").forEach {
            add(Page(size, baseUrl + it.attr("value")))
        }
    }

    override fun imageUrlRequest(page: Page) = GET(page.url, newHeaders())

    override fun imageUrlParse(document: Document) = document.select("div.pic_box img.manga_pic").first().attr("src").orEmpty()

    /*override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/?name_sel=&wd=$query&author_sel=&author=&artist_sel=&artist=&category_id=&out_category_id=&completed_series=&page=$page.html", headers)
    }*/

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/search/")!!.newBuilder()

        url.addQueryParameter("wd", query)
        url.addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state
                        .filter { genre -> genre.state }
                        .forEach { genre -> url.addQueryParameter("category_id", genre.id) }
                }
            }
        }

        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    open class Genre(name: String, val id: String) : Filter.CheckBox(name)
    open class GenreList(genres: List<Genre>) : Filter.Group<Genre>("Genre", genres)

    override fun getFilterList() = FilterList(
        GenreList(getGenreList())
    )

    // $(document.querySelectorAll('.optionbox .typelist:nth-child(3) ul li.cate_list')).map((i, el)=>`Genre("${$(el).first().text().trim()}", "${$(el).attr("cate_id")}")`).get().sort().join(",\n")
    // https://en.ninemanga.com/search/?type=high
    open fun getGenreList() = listOf(
        Genre("4-Koma", "56"),
        Genre("Action", "1"),
        Genre("Adventure", "2"),
        Genre("Anime", "3"),
        Genre("Award Winning", "59"),
        Genre("Bara", "84"),
        Genre("Comedy", "4"),
        Genre("Cooking", "5"),
        Genre("Crime", "132"),
        Genre("Demons", "49"),
        Genre("Doujinshi", "45"),
        Genre("Drama", "6"),
        Genre("Fantasy", "8"),
        Genre("Game", "126"),
        Genre("Gender Bender", "9"),
        Genre("Historical", "11"),
        Genre("Horror", "12"),
        Genre("Isekai", "127"),
        Genre("Josei", "13"),
        Genre("Live Action", "14"),
        Genre("Magic", "47"),
        Genre("Magical Girls", "130"),
        Genre("Manhua", "15"),
        Genre("Manhwa", "16"),
        Genre("Martial Arts", "17"),
        Genre("Matsumoto Tomokicomedy", "37"),
        Genre("Mecha", "18"),
        Genre("Medical", "19"),
        Genre("Military", "51"),
        Genre("Music", "20"),
        Genre("Mystery", "21"),
        Genre("N/A", "54"),
        Genre("None", "64"),
        Genre("One Shot", "22"),
        Genre("Oneshot", "57"),
        Genre("Philosophical", "133"),
        Genre("Psychological", "23"),
        Genre("Reverse Harem", "55"),
        Genre("Romance Shoujo", "38"),
        Genre("Romance", "24"),
        Genre("School Life", "25"),
        Genre("Sci-Fi", "26"),
        Genre("Seinen", "27"),
        Genre("Shoujo Ai", "44"),
        Genre("Shoujo", "28"),
        Genre("Shoujo-Ai", "29"),
        Genre("Shoujoai", "48"),
        Genre("Shounen Ai", "42"),
        Genre("Shounen", "30"),
        Genre("Shounen-Ai", "31"),
        Genre("Shounenai", "46"),
        Genre("Slice Of Life", "32"),
        Genre("Sports", "33"),
        Genre("Staff Pick", "60"),
        Genre("Super Power", "62"),
        Genre("Superhero", "131"),
        Genre("Supernatural", "34"),
        Genre("Suspense", "53"),
        Genre("Thriller", "129"),
        Genre("Tragedy", "35"),
        Genre("Vampire", "52"),
        Genre("Webtoon", "58"),
        Genre("Webtoons", "50"),
        Genre("Wuxia", "128"),
        Genre("[No Chapters]", "61")
    )
}
