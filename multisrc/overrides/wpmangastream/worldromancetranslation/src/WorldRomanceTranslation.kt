package eu.kanade.tachiyomi.extension.id.worldromancetranslation

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class WorldRomanceTranslation : WPMangaStream("World Romance Translation", "https://wrt.my.id/", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {
    override val projectPageString = "/project-wrt"

    override val hasProjectPage = true

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".lchx > a, span.leftoff a, div.eph-num > a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = "Chapter " + chapter.url.substringAfterLast("chapter-")
            .replace("/", "").replace("-bahasa-indonesia", "").replace("-", ".")
        chapter.date_upload = element.select("span.rightoff, time, span.chapterdate").firstOrNull()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }
}
