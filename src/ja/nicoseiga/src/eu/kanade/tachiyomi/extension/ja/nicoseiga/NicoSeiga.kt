package eu.kanade.tachiyomi.extension.ja.nicoseiga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.*

class NicoSeiga : ParsedHttpSource() {
    override val name = "NicoSeiga"

    override val baseUrl = "https://seiga.nicovideo.jp"

    override val lang = "ja"

    override val supportsLatest = true

    override fun popularMangaNextPageSelector() = "a[rel=\"next\"]"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga/list?sort=manga_updated")

    override fun latestUpdatesSelector() = "li.mg_item.item .item_container"

    override fun latestMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select(".mg_body .title a").first().let {
            manga.title = it.text()
            manga.url = it.attr("href")
        }
        element.select(".mg_body .description font font").first().let {
            manga.description = it.text()
        }
        element.select(".comic_icon img").first().let {
            manga.thumbnail_url = it.attr("src")
        }
        manga
    }
    
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga/ranking/")

    override fun popularMangaSelector() = "#mg_ranking_table .mg_ranking_box"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a.mg_txt").first().let {
            manga.url = it.attr("href")
            manga.title = it.text()
        }
        element.select(".mg_thumb_img img").first().let {
            manga.thumbnail_url = it.attr("src")
        }
        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.description = document.select(".description_text").first()?.text()
        manga.title = document.select(".main_title h1").first()?.text()
        manga.author = document.select(".main_title .author span").first()?.text()
        manga.thumbnail_url = document.select(".main_visual img").first()?.attr("src")
        manga
    }
    override fun chapterListSelector() = "li.episode_item"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val urlElement = element.select(".title a").first()

        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(".pages img[data-original]").forEach {
            val url = it.attr("data-original")
            if (url != "") {
                pages.add(Page(pages.size, "", url))
            }
        }
        return pages
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}