package eu.kanade.tachiyomi.extension.id.matakomik

import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class Matakomik : WPMangaStream("Matakomik", "https://matakomik.com", "id") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea a").mapIndexed { i, a ->
            Page(i, "", a.attr("abs:href"))
        }
    }
}
