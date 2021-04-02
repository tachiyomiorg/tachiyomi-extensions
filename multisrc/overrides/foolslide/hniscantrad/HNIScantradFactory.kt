package eu.kanade.tachiyomi.extension.all.hniscantrad

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class HNIScantradFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        HNIScantradFR(),
        HNIScantradEN(),
    )
}
class HNIScantradFR : FoolSlide("HNI-Scantrad", "https://hni-scantrad.com", "fr", "/lel")
class HNIScantradEN : FoolSlide("HNI-Scantrad", "https://hni-scantrad.com", "en", "/eng/lel") {
    override val supportsLatest = false
    override fun popularMangaRequest(page: Int) = GET(baseUrl + urlModifier, headers)
    override fun popularMangaSelector() = "div.listed"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a:has(h3)").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl$urlModifier/?manga=${query.replace(" ", "+")}")
    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun chapterListSelector() = "div.theList > a"
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.select("div.chapter b").text()
            setUrlWithoutDomain(element.attr("abs:href"))
        }
    }
    override fun pageListParse(response: Response): List<Page> {
        return Regex("""imageArray\[\d+]='(.*)'""").findAll(response.body()!!.string()).toList().mapIndexed { i, mr ->
            Page(i, "", "$baseUrl$urlModifier/${mr.groupValues[1]}")
        }
    }
}

