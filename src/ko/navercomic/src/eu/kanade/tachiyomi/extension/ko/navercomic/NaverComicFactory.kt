package eu.kanade.tachiyomi.extension.ko.navercomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Element


class NaverComicFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
            NaverWebtoon()
    )
}

class NaverWebtoon : NaverComicBase("webtoon") {
    override val name = "Naver Webtoon"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$mType/weekday.nhn")
    override fun popularMangaSelector() = ".list_area.daily_all .col ul > li"
    override fun popularMangaNextPageSelector() = null
    override fun popularMangaFromElement(element: Element): SManga {
        val thumb = element.select("div.thumb img").first().attr("src")
        val title = element.select("a.title").first()

        val manga = SManga.create()
        manga.url = title.attr("href").substringBefore("&week")
        manga.title = title.text().trim()
        manga.thumbnail_url = thumb
        return manga
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$mType/weekday.nhn?order=Update")
    override fun latestUpdatesSelector() = ".list_area.daily_all .col.col_selected ul > li"
    override fun latestUpdatesNextPageSelector() = null
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
}
