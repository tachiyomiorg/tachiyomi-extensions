package eu.kanade.tachiyomi.extension.en.lynxscans

import eu.kanade.tachiyomi.multisrc.genkan.Genkan
import org.jsoup.nodes.Document
import eu.kanade.tachiyomi.source.model.Page

class LynxScans : Genkan("Lynx Scans", "https://lynxscans.com", "en") {
    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        
        val allImages = document.select("div#pages-container + script").first().data()
            .substringAfter("[").substringBefore("];")
            .replace(Regex("""["\\]"""), "")
            .split(",/")

        for (i in allImages.indices) {
            pages.add(Page(i, "", if (i == 0) allImages[i] else "/" + allImages[i]))
        }

        return pages
    }
}
