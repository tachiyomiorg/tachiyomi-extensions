package eu.kanade.tachiyomi.extension.all.manhwa18cc

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Manhwa18CcFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Manhwa18CcEN(),
        Manhwa18CcKO(),
        Manhwa18CcALL(),
    )
}

class Manhwa18CcEN : Manhwa18Cc("Manhwa18.cc", "https://manhwa18.cc", "en") {
    override fun popularMangaSelector() = "div.manga-item:not(:contains(Raw))"
}
class Manhwa18CcKO : Manhwa18Cc("Manhwa18.cc", "https://manhwa18.cc", "ko") {
    override fun popularMangaSelector() = "div.manga-item:contains(Raw)"
}
class Manhwa18CcALL : Manhwa18Cc("Manhwa18.cc", "https://manhwa18.cc", "all")

abstract class Manhwa18Cc(
    override val name: String,
    override val baseUrl: String,
    override val lang: String
) : Madara(name, baseUrl, lang) {

    override fun popularMangaSelector() = "div.manga-item"
    override val popularMangaUrlSelector = "div.data > h3 > a"


    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/webtoons/$page?orderby=trending")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/webtoons/$page?orderby=latest")
    }

    override val mangaSubString = "webtoon"

    override fun chapterListSelector() = "li.wleft"

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
