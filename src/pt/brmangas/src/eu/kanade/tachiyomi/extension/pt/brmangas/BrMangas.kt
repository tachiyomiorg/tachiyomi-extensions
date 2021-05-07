package eu.kanade.tachiyomi.extension.pt.brmangas

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

@Nsfw
class BrMangas : ParsedHttpSource() {

    override val name = "BR Mangás"

    override val baseUrl = "https://brmangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(RateLimitInterceptor(1, 1, TimeUnit.SECONDS))
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Accept", ACCEPT)
        .add("Accept-Language", ACCEPT_LANGUAGE)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request {
        val listPath = if (page == 1) "" else "page/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/lista-de-mangas/$listPath")
            .build()

        val pageStr = if (page != 1) "page/$page" else ""
        return GET("$baseUrl/lista-de-mangas/$pageStr", newHeaders)
    }

    override fun popularMangaSelector(): String = "div.listagem.row div.item a[title]"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val thumbnailEl = element.select("img").first()!!

        title = element.select("h2.titulo").first()!!.text()
        thumbnail_url = when {
            thumbnailEl.hasAttr("original-src") -> thumbnailEl.attr("original-src")
            else -> thumbnailEl.attr("src")
        }
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun popularMangaNextPageSelector() = "div.navigation a.next"

    override fun latestUpdatesRequest(page: Int): Request {
        val listPath = if (page == 1) "" else "category/page/${page - 1}"
        val newHeaders = headersBuilder()
            .set("Referer", "$baseUrl/$listPath")
            .build()

        val pageStr = if (page != 1) "page/$page" else ""
        return GET("$baseUrl/category/mangas/$pageStr", newHeaders)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("s", query)

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div.serie-geral div.infoall").first()!!

        title = document.select("title").first().text().substringBeforeLast(" - ")
        genre = infoElement.select("a.category.tag").joinToString { it.text() }
        description = document.select("div.manga_sinopse ~ p").text().trim()
        thumbnail_url = infoElement.select("div.serie-capa img").first()!!.attr("src")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun chapterListSelector() = "ul.capitulos li.row a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.text()
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("script:containsData(imageArray)").first()!!
            .data()
            .substringAfter("[")
            .substringBefore("]")
            .split(",")
            .mapIndexed { i, imageUrl ->
                val fixedImageUrl = imageUrl
                    .replace("\\\"", "")
                    .replace("\\/", "/")
                Page(i, document.location(), fixedImageUrl)
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

    companion object {
        private const val ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9," +
            "image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9"
        private const val ACCEPT_IMAGE = "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8"
        private const val ACCEPT_LANGUAGE = "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7,es;q=0.6,gl;q=0.5"
    }
}
