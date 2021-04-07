package eu.kanade.tachiyomi.extension.pt.toonei

import eu.kanade.tachiyomi.multisrc.mangasproject.MangasProject
import org.jsoup.nodes.Document

class Toonei : MangasProject("Toonei", "https://toonei.com", "pt-br") {

    override fun getReaderToken(document: Document): String? {
        return document.select("script:containsData(window.PAGES_KEY)").firstOrNull()
            ?.data()
            ?.substringAfter("\"")
            ?.substringBefore("\";")
    }
}
