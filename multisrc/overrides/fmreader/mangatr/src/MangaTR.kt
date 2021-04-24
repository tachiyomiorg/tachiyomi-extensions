package eu.kanade.tachiyomi.extension.tr.mangatr

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MangaTR : FMReader("Manga-TR", "https://manga-tr.com", "tr") {
    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 6.3; WOW64)")
    }
    override fun popularMangaNextPageSelector() = "div.btn-group:not(div.btn-block) button.btn-info"
    // TODO: genre search possible but a bit of a pain
    override fun getFilterList() = FilterList()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/arama.html?icerik=$query", headers)
    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = mutableListOf<SManga>()

        response.asJsoup().select("div.row a[data-toggle]")
                .filterNot { it.siblingElements().text().contains("Novel") }
                .map { mangas.add(searchMangaFromElement(it)) }

        return MangasPage(mangas, false)
    }

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("abs:href"))
        manga.title = element.text()

        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div#tab1").first()

        manga.author = infoElement.select("table + table tr + tr td a").first()?.text()
        manga.artist = infoElement.select("table + table tr + tr td + td a").first()?.text()
        manga.genre = infoElement.select("div#tab1 table + table tr + tr td + td + td").text()
        manga.status = parseStatus(infoElement.select("div#tab1 table tr + tr td a").first().text())
        manga.description = infoElement.select("div.well").text().trim()
        manga.thumbnail_url = document.select("img.thumbnail").attr("abs:src")

        return manga
    }

    override fun chapterListSelector() = "tr.table-bordered"
    override val chapterUrlSelector = "td[align=left] > a"
    override val chapterTimeSelector = "td[align=right]"
    private val chapterListHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest").build()
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val requestUrl = "$baseUrl/cek/fetch_pages_manga.php?manga_cek=${manga.url.substringAfter("manga-").substringBefore(".")}"
        return client.newCall(GET(requestUrl, chapterListHeaders))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response, requestUrl)
                }
    }

    private fun chapterListParse(response: Response, requestUrl: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var document = response.asJsoup()
        var moreChapters = true
        var nextPage = 2

        // chapters are paginated
        while (moreChapters) {
            document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            if (document.select("a[data-page=$nextPage]").isNotEmpty()) {
                val body = FormBody.Builder()
                        .add("page", nextPage.toString())
                        .build()
                document = client.newCall(POST(requestUrl, chapterListHeaders, body)).execute().asJsoup()
                nextPage++
            } else {
                moreChapters = false
            }
        }
        return chapters
    }

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/${chapter.url.substringAfter("cek/")}", headers)
}
