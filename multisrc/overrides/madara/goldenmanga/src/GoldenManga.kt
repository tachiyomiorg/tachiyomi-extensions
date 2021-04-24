package eu.kanade.tachiyomi.extension.ar.goldenmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class GoldenManga : Madara("موقع لترجمة المانجا", "https://golden-manga.com", "ar", SimpleDateFormat("yyyy-MM-dd", Locale.US)) {
    override fun searchMangaSelector() = "div.c-image-hover a"
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            title = element.attr("title")
            thumbnail_url = element.select("img").firstOrNull()?.let { imageFromElement(it) }
        }
    }

    override fun chapterListSelector() = "div.main a"
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select("h6:first-of-type").text()
            date_upload = parseChapterDate(element.select("h6:last-of-type").firstOrNull()?.ownText())
        }
    }
}
