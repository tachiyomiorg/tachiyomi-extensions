package eu.kanade.tachiyomi.extension.all.ninemanga

import android.os.Build
import android.util.Log
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import kotlin.collections.ArrayList

open class NineManga(override val name: String, override val baseUrl: String, override val lang: String) : ParsedHttpSource() {
    override val supportsLatest: Boolean = true

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/category/updated_$page.html", headers)

    override fun latestUpdatesSelector() = "ul.direlist > li"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        element.select("dl.bookinfo").let {
            /*Log.d("URL", it.select("dd > a.bookname").attr("href"))
            Log.d("TITLE", it.select("dd > a.bookname").first().text())
            Log.d("THUMBNAIL_URL", it.select("dt > a > img").attr("src"))*/

            setUrlWithoutDomain(it.select("dd > a.bookname").attr("href"))
            title = it.select("dd > a.bookname").first().text()
            thumbnail_url = it.select("dt > a > img").attr("src")
        }
    }

    override fun latestUpdatesNextPageSelector() = "ul.pageList > li:last-child > a.l"

    override fun popularMangaSelector(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun popularMangaRequest(page: Int): Request {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun popularMangaFromElement(element: Element): SManga {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun mangaDetailsParse(document: Document) =  SManga.create().apply {
        document.select("div.bookintro").let {
            thumbnail_url = document.select("a.bookface > img").attr("src")
            genre = document.select("ul.message > li.genre").let {
                it.select("a").text()
            }
            author = it.select("a.author").text()

            //description = document.select("p.element-description")?.text()
            status = parseStatus(it.select("a.red").first().text().orEmpty())

        }
    }

    private fun parseStatus(status: String) = when {
        status.contains("En curso") -> SManga.ONGOING
        status.contains("Completo") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }





    override fun chapterFromElement(element: Element): SChapter {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chapterListSelector(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun imageUrlParse(document: Document): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun pageListParse(document: Document): List<Page> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun popularMangaNextPageSelector(): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaSelector(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}