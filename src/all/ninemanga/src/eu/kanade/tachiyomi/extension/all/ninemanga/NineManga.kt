package eu.kanade.tachiyomi.extension.all.ninemanga

import eu.kanade.tachiyomi.source.model.*
import okhttp3.Request
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

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

    override fun latestUpdatesSelector() = "ul.direlist > li"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("dl.bookinfo").let {
            setUrlWithoutDomain(it.select("dd > a.bookname").attr("href") + "?waring=1") // To removes warning message and shows chapter list.
            title = it.select("dd > a.bookname").first().text()
            thumbnail_url = it.select("dt > a > img").attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector() = "ul.pageList > li:last-child > a.l"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/index_$page.html", headers)

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsParse(document: Document) =  SManga.create().apply {
        document.select("div.bookintro").let {
            thumbnail_url = document.select("a.bookface > img").attr("src")
            genre = document.select("li[itemprop=genre] > a").joinToString(", ") {
                it.text()
            }
            author = it.select("a[itemprop=author]").text()
            artist = ""
            description = it.select("p[itemprop=description]").text().orEmpty()
            status = parseStatus(it.select("a.red").first().text().orEmpty())
        }
    }

    private fun parseStatus(status: String) = when { // TODO: agregar estados para cada idioma
        status.contains("En curso") -> SManga.ONGOING // ES
        status.contains("Ongoing") -> SManga.ONGOING // EN
        status.contains("Laufende") -> SManga.ONGOING // DE
        status.contains("In corso") -> SManga.ONGOING // IT
        status.contains("Em tradução") -> SManga.COMPLETED // BR
        status.contains("En cours") -> SManga.COMPLETED // FR

        status.contains("Completo") -> SManga.COMPLETED // ES & BR
        status.contains("Completed") -> SManga.COMPLETED // EN
        status.contains("завершенный") -> SManga.COMPLETED // RU
        status.contains("Abgeschlossen") -> SManga.COMPLETED // DE
        status.contains("Completato") -> SManga.COMPLETED // IT
        status.contains("Complété") -> SManga.COMPLETED // FR
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.sub_vol_ul > li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.select("a.chapter_list_a").let {
            name = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        date_upload = parseChapterDate(element.select("span").text())
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords = date.split(" ")
        var timeAgo = 0

        if (dateWords.size == 2) { // Aleman
            timeAgo = Integer.parseInt(dateWords[0])
            return Calendar.getInstance().apply {
                when (dateWords[1]) {
                    "Stunden" -> Calendar.HOUR // Aleman - 2 palabras
                    else -> null
                }?.let {
                    add(it, -timeAgo)
                }
            }.timeInMillis
        }
        if (dateWords.size == 3) {
            if(dateWords[1].contains(",")){
                try {
                    return SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH).parse(date).time
                } catch (e: ParseException) {
                    return 0L
                }
            }else{
                timeAgo = Integer.parseInt(dateWords[0])
                return Calendar.getInstance().apply {
                    when (dateWords[1]) {
                        "minutos" -> Calendar.MINUTE // ES y BR
                        "minutes" -> Calendar.MINUTE // EN
                        "минут" -> Calendar.MINUTE // RU
                        "minuti" -> Calendar.MINUTE // IR

                        "horas" -> Calendar.HOUR // ES
                        "hours" -> Calendar.HOUR // EN
                        "часа" -> Calendar.HOUR // RU
                        "ore" -> Calendar.HOUR // IR
                        "hora" -> Calendar.HOUR // BR
                        else -> null
                    }?.let {
                        add(it, -timeAgo)
                    }
                }.timeInMillis
            }
        }
        if(dateWords.size == 5) { // FR
            timeAgo = Integer.parseInt(dateWords[3])
            return Calendar.getInstance().apply {
                when (dateWords[4]) {
                    "minutes" -> Calendar.MINUTE
                    "heures" -> Calendar.HOUR
                    else -> null
                }?.let {
                    add(it, -timeAgo)
                }
            }.timeInMillis
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/search/?name_sel=&wd=$query&author_sel=&author=&artist_sel=&artist=&category_id=&out_category_id=&completed_series=&page=$page.html", headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    //TODO: Implement filters list.
    // Array.from(document.querySelectorAll('.optionbox .typelist:nth-child(3) ul')).map(a => Array.from(a.querySelectorAll('li')).map(b => `Genre("${b.querySelector('label').innerText}", "${a.querySelector('li[cate_id]').getAttribute('cate_id')}")`)).join(',\n')
    // http://es.ninemanga.com/search/?name_sel=contain&wd=&author_sel=contain&author=&artist_sel=contain&artist=&category_id=&out_category_id=&completed_series=either&type=high
}