package eu.kanade.tachiyomi.extension.ja.kisslove

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.Page
import org.jsoup.nodes.Document

class KissLove : FMReader("KissLove", "https://kissaway.net", "ja") {
    override fun pageListParse(document: Document): List<Page> = base64PageListParse(document)
}