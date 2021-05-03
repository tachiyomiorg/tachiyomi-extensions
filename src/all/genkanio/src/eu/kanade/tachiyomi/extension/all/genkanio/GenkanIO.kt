package eu.kanade.tachiyomi.extension.all.genkanio

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

open class GenkanIO() : ParsedHttpSource() {
    override val lang = "all"
    final override val name = "Genkan.io"
    final override val baseUrl = "https://genkan.io"
    final override val supportsLatest = false

    // genkan.io defaults to listing manga alphabetically, and provides no configuration
    fun alphabeticalMangaRequest(page: Int): Request = GET("$baseUrl/manga?page=$page", headers)

    fun alphabeticalMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a").let {
            manga.url = it.attr("href").substringAfter(baseUrl)
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").attr("src")
        return manga
    }

    fun alphabeticalMangaSelector() = "ul[role=list] > li"
    fun alphabeticalMangaNextPageSelector() = "a[rel=next]"

    // popular
    override fun popularMangaRequest(page: Int) = alphabeticalMangaRequest(page)
    override fun popularMangaFromElement(element: Element) = alphabeticalMangaFromElement(element)
    override fun popularMangaSelector() = alphabeticalMangaSelector()
    override fun popularMangaNextPageSelector() = alphabeticalMangaNextPageSelector()

    // latest

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Not used")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Not used")

    // search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                // Genkan.io redirects if the search query contains characters it deems "illegal" (e.g: '-')
                // Return no responses if any redirects occurred
                if (response.priorResponse != null)
                    MangasPage(emptyList(), false)
                else
                    searchMangaParse(response)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, @Suppress("UNUSED_PARAMETER") filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", "$page")
            .addQueryParameter("search", query)
        return GET("$url")
    }

    override fun searchMangaFromElement(element: Element) = alphabeticalMangaFromElement(element)
    override fun searchMangaSelector() = alphabeticalMangaSelector()
    override fun searchMangaNextPageSelector() = alphabeticalMangaNextPageSelector()

    // chapter list (is paginated),
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")
    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException("Not used")
    data class ChapterPage(val chapters: List<SChapter>, val hasnext: Boolean)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            // Returns an observable which emits the list of chapters found on a page,
            // for every page starting from specified page
            fun getAllPagesFrom(page: Int): Observable<List<SChapter>> =
                client.newCall(chapterListRequest(manga, page))
                    .asObservableSuccess()
                    .concatMap { response ->
                        val cp = chapterPageParse(response)
                        if (cp.hasnext)
                            Observable.just(cp.chapters).concatWith(getAllPagesFrom(page + 1))
                        else
                            Observable.just(cp.chapters)
                    }
            getAllPagesFrom(1).reduce(List<SChapter>::plus)
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
        }
    }

    private fun chapterPageParse(response: Response): ChapterPage {
        val document = response.asJsoup()

        val mangas = document.select(chapterListSelector()).map { element ->
            chapterFromElement(element)
        }

        val hasNextPage = chapterListNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return ChapterPage(mangas, hasNextPage)
    }

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = "$baseUrl${manga.url}".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", "$page")
        return GET("$url", headers)
    }

    override fun chapterFromElement(element: Element): SChapter = element.children().let { tablerow ->
        val isTitleBlank: (String) -> Boolean = { s: String -> s == "-" || s.isBlank() }
        val (numElem, nameElem, languageElem, groupElem, viewsElem) = tablerow
        val (releasedElem, urlElem) = Pair(tablerow[5], tablerow[6])
        SChapter.create().apply {
            name = if (isTitleBlank(nameElem.text())) "Chapter ${numElem.text()}" else "Ch. ${numElem.text()}: ${nameElem.text()}"
            url = urlElem.select("a").attr("href").substringAfter(baseUrl)
            date_upload = parseRelativeDate(releasedElem.text()) ?: 0
            scanlator = "${groupElem.text()} - ${languageElem.text()}"
            chapter_number = numElem.text().toFloat()
        }
    }

    override fun chapterListSelector() = "tbody > tr"
    fun chapterListNextPageSelector() = "a[rel=next]"

    // manga

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst("section > div > img").attr("src")
        status = SManga.UNKNOWN // unreported
        artist = null // unreported
        author = null // unreported
        description = document.selectFirst("h2").nextElementSibling().text()
            .plus("\n\n\n")
            // Add additional details from info table
            .plus(
                document.select("ul.mt-1").joinToString("\n") {
                    "${it.previousElementSibling().text()}: ${it.text()}"
                }
            )
    }

    private fun parseRelativeDate(date: String): Long? {
        val trimmedDate = date.substringBefore(" ago").removeSuffix("s").split(" ")

        val calendar = Calendar.getInstance()
        when (trimmedDate[1]) {
            "year" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
            "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
            "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
            "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            "hour" -> calendar.apply { add(Calendar.HOUR_OF_DAY, -trimmedDate[0].toInt()) }
            "minute" -> calendar.apply { add(Calendar.MINUTE, -trimmedDate[0].toInt()) }
            "second" -> calendar.apply { add(Calendar.SECOND, 0) }
        }

        return calendar.timeInMillis
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> = document.select("main > div > img").mapIndexed { index, img ->
        Page(index, "", img.attr("src"))
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
