package eu.kanade.tachiyomi.extension.zh.gufengmh

import android.net.Uri
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element


class Gufengmh : ParsedHttpSource() {

    override val name: String = "古风漫画网"
    override val lang: String = "zh"
    override val supportsLatest: Boolean = true
    override val baseUrl: String = "https://m.gufengmh8.com"

    //Popular


    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/list/click/?page=$page", headers)
    }
    override fun popularMangaNextPageSelector(): String? = "li.next"
    override fun popularMangaSelector(): String = "li.list-comic"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a.txtA").text()
        setUrlWithoutDomain(element.select("a.txtA").attr("abs:href"))
        thumbnail_url = element.select("mip-img").attr("abs:src")
    }

    //Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/list/update/?page=$page", headers)
    }
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    //Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendPath("search")
            .appendQueryParameter("keywords",query)
            .appendQueryParameter("page",page.toString())
        return GET(uri.toString(), headers)
    }
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()
    override fun searchMangaSelector(): String = "div.itemBox"
    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("a.title").text()
        setUrlWithoutDomain(element.select("a.title").attr("abs:href"))
        thumbnail_url = element.select("mip-img").attr("abs:src")
    }

    //Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.select("h1.title").text()
        thumbnail_url = document.select("div#Cover mip-img").attr("abs:src")
        author = document.select("dt:contains(作者) + dd").text()
        artist = author
        genre = document.select("dt:contains(类别) + dd").text()
        description = document.select("p.txtDesc").text()
    }

    //Chapters

    override fun chapterListSelector(): String = "div.list li"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        url = element.select("a").attr("href")
        name = element.select("span").text()
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    //Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        val script = document.select("script:containsData(chapterImages )").html()
        val images = script.substringAfter("chapterImages = [\"").substringBefore("\"]").split("\",\"")
        val path = script.substringAfter("chapterPath = \"").substringBefore("\";")
        val server = script.substringAfter("pageImage = \"").substringBefore("/images/cover")
        images.forEach {
            add(Page(size,"","$server/$path/$it"))
        }
    }
    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")
}

