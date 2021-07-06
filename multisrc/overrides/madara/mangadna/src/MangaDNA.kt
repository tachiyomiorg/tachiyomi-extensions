package eu.kanade.tachiyomi.extension.en.mangadna

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDNA : Madara(
  "MangaDNA",
  "https://mangadna.com",
  "en",
  dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)
) {

    override fun popularMangaSelector() = "div.manga-item"
    override val popularMangaUrlSelector = "div > h3 > a"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page?orderby=trending")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/page/$page?orderby=latest")
    }


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
