package eu.kanade.tachiyomi.extension.all.wpcomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class WPComicsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        ManhuaPlus(),
        ManhuaES()
    )
}

private class ManhuaPlus : WPComics("Manhua Plus", "https://manhuaplus.com", "en")

private class ManhuaES : WPComics("Manhua ES", "https://manhuaes.com", "en", SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US), "+0700") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/category-comics/manga/")
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.image a").let {
                title = it.attr("title")
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("div.image img").attr("abs:src")
        }
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a.head").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override val pageListSelector = "div.chapter-detail img"
}
