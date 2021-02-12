package eu.kanade.tachiyomi.extension.en.manhuaes

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaES : WPComics("Manhua ES", "https://manhuaes.com", "en", SimpleDateFormat("HH:mm - dd/MM/yyyy Z", Locale.US), "+0700") {
    override val popularPath = "category-comics/manga"
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/$popularPath" + if (page > 1) "/page/$page" else "", headers)
    }
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl + if (page > 1) "/page/$page" else "", headers)
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/page/$page/?s=$query&post_type=comics")
    }
    override fun popularMangaNextPageSelector() = ".pagination li:last-child:not(.active)"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.overlay a:has(h2)").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("img").firstOrNull()?.attr("abs:src")
        }
    }
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("article#item-detail").let { info ->
                author = info.select("li.author p.col-xs-8").text()
                status = info.select("li.status p.col-xs-8").text().toStatus()
                genre = info.select(".tags-genre a").joinToString { it.text() }
                thumbnail_url = imageOrNull(info.select("div.col-image img").first())

                val h3 = info.select(".detail-content h3").text()
                val strong = info.select(".detail-content strong").text()
                val showMoreFake = info.select(".detail-content .content-readmore").text()
                val showMore = info.select(".detail-content .morelink").text()
                val rawDesc = info.select("div.detail-content").text()

                if (showMoreFake == null || showMoreFake == "") {
                    description = rawDesc.substringAfter(h3).substringAfter(strong).substringBefore(showMore)
                } else {
                    description = rawDesc.substringAfter(h3).substringAfter(strong).substringBefore(showMoreFake)
                }
            }
        }
    }
    override val pageListSelector = "div.chapter-detail ul img, div.chapter-detail div:not(.container) > img, div.chapter-detail p > img"
}
