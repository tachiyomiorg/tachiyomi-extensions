package eu.kanade.tachiyomi.extension.pt.hqdragon

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.concurrent.TimeUnit

class HQDragon : ParsedHttpSource() {

    override val name = "HQ Dragon"

    override val baseUrl = "https://hqdragon.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 2, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", "$baseUrl/")

    // Popular

    // Top 10
    override fun popularMangaRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val results = super.popularMangaParse(response)

        if (results.mangas.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun popularMangaSelector() = "h4:contains(Top 10) + ol.mb-0 li a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.text()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return super.fetchLatestUpdates(page)
            .map { results -> results.copy(hasNextPage = page < 5) }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val formBody = FormBody.Builder()
            .add("pagina", page.toString())
            .build()

        val headers = headersBuilder()
            .add("Content-Length", formBody.contentLength().toString())
            .add("Content-Type", formBody.contentType().toString())
            .add("Origin", baseUrl)
            .add("X-Requested-With", "XMLHttpRequest")
            .set("Accept", "*/*")
            .build()

        return POST("$baseUrl/assets/php/index_paginar.php", headers, formBody)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val results = super.latestUpdatesParse(response)

        if (results.mangas.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun latestUpdatesSelector() = "a:has(img)"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val image = element.select("img").first()

        title = image.attr("alt")
        thumbnail_url = image.attr("abs:src")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/pesquisa".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("titulo", query)

        return GET(url.toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val results = super.searchMangaParse(response)

        if (results.mangas.isEmpty()) {
            throw Exception(BLOCK_MESSAGE)
        }

        return results
    }

    override fun searchMangaSelector() = "div.col-sm-6.col-md-3:has(img.img-thumbnail)"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.select("a + a").first()

        title = link.text()
        thumbnail_url = element.select("img").first().attr("abs:src")
        setUrlWithoutDomain(link.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    // Manga summary page

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.blog-post div.row").firstOrNull()
            ?: throw Exception(BLOCK_MESSAGE)

        title = infoElement.select("h3").first().text()
        author = infoElement.select("p:contains(Editora:)").first().textWithoutLabel()
        status = infoElement.select("p:contains(Status:) span").first().text().toStatus()
        description = infoElement.select("p:contains(Sinopse:)").first().ownText()
        thumbnail_url = infoElement.select("div.col-md-4 .img-fluid").first().attr("src")
    }

    // Chapters

    override fun chapterListSelector() = "table.table tr a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text().replace("Ler ", "")
        setUrlWithoutDomain(element.attr("href"))
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.img-responsive.img-manga")
            .filter { it.attr("src").contains("/leitor/") }
            .mapIndexed { i, element ->
                Page(i, document.location(), element.absUrl("src"))
            }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", ACCEPT_IMAGE)
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun Element.textWithoutLabel(): String = text()!!.substringAfter(":").trim()

    private fun String.toStatus(): Int = when {
        contains("Ativo") -> SManga.ONGOING
        contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.128 Safari/537.36"

        private const val BLOCK_MESSAGE = "O site está bloqueando o Tachiyomi. " +
            "Migre para outra fonte caso o problema persistir."
    }
}
