package eu.kanade.tachiyomi.extension.fr.japscan

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import java.io.ByteArrayOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.apache.commons.lang3.StringUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class Japscan : ParsedHttpSource() {

    override val id: Long = 11

    override val name = "Japscan"

    override val baseUrl = "https://www.japscan.co"

    override val lang = "fr"

    override val supportsLatest = true

    @SuppressLint("SetJavaScriptEnabled")
    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val indicator = "&wvsc"
        val cleanupjs = "var db=document.body,chl=db.children;for(db.appendChild(document.getElementsByTagName('CNV-VV')[0]);'CNV-VV'!=chl[0].tagName;)db.removeChild(chl[0]);window.variable={w:chl[0].all_canvas[0].width,h:chl[0].all_canvas[0].height};"
        val request = chain.request()
        val url = request.url().toString()

        val newRequest = request.newBuilder()
            .url(url.substringBefore(indicator))
            .build()
        val response = chain.proceed(newRequest)
        Log.d("japscan", "network req $url")
        if (!url.endsWith(indicator)) return@addInterceptor response
        // Webview screenshotting code
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var height = 0
        var width = 0

        handler.post {
            val webview = WebView(Injekt.get<Application>())
            webView = webview
            webview.settings.javaScriptEnabled = true
            webview.settings.domStorageEnabled = true
            webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)

            webview.webChromeClient = object : WebChromeClient() {
                @SuppressLint("NewApi")
                override fun onProgressChanged(view: WebView, progress: Int) {
                    if (progress == 100) {
                        Log.d("japscan", "loaded page, running JS")
                        view.evaluateJavascript(cleanupjs) {
                            Log.d("japscan", "received alert $it, unlatching")
                            if (it.contains('{')) {
                                val j = JsonParser().parse(it).asJsonObject
                                width = j["w"].asInt
                                height = j["h"].asInt
                                Log.d("japscan", "passed $width $height")
                                latch.countDown()
                            } else {
                                Log.d("japscan", "returned null, reloading")
                                webview.loadUrl(url.replace("&wvsc", ""))
                            }
                        }
                    }
                }
            }
            webview.loadUrl(url.replace("&wvsc", ""))
        }

        Log.d("japscan", "awaiting alert")
        latch.await()

        webView!!.measure(width, height)
        webView!!.layout(0, 0, width, height)
        Thread.sleep(350)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var canvas = Canvas(bitmap)
        webView!!.draw(canvas)

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)

        val rb = ResponseBody.create(MediaType.parse("image/png"), output.toByteArray())
        response.newBuilder().body(rb).build()
    }.build()

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM yyyy", Locale.US)
        }
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/mangas/", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        pageNumberDoc = document

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        val hasNextPage = false
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaSelector() = "#top_mangas_week li > span"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()

            val s = StringUtils.stripAccents(it.text())
                .replace("[\\W]".toRegex(), "-")
                .replace("[-]{2,}".toRegex(), "-")
                .replace("^-|-$".toRegex(), "")
            manga.thumbnail_url = "$baseUrl/imgs/mangas/$s.jpg".toLowerCase(Locale.ROOT)
        }
        return manga
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector())
            .distinctBy { element -> element.select("a").attr("href") }
            .map { element ->
                latestUpdatesFromElement(element)
            }
        val hasNextPage = false
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesSelector() = "#chapters > div > h3.text-truncate"
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isEmpty()) {
            val uri = Uri.parse(baseUrl).buildUpon()
                .appendPath("mangas")
            filters.forEach { filter ->
                when (filter) {
                    is TextField -> uri.appendPath(((page - 1) + filter.state.toInt()).toString())
                    is PageList -> uri.appendPath(((page - 1) + filter.values[filter.state]).toString())
                }
            }
            return GET(uri.toString(), headers)
        } else {
            val formBody = FormBody.Builder()
                .add("search", query)
                .build()
            val searchHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build()
            return POST("$baseUrl/live-search/", searchHeaders, formBody)
        }
    }

    override fun searchMangaNextPageSelector(): String? = "li.page-item:last-child:not(li.active)"
    override fun searchMangaSelector(): String = "div.card div.p-2, a.result-link"
    override fun searchMangaParse(response: Response): MangasPage {
        if ("live-search" in response.request().url().toString()) {
            val body = response.body()!!.string()
            val json = JsonParser().parse(body).asJsonArray
            val mangas = json.map { jsonElement ->
                searchMangaFromJson(jsonElement)
            }

            val hasNextPage = false

            return MangasPage(mangas, hasNextPage)
        } else {
            val document = response.asJsoup()

            val mangas = document.select(searchMangaSelector()).map { element ->
                searchMangaFromElement(element)
            }

            val hasNextPage = searchMangaNextPageSelector()?.let { selector ->
                document.select(selector).first()
            } != null

            return MangasPage(mangas, hasNextPage)
        }
    }

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select("p a").let {
            title = it.text()
            url = it.attr("href")
        }
    }

    private fun searchMangaFromJson(jsonElement: JsonElement): SManga = SManga.create().apply {
        title = jsonElement["name"].string
        url = jsonElement["url"].string
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#main > .card > .card-body").first()

        val manga = SManga.create()
        manga.thumbnail_url = "$baseUrl/${infoElement.select(".d-flex > div.m-2:eq(0) > img").attr("src")}"

        infoElement.select(".d-flex > div.m-2:eq(1) > p.mb-2").forEachIndexed { _, el ->
            when (el.select("span").text().trim()) {
                "Auteur(s):" -> manga.author = el.text().replace("Auteur(s):", "").trim()
                "Artiste(s):" -> manga.artist = el.text().replace("Artiste(s):", "").trim()
                "Genre(s):" -> manga.genre = el.text().replace("Genre(s):", "").trim()
                "Statut:" -> manga.status = el.text().replace("Statut:", "").trim().let {
                    parseStatus(it)
                }
            }
        }
        manga.description = infoElement.select("> p").text().orEmpty()

        return manga
    }

    private fun parseStatus(status: String) = when {
        status.contains("En Cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "#chapters_list > div.collapse > div.chapters_list" +
        ":not(:has(.badge:contains(SPOILER),.badge:contains(RAW),.badge:contains(VUS)))"
    // JapScan sometimes uploads some "spoiler preview" chapters, containing 2 or 3 untranslated pictures taken from a raw. Sometimes they also upload full RAWs/US versions and replace them with a translation as soon as available.
    // Those have a span.badge "SPOILER" or "RAW". The additional pseudo selector makes sure to exclude these from the chapter list.

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.ownText()
        // Using ownText() doesn't include childs' text, like "VUS" or "RAW" badges, in the chapter name.
        chapter.date_upload = element.select("> span").text().trim().let { parseChapterDate(it) }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = document.getElementsByTag("option").mapIndexed { i, it -> Page(i, "", baseUrl + it.attr("value") + "&wvsc") }
        Log.d("japscan", pages.first().imageUrl.toString())
        return pages
    }

    override fun imageUrlParse(document: Document): String = ""

    // Filters
    private class TextField(name: String) : Filter.Text(name)

    private class PageList(pages: Array<Int>) : Filter.Select<Int>("Page #", arrayOf(0, *pages))

    override fun getFilterList(): FilterList {
        val totalPages = pageNumberDoc?.select("li.page-item:last-child a")?.text()
        val pagelist = mutableListOf<Int>()
        return if (!totalPages.isNullOrEmpty()) {
            for (i in 0 until totalPages.toInt()) {
                pagelist.add(i + 1)
            }
            FilterList(
                Filter.Header("Page alphabétique"),
                PageList(pagelist.toTypedArray())
            )
        } else FilterList(
            Filter.Header("Page alphabétique"),
            TextField("Page #"),
            Filter.Header("Appuyez sur reset pour la liste")
        )
    }

    private var pageNumberDoc: Document? = null
}
