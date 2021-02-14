package eu.kanade.tachiyomi.extension.id.kiryuu

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class Kiryuu : WPMangaStream("Kiryuu", "https://kiryuu.co", "id") {
    // Formerly "Kiryuu (WP Manga Stream)"
    override val id = 3639673976007021338

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img").map { it.attr("abs:src") }
            .filterNot { it.substringAfterLast("/").contains(Regex("""(filerun|photothumb\.db)""")) }
            .mapIndexed { i, image -> Page(i, "", image) }
    }
}
