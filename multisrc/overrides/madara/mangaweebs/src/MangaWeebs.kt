package eu.kanade.tachiyomi.extension.en.mangaweebs

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class MangaWeebs : Madara("Manga Weebs", "https://mangaweebs.in", "en") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector).mapIndexed { index, element ->
            Page(
                index,
                "",
                element.select("img.wp-manga-chapter-img").attr("src")
            )
        }
    }
}
