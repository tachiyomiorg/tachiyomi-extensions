package eu.kanade.tachiyomi.extension.pt.baixarhentai

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document

@Nsfw
class BaixarHentai : FoolSlide("Baixar Hentai", "https://leitura.baixarhentai.net", "pt-BR") {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 8908032188831949972

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.select("h1.title").text()
            thumbnail_url = getDetailsThumbnail(document, "div.title a")
        }
    }
}
