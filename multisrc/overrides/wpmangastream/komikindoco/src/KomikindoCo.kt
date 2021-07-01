package eu.kanade.tachiyomi.extension.id.komikindoco

import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.multisrc.wpmangastream.WPMangaStream
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class KomikindoCo : WPMangaStream("KomikIndo.co", "https://komikindo.co", "id", SimpleDateFormat("dd-MM-yyyy", Locale.US)) {
    // Formerly "Komikindo.co"
    override val id = 734619124437406170

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override val hasProjectPage = true

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select(".lchx > a, span.leftoff a, div.eph-num > a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = if (urlElement.select("span.chapternum").isNotEmpty()) urlElement.select("span.chapternum").text() else urlElement.text()
        chapter.date_upload = element.select("span .waktu").firstOrNull()?.text()?.let { parseChapterDate(it) } ?: 0
        return chapter
    }

    private val json: Json by injectLazy()

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        document.select(pageSelector)
            .filterNot { it.attr("src").isNullOrEmpty() }
            .mapIndexed { i, img -> pages.add(Page(i, "", img.attr("abs:src"))) }

        // Some sites like mangakita now load pages via javascript
        if (pages.isNotEmpty()) { return pages }

        val docString = document.toString()
        val imageListRegex = Regex("\\\"images.*?:.*?(\\[.*?\\])")
        val imageListJson = imageListRegex.find(docString)!!.destructured.toList()[0]

        val imageList = json.parseToJsonElement(imageListJson).jsonArray

        pages += imageList.mapIndexed { i, jsonEl ->
            Page(i, "", jsonEl.jsonPrimitive.content)
        }

        return pages
    }
}
