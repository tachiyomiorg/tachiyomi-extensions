package eu.kanade.tachiyomi.extension.id.kiryuuwpms

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class KiryuuWPMS : WPMangaStream("Kiryuu (WP Manga Stream)", "https://kiryuu.co", "id") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img").map { it.attr("abs:src") }
            .filterNot { it.substringAfterLast("/").contains(Regex("""(filerun|photothumb\.db)""")) }
            .mapIndexed { i, image -> Page(i, "", image) }
    }
}
