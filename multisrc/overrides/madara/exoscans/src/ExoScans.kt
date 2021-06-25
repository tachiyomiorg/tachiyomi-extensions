package eu.kanade.tachiyomi.extension.en.exoscans

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class ExoScans : Madara("Exo Scans", "https://exoscans.club", "en") {

    override val pageListParseSelector = "div.read-content img"

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector).mapIndexed { index, element ->
            Page(
                index,
                document.location(),
                element?.let {
                    it.absUrl(if (it.hasAttr("data-src")) "data-src" else "src")
                }
            )
        }
    }
}
