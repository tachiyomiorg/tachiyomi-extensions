package eu.kanade.tachiyomi.extension.pt.hqdragon

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class HQDragon : ParsedHttpSource() {

    override val name = "HQ Dragon"

    override val baseUrl = "https://hqdragon.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    // Top 10
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaSelector() = "ol.list-unstyled.mb-0 li a"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text()

        return manga
    }

    override fun popularMangaNextPageSelector() = "Not needed"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val body = FormBody.Builder()
            .add("pagina", page.toString())
            .build()

        val headers = headersBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()

        return POST("$baseUrl/assets/php/index_paginar.php", headers, body)
    }

    override fun latestUpdatesSelector() = "body a:has(img)"

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()

        document.select(latestUpdatesSelector())
            .forEach { mangas.add(latestUpdatesFromElements(it, it.nextElementSibling())) }

        return MangasPage(mangas, document.select("a[href\$=5693-x-force-2018]").isEmpty())
    }

    private fun latestUpdatesFromElements(imageElement: Element, titleElement: Element): SManga {
        val manga = SManga.create()

        manga.thumbnail_url = imageElement.select("img").attr("src")
        manga.setUrlWithoutDomain(titleElement.attr("href"))
        manga.title = titleElement.ownText()

        return manga
    }

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesNextPageSelector() = "not using"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/pesquisa?pesquisa=$query", headers)
    }

    override fun searchMangaSelector() = "div.col-6"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        element.select("a + a").let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
        }
        manga.thumbnail_url = element.select("img").attr("abs:src")

        return manga
    }

    override fun searchMangaNextPageSelector() = "Not needed"

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.blog-post div.row").first()

        val manga = SManga.create()
        manga.title = infoElement.select("h3").first().text()
        manga.author = infoElement.select("p:contains(editora)").first().ownText()
        val status = infoElement.select("p:contains(status) span").text()
        manga.status = parseStatus(status)
        manga.description = infoElement.select("p:contains(sinopse)").first().ownText()
        manga.thumbnail_url = infoElement.select("img").attr("abs:src")

        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ativo") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "tr a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        chapter.setUrlWithoutDomain(element.attr("href"))
        chapter.name = element.text()

        return chapter
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("img.img-responsive").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    override fun getFilterList() = FilterList()
}
