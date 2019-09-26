package eu.kanade.tachiyomi.extension.all.lhreader

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document

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
        ReadComicOnline(),
        MangaWeek(),
        HanaScan(),
        RawLH(),
        ManhwaEighteen(),
        TruyenTranhLH(),
        EighteenLHPlus(),
        MangaTR()
    )
}

//TODO check/change filters for some sources

class LHTranslation : LHReader("LHTranslation", "https://lhtranslation.net", "en")
class MangaHato : LHReader("Hato", "https://mangahato.com", "ja")
class ManhwaScan : LHReader("ManhwaScan", "https://manhwascan.com", "en")
class MangaTiki : LHReader("MangaTiki", "https://mangatiki.com", "ja")
class MangaBone : LHReader("MangaBone", "https://mangabone.com", "en")
class YoloManga : LHReader("Yolo Manga", "https://yolomanga.ca", "es") {
    override fun chapterListSelector() = "div#tab-chapper ~ div#tab-chapper table tr"
}
class MangaLeer : LHReader("MangaLeer", "https://mangaleer.com", "es")
class AiLoveManga : LHReader("AiLoveManga", "https://ailovemanga.com", "vi") {
    override val requestPath = "danh-sach-truyen.html"
    override fun chapterListSelector() = "div#tab-chapper table tr"
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        val infoElement = document.select("div.container:has(img)").first()

        manga.author = infoElement.select("a.btn-info").first().text()
        manga.artist = infoElement.select("a.btn-info + a").text()
        manga.genre = infoElement.select("a.btn-danger").text().replace(" ", ", ")
        manga.status = parseStatus(infoElement.select("a.btn-warning").text())
        manga.description = document.select("div.col-sm-8 p").text().trim()
        manga.thumbnail_url = infoElement.select("img").attr("abs:src")

        return manga
    }
}
class ReadComicOnline : LHReader("ReadComicOnline.org", "https://readcomiconline.org", "en") {
    override val requestPath = "comic-list.html"
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("div#divImage select").first().select("option").forEachIndexed{ i, imgPage ->
            pages.add(Page(i, imgPage.attr("value"), ""))
        }
        return pages
    }
    override fun imageUrlRequest(page: Page): Request = GET(baseUrl + page.url, headers)
    override fun imageUrlParse(document: Document): String = document.select("img.chapter-img").attr("abs:src").trim()
}
class MangaWeek : LHReader("MangaWeek", "https://mangaweek.com", "en")
class HanaScan : LHReader("HanaScan (RawQQ)", "https://hanascan.com", "ja")
class RawLH : LHReader("RawLH", "https://lhscan.net", "ja") {
    override fun popularMangaNextPageSelector() = "div.col-md-8 button"
}
class ManhwaEighteen : LHReader("Manhwa18", "https://manhwa18.com", "en")
class TruyenTranhLH : LHReader("TruyenTranhLH", "https://truyentranhlh.net", "vi") {
    override val requestPath = "danh-sach-truyen.html"
}
class EighteenLHPlus : LHReader("18LHPlus", "https://18lhplus.com", "en")
class MangaTR : LHReader("Manga-TR", "https://manga-tr.com", "tr") {
    override fun popularMangaNextPageSelector() = "div.btn-group:not(div.btn-block) button.btn-info"
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        //TODO more info
        manga.description = document.select("div.well:has(h3)").text().trim()
        manga.thumbnail_url = document.select("div.col-sm-4 > img.thumbnail").attr("abs:src")

        return manga
    }
    override fun chapterListSelector() = "div.col-sm-8 table.table tbody tr.table-bordered"
    override val timeElementSelector = "[align=right]"
    //TODO chapters, chapter list pagination, blacklist novels
}





