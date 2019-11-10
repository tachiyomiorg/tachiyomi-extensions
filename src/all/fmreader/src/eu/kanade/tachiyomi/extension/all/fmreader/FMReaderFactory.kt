package eu.kanade.tachiyomi.extension.all.fmreader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class FMReaderFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        LHTranslation(),
        MangaHato(),
        ManhwaScan(),
        MangaTiki(),
        MangaBone(),
        YoloManga(),
        MangaLeer(),
        AiLoveManga(),
        ReadComicOnlineOrg(),
        MangaWeek(),
        HanaScan(),
        RawLH(),
        Manhwa18(),
        TruyenTranhLH(),
        EighteenLHPlus(),
        MangaTR(),
        Comicastle(),
        Manhwa18Net(),
        Manhwa18NetRaw()
    )
}

/** For future sources: when testing and popularMangaRequest() returns a Jsoup error instead of results
 *  most likely the fix is to override popularMangaNextPageSelector()   */

class LHTranslation : FMReader("LHTranslation", "https://lhtranslation.net", "en")

class MangaHato : FMReader("MangaHato", "https://mangahato.com", "ja")
class ManhwaScan : FMReader("ManhwaScan", "https://manhwascan.com", "en")
class MangaTiki : FMReader("MangaTiki", "https://mangatiki.com", "ja")
class MangaBone : FMReader("MangaBone", "https://mangabone.com", "en")
class YoloManga : FMReader("Yolo Manga", "https://yolomanga.ca", "es") {
    override fun chapterListSelector() = "div#tab-chapper ~ div#tab-chapper table tr"
}

class MangaLeer : FMReader("MangaLeer", "https://mangaleer.com", "es") {
    override val dateValueIndex = 1
    override val dateWordIndex = 2
}

class AiLoveManga : FMReader("AiLoveManga", "https://ailovemanga.com", "vi") {
    override val requestPath = "danh-sach-truyen.html"
    // TODO: could add a genre search (different URL paths for genres)
    override fun getFilterList() = FilterList()

    // I don't know why, but I have to override searchMangaRequest to make it work for this source
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/$requestPath?name=$query&page=$page")

    override fun chapterListSelector() = "div#tab-chapper table tr"
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.container:has(img)").first()

        manga.author = infoElement.select("a.btn-info").first().text()
        manga.artist = infoElement.select("a.btn-info + a").text()
        manga.genre = infoElement.select("a.btn-danger").joinToString { it.text() }
        manga.status = parseStatus(infoElement.select("a.btn-success").text().toLowerCase())
        manga.description = document.select("div.col-sm-8 p").text().trim()
        manga.thumbnail_url = infoElement.select("img").attr("abs:src")

        return manga
    }
}

class ReadComicOnlineOrg : FMReader("ReadComicOnline.org", "https://readcomiconline.org", "en") {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { requestIntercept(it) }
        .build()

    private fun requestIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        return if (response.headers("set-cookie").isNotEmpty()) {
            val body = FormBody.Builder()
                .add("dqh_firewall", "%2F")
                .build()
            val cookie = mutableListOf<String>()
            response.headers("set-cookie").map { cookie.add(it.substringBefore(" ")) }
            headers.newBuilder().add("Cookie", cookie.joinToString { " " }).build()
            client.newCall(POST(request.url().toString(), headers, body)).execute()
        } else {
            response
        }
    }

    override val requestPath = "comic-list.html"
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#divImage > select:first-of-type option").forEachIndexed { i, imgPage ->
            pages.add(Page(i, imgPage.attr("value"), ""))
        }
        return pages.dropLast(1) // last page is a comments page
    }

    override fun imageUrlRequest(page: Page): Request = GET(baseUrl + page.url, headers)
    override fun imageUrlParse(document: Document): String = document.select("img.chapter-img").attr("abs:src").trim()
    override fun getGenreList() = getComicsGenreList()
}

class MangaWeek : FMReader("MangaWeek", "https://mangaweek.com", "en")
class HanaScan : FMReader("HanaScan (RawQQ)", "http://rawqq.com", "ja") {
    override fun popularMangaNextPageSelector() = "div.col-md-8 button"
}

class RawLH : FMReader("RawLH", "https://lhscan.net", "ja") {
    override fun popularMangaNextPageSelector() = "div.col-md-8 button"
}

class Manhwa18 : FMReader("Manhwa18", "https://manhwa18.com", "en") {
    override fun getGenreList() = getAdultGenreList()
}

class TruyenTranhLH : FMReader("TruyenTranhLH", "https://truyentranhlh.net", "vi") {
    override val requestPath = "danh-sach-truyen.html"
}

class EighteenLHPlus : FMReader("18LHPlus", "https://18lhplus.com", "en") {
    override fun getGenreList() = getAdultGenreList()
}

class MangaTR : FMReader("Manga-TR", "https://manga-tr.com", "tr") {
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
        manga.status = parseStatus(infoElement.select("div#tab1 table tr + tr td a").first().text().toLowerCase())
        manga.description = infoElement.select("div.well").text().trim()
        manga.thumbnail_url = document.select("img.thumbnail").attr("abs:src")

        return manga
    }

    override fun chapterListSelector() = "tr.table-bordered"
    override val chapterUrlSelector = "td[align=left] > a"
    override val chapterTimeSelector = "td[align=right]"
    private val chapterListHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest").build()
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return if (manga.status != SManga.LICENSED) {
            val requestUrl = "$baseUrl/cek/fetch_pages_manga.php?manga_cek=${manga.url.substringAfter("manga-").substringBefore(".")}"
            client.newCall(GET(requestUrl, chapterListHeaders))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response, requestUrl)
                }
        } else {
            Observable.error(Exception("Licensed - No chapters to show"))
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
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.chapter-content select:first-of-type option").forEachIndexed { i, imgPage ->
            pages.add(Page(i, "$baseUrl/${imgPage.attr("value")}", ""))
        }
        return pages.dropLast(1) // last page is a comments page
    }

    override fun imageUrlParse(document: Document): String = document.select("img.chapter-img").attr("abs:src").trim()
}

class Comicastle : FMReader("Comicastle", "https://www.comicastle.org", "en") {
    override val requestPath = "comic-dir"
    // this source doesn't have the "page x of y" element
    override fun popularMangaNextPageSelector() = "li:contains(»)"

    override fun popularMangaParse(response: Response) = defaultMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/comic-dir?q=$query", headers)
    override fun searchMangaParse(response: Response): MangasPage = defaultMangaParse(response)
    override fun getFilterList() = FilterList()
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.col-md-9").first()

        manga.author = infoElement.select("tr + tr td a").first().text()
        manga.artist = infoElement.select("tr + tr td + td a").text()
        manga.genre = infoElement.select("tr + tr td + td + td").text()
        manga.description = infoElement.select("p").text().trim()
        manga.thumbnail_url = document.select("img.manga-cover").attr("abs:src")

        return manga
    }

    override fun chapterListSelector() = "div.col-md-9 table:last-of-type tr"
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.text-center select option").forEachIndexed { i, imgPage ->
            pages.add(Page(i, imgPage.attr("value"), ""))
        }
        return pages
    }

    override fun imageUrlParse(document: Document): String = document.select("img.chapter-img").attr("abs:src").trim()
    override fun getGenreList() = getComicsGenreList()
}

class Manhwa18Net : FMReader("Manhwa18.net", "https://manhwa18.net", "en") {
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/$requestPath?listType=pagination&page=$page&sort=views&sort_type=DESC&ungenre=raw", headers)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/$requestPath?listType=pagination&page=$page&sort=last_update&sort_type=DESC&ungenre=raw", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val noRawsUrl = super.searchMangaRequest(page, query, filters).url().newBuilder().addQueryParameter("ungenre", "raw").toString()
        return GET(noRawsUrl, headers)
    }

    override fun getGenreList() = getAdultGenreList()
}

class Manhwa18NetRaw : FMReader("Manhwa18.net Raw", "https://manhwa18.net", "ko") {
    override val requestPath = "manga-list-genre-raw.html"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val onlyRawsUrl = super.searchMangaRequest(page, query, filters).url().newBuilder().addQueryParameter("genre", "raw").toString()
        return GET(onlyRawsUrl, headers)
    }

    override fun getFilterList() = FilterList(super.getFilterList().filterNot { it == GenreList(getGenreList()) })
}
