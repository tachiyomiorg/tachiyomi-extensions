package eu.kanade.tachiyomi.extension.all.lhreader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

class LHReaderFactory : SourceFactory {
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
        Comicastle()
    )
}

class LHTranslation : LHReader("LHTranslation", "https://lhtranslation.net", "en")
class MangaHato : LHReader("Hato", "https://mangahato.com", "ja")
class ManhwaScan : LHReader("ManhwaScan", "https://manhwascan.com", "en")
class MangaTiki : LHReader("MangaTiki", "https://mangatiki.com", "ja")
class MangaBone : LHReader("MangaBone", "https://mangabone.com", "en")
class YoloManga : LHReader("Yolo Manga", "https://yolomanga.ca", "es") {
    override fun chapterListSelector() = "div#tab-chapper ~ div#tab-chapper table tr"
}
class MangaLeer : LHReader("MangaLeer", "https://mangaleer.com", "es") {
    override val dateValueIndex = 1
    override val dateWhenIndex = 2
}
class AiLoveManga : LHReader("AiLoveManga", "https://ailovemanga.com", "vi") {
    override val requestPath = "danh-sach-truyen.html"
    override fun chapterListSelector() = "div#tab-chapper table tr"
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.container:has(img)").first()

        manga.author = infoElement.select("a.btn-info").first().text()
        manga.artist = infoElement.select("a.btn-info + a").text()
        manga.genre = infoElement.select("a.btn-danger").text().replace(" ", ", ")
        // TODO figure out why status isn't being set
        manga.status = parseStatus(infoElement.select("a.btn-success").text())
        manga.description = document.select("div.col-sm-8 p").text().trim()
        manga.thumbnail_url = infoElement.select("img").attr("abs:src")

        return manga
    }
}
class ReadComicOnlineOrg : LHReader("ReadComicOnline.org", "https://readcomiconline.org", "en") {
    override val requestPath = "comic-list.html"
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#divImage > select:first-of-type option").forEachIndexed{ i, imgPage ->
            pages.add(Page(i, imgPage.attr("value"), ""))
        }
        return pages.dropLast(1) // last page is a comments page
    }
    override fun imageUrlRequest(page: Page): Request = GET(baseUrl + page.url, headers)
    override fun imageUrlParse(document: Document): String = document.select("img.chapter-img").attr("abs:src").trim()
    override fun getGenreList() = getComicsGenreList()
}
class MangaWeek : LHReader("MangaWeek", "https://mangaweek.com", "en")
class HanaScan : LHReader("HanaScan (RawQQ)", "http://rawqq.com", "ja") {
    override fun popularMangaNextPageSelector() = "div.col-md-8 button"
}
class RawLH : LHReader("RawLH", "https://lhscan.net", "ja") {
    override fun popularMangaNextPageSelector() = "div.col-md-8 button"
}
class Manhwa18 : LHReader("Manhwa18", "https://manhwa18.com", "en") {
    override fun getGenreList() = getAdultGenreList()
}
class TruyenTranhLH : LHReader("TruyenTranhLH", "https://truyentranhlh.net", "vi") {
    override val requestPath = "danh-sach-truyen.html"
}
class EighteenLHPlus : LHReader("18LHPlus", "https://18lhplus.com", "en") {
    override fun getGenreList() = getAdultGenreList()
}
class MangaTR : LHReader("Manga-TR", "https://manga-tr.com", "tr") {
    override fun popularMangaNextPageSelector() = "div.btn-group:not(div.btn-block) button.btn-info"
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div#tab1").first()

        manga.author = infoElement.select("table + table tr + tr td a").first()?.text()
        manga.artist = infoElement.select("table + table tr + tr td + td a").first()?.text()
        manga.genre = infoElement.select("div#tab1 table + table tr + tr td + td + td a").text().replace(" ", ", ")
        manga.status = parseStatus(infoElement.select("div#tab1 table tr + tr td a").first().text())
        manga.description = infoElement.select("div.well").text().trim()
        manga.thumbnail_url = document.select("img.thumbnail").attr("abs:src")

        return manga
    }
    override fun chapterListSelector() = "tr.table-bordered"
    override val chapterUrlSelector = "td[align=left] > a"
    override val timeElementSelector = "td[align=right]"
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
            document.select(chapterListSelector()).map{ chapters.add(chapterFromElement(it)) }
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

        document.select("div.chapter-content select:first-of-type option").forEachIndexed{ i, imgPage ->
            pages.add(Page(i, "$baseUrl/${imgPage.attr("value")}", ""))
        }
        return pages.dropLast(1) // last page is a comments page
    }
    override fun imageUrlParse(document: Document): String = document.select("img.chapter-img").attr("abs:src").trim()
}
class Comicastle : LHReader("Comicastle", "https://www.comicastle.org", "en") {
    override val requestPath = "comic-dir"
    override fun popularMangaNextPageSelector() = "li:contains(»)"
    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()

        document.select(popularMangaSelector()).map{ mangas.add(popularMangaFromElement(it)) }

        return MangasPage(mangas, document.select(popularMangaNextPageSelector()).isNotEmpty())
    }
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.col-md-9").first()

        manga.author = infoElement.select("tr + tr td a").first().text()
        manga.artist = infoElement.select("tr + tr td + td a").text()
        manga.genre = infoElement.select("tr + tr td + td + td a").text().replace(" ", ", ")
        manga.description = infoElement.select("p").text().trim()
        manga.thumbnail_url = document.select("img.manga-cover").attr("abs:src")

        return manga
    }
    override fun chapterListSelector() = "div.col-md-9 table:last-of-type tr"
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div.text-center select option").forEachIndexed{ i, imgPage ->
            pages.add(Page(i, imgPage.attr("value"), ""))
        }
        return pages
    }
    override fun imageUrlParse(document: Document): String = document.select("img.chapter-img").attr("abs:src").trim()
    override fun getGenreList() = getComicsGenreList()
}





