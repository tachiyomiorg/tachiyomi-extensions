package eu.kanade.tachiyomi.extension.fr.japscan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
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
import java.io.InputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.apache.commons.lang3.StringUtils
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class Japscan : ParsedHttpSource() {

    override val id: Long = 11

    override val name = "Japscan"

    override val baseUrl = "https://www.japscan.co"

    override val lang = "fr"

    override val supportsLatest = true

    private val keysheetChapterUrl1 = "https://www.japscan.co/lecture-en-ligne/bloody-kiss/volume-1/"
    private val keysheetChapterUrl2 = "https://www.japscan.co/lecture-en-ligne/1-nen-a-gumi-no-monster/1/"

    private var keysheet = "0123456789abcdefghijklmnopqrstuvwxyz"

    private val realpageBk2 = "221518b2f9a20842e4c5379223a5881255d2e522c32277923242520284a542f28085b952e422b1e5a97216b262e5e6621535a22/552c5c112f3e2c4f2e122f025517294925042f322d54513e25822c7e2ev0274751116a4266386a0d56w2506a578b562f6exd2e6/65f435e996fs95fw1160125x715062eve104e33va38454bu632x1315b4fxa10244e2c438932ta38v1158d4e323cx237x933u435/1038xa1"
    private val realpageBk4 = "a336f98370e3d9130516d873045639a35633468374c3389353f36313759623633176406345c3e2e60033e713c37687239616b3f6732612a3d4437/5738213e1d642b3d5a3f113d4e3b666c423e973a8d3bja3a50662772597a4270196akc6b74609a613277l1337d6c0776ge60k3220735l85410640/33a25325d2a927b446al56al36fi8672e4dl120113dj7205543ja455354i847lc436955lf26315739529643h744j6249c5e4e40l34el14ci74822/49l12"
    private val realpageBk14 = "4033163017801660626355d0118326c0c3202380719055b0e070e020a2e3d0c04883e750d2a029f397704470f0e3a4208303f0b370a37930816022/f0097038e3a9a0e2c0781021d0f303a120f6d075b05nd0e27379d48204b164f8a36od374a3c6f3e0b4fpa0841357f44kc3co798740c8c02p924853/e7b0398052d996e491534p331p331m8319616p6958d01n8922c1cn2122827m915pb1e372ep29a0e2d0c24681el51dna926f291714p71fpe1am2189/01fpb9"
    private val realpageBk29 = "e952450996d945591172e4b9e0528549828912293089e469a939a939c172c9d997b276f991c9a842566973f96922e359c222f9b21/932984980e931e908d98742f819f179a7a9d0c972e2a07975794419az6931c248032173809377523a028322b51279a3fbf903e2d6/03dw72bae8a6399bd1f7f26bf8d7196za85140fz00d121ey504b0092d14b581971e9b1e5508x408z8815213080eb60ab30ay30c87/0cbd8"
    private val realpageMonster1 = "d7b023a0f83391903313f630b090311054d04910118397b0c140003368b3e3d343e0a390a76032c0d990f16387104953d110405378d030/4388f0d7a052f05820e853203377a04760e06034a0c5b019c05p805223198472d41174f863aqe394f3e6c32014ar30642387f41m13dqe9/f8a0f7700rf95890era9a9e26211a1a29062dqb12ra1fp319p993r11fq31bp9936115pd1421214819pb9c8410p396qb1e2d14qe1ap99a8/603rc9"
    private val realPageUrls = listOf(
        realpageBk2,
        realpageBk4,
        realpageBk14,
        realpageBk29,
        realpageMonster1
    )

    override val client: OkHttpClient = network.cloudflareClient.newBuilder().addInterceptor { chain ->
        val indicator = "&decodeImage"

        val request = chain.request()
        val url = request.url().toString()

        val newRequest = request.newBuilder()
            .url(url.substringBefore(indicator))
            .build()
        val response = chain.proceed(newRequest)

        if (!url.endsWith(indicator)) return@addInterceptor response

        val res = response.body()!!.byteStream().use {
            decodeImage(it)
        }

        val rb = ResponseBody.create(MediaType.parse("image/png"), res)
        response.newBuilder().body(rb).build()
    }.build()

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM yyyy", Locale.US)
        }
    }

    private fun loadKeysheetChapters() {
        var response = client.newCall(GET(keysheetChapterUrl1, headers)).execute()
        val doc = response.asJsoup()
        response = client.newCall(GET(keysheetChapterUrl2, headers)).execute()
        val doc2 = response.asJsoup()

        // 「MULTI-DOCUMENT DRIFTING!!」
        createKeysheet(doc, doc2)
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

    private fun createKeysheet(doc: Document, doc2: Document) {
        val pageUrls = mutableListOf<String>()

        var pages = doc.select("select#pages").first()?.select("option")!! // if this is null we're done
        for (i in listOf(1, 3, 13, 28)) {
            pageUrls.add(pages[i].attr("data-img").substring("https://c.japscan.co/".length, pages[i].attr("data-img").length - 4))
        }
        pages = doc2.select("select#pages").first()?.select("option")!! // if this is null we're done
        for (i in listOf(0)) {
            pageUrls.add(pages[i].attr("data-img").substring("https://c.japscan.co/".length, pages[i].attr("data-img").length - 4))
        }

        val az = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray()
        val ks = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray()

        for (i in 0 until realPageUrls.count())
            for (j in 0 until realPageUrls[i].length) {
                if (realPageUrls[i][j] != pageUrls[i][j]) {
                    ks[az.indexOf(pageUrls[i][j])] = realPageUrls[i][j]
                }
            }
        keysheet = ks.joinToString("")
    }

    override fun pageListParse(document: Document): List<Page> {
        loadKeysheetChapters()
        val pages = mutableListOf<Page>()
        var imagePath = "(.*\\/).*".toRegex().find(document.select("#image").attr("data-src"))?.groupValues?.get(1)
        val imageScrambled = if (!document.select("script[src^='/js/iYFbYi_U']").isNullOrEmpty()) "&decodeImage" else ""

        document.select("select#pages").first()?.select("option")?.forEach {
            if (it.attr("data-img").startsWith("http")) imagePath = ""
            pages.add(Page(pages.size, "", decodeImageUrl("$imagePath${it.attr("data-img")}") + imageScrambled))
        }

        return pages
    }

    private fun decodeImageUrl(url: String): String {
        val az = "0123456789abcdefghijklmnopqrstuvwxyz"
        // skip https://, cut after next slash and before extension
        val urlBase = url.substring(0, url.indexOf('/', 10) + 1)
        val extension = url.substring(url.length - 4, url.length)
        val encodedPart = url.substring(url.indexOf('/', 10) + 1, url.length - 4)

        return urlBase + encodedPart.map { if (az.indexOf(it) < 0) it else keysheet[az.indexOf(it)] }.joinToString("") + extension
    }

    override fun imageUrlParse(document: Document): String = ""

    private fun decodeImage(img: InputStream): ByteArray {
        val input = BitmapFactory.decodeStream(img)

        val xResult = Bitmap.createBitmap(input.width,
            input.height,
            Bitmap.Config.ARGB_8888)
        val xCanvas = Canvas(xResult)

        val result = Bitmap.createBitmap(input.width,
            input.height,
            Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        for (x in 0..input.width step 200) {
            val col1 = Rect(x, 0, x + 100, input.height)
            if ((x + 200) < input.width) {
                val col2 = Rect(x + 100, 0, x + 200, input.height)
                xCanvas.drawBitmap(input, col1, col2, null)
                xCanvas.drawBitmap(input, col2, col1, null)
            } else {
                val col2 = Rect(x + 100, 0, input.width, input.height)
                xCanvas.drawBitmap(input, col1, col1, null)
                xCanvas.drawBitmap(input, col2, col2, null)
            }
        }

        for (y in 0..input.height step 200) {
            val row1 = Rect(0, y, input.width, y + 100)

            if ((y + 200) < input.height) {
                val row2 = Rect(0, y + 100, input.width, y + 200)
                canvas.drawBitmap(xResult, row1, row2, null)
                canvas.drawBitmap(xResult, row2, row1, null)
            } else {
                val row2 = Rect(0, y + 100, input.width, input.height)
                canvas.drawBitmap(xResult, row1, row1, null)
                canvas.drawBitmap(xResult, row2, row2, null)
            }
        }

        val output = ByteArrayOutputStream()
        result.compress(Bitmap.CompressFormat.PNG, 100, output)
        return output.toByteArray()
    }

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
