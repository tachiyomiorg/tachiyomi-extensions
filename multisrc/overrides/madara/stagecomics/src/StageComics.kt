package eu.kanade.tachiyomi.extension.pt.stagecomics

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class StageComics : Madara("StageComics", "https://stagecomics.com", "pt-BR", SimpleDateFormat("MMMM dd, yyyy", Locale("pt"))) {
    override fun chapterFromElement(element: Element): SChapter {
        val parsedChapter = super.chapterFromElement(element)

        parsedChapter.date_upload = element.select("img").firstOrNull()?.attr("alt")
            ?.let { parseChapterDate(it) }
            ?: parseChapterDate(element.select("span.chapter-release-date i").firstOrNull()?.text())

        return parsedChapter
    }
}
