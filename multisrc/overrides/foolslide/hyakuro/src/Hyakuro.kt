package eu.kanade.tachiyomi.extension.en.hyakuro

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

class Hyakuro : FoolSlide("Hyakuro", "https://hyakuro.com/reader/", "en") {
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            description = document.select("$mangaDetailsInfoSelector li:has(b:contains(description))")
                .first()?.ownText()?.substringAfter(":")
            thumbnail_url = getDetailsThumbnail(document)
        }
    }
}
