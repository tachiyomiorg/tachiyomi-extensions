package eu.kanade.tachiyomi.extension.en.mangadog

import android.net.Uri
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.*

class Mangadog : HttpSource() {

    override val name = "MangaDog"
    override val baseUrl = "https://mangadog.club"
    private val cdn = "https://cdn.mangadog.club"
    override val lang = "en"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/index/classification/search_test?page=$page&state=all&demographic=all&genre=all", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/index/latestupdate/getUpdateResult?page=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/index/keywordsearch/index").buildUpon()
                .appendQueryParameter("query", query)
        return GET(uri.toString(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        //val page = response.request().url().queryParameterValues("page").toString().toInt()
        val jsonData= response.body()!!.string()
        val results = JsonParser().parse(jsonData)
        val data = results["data"]["data"]
        val mangas = mutableListOf<SManga>()
        for (i in 0 until data.asJsonArray.size()) {
            mangas.add(popularMangaFromjson(data[i]))
        }

        val hasNextPage = true //page < results["data"]["pageNum"].int
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaFromjson(json: JsonElement): SManga {
        val manga = SManga.create()
        manga.title = json["name"].string.trim()
        manga.thumbnail_url = cdn + json["image"].string.replace("\\/","/")
        val searchname = json["search_name"].string
        val id = json["id"].string
        manga.url = "/detail/$searchname/$id.html"
        return manga
    }
    
    override fun latestUpdatesParse(response: Response):  MangasPage {
        val jsonData= response.body()!!.string()
        val results = JsonParser().parse(jsonData)
        val data = results["data"]
        val mangas = mutableListOf<SManga>()
        for (i in 0 until data.asJsonArray.size()) {
            mangas.add(popularMangaFromjson(data[i]))
        }

        val hasNextPage = true //data.asJsonArray.size()>18
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonData= response.body()!!.string()
        val results = JsonParser().parse(jsonData)
        val data = results["suggestions"]
        val mangas = mutableListOf<SManga>()
        for (i in 0 until 1) {
            mangas.add(searchMangaFromjson(data[i]))
        }

        val hasNextPage = false
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromjson(json: JsonElement): SManga {
        val manga = SManga.create()
        manga.title = json["value"].string.trim()
        val data = json["data"].string.replace("\\/","/")
        manga.url = "/detail/$data.html"
        return manga
    }


    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/").substringBefore(".html")
        return GET("$baseUrl/index/detail/getChapterList?comic_id=$id&page=1", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonData= response.body()!!.string()
        val results = JsonParser().parse(jsonData)
        val data = results["data"]["data"]
        val chapters = mutableListOf<SChapter>()
        for (i in 0 until data.asJsonArray.size()) {
            chapters.add(chapterFromjson(data[i]))
        }
        return chapters
    }

    private fun chapterFromjson(json: JsonElement): SChapter {
        val chapter = SChapter.create()
        val searchname = json["search_name"].string
        val id = json["comic_id"].string
        chapter.url = "/read/read/$searchname/$id.html" //The url should include the manga name but it doesn't seem to matter
        chapter.name = json["name"].string.trim()
        chapter.chapter_number = json["obj_id"].asFloat
        chapter.date_upload = parseDate(json["create_date"].string)
        return chapter
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date).time
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img.detail-post-img").attr("abs:src")
        manga.description = document.select("h2.fs15 + p").text().trim()
        manga.author = document.select("a[href*=artist]").text()
        manga.artist = document.select("a[href*=artist]").text()
        val glist = document.select("div.col-sm-10.col-xs-9.text-left.toe.mlr0.text-left-m a[href*=genre]").map { it.text().substringAfter(",").capitalize() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select("span.label.label-success").first().text()) {
            "update" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    override fun pageListParse(response: Response): List<Page> {
        val body = response.asJsoup()
        val pages = mutableListOf<Page>()
        val elements = body.select("img[data-src]")
        for (i in 0 until elements.size) {
            pages.add(Page(i, "", elements[i].select("img").attr("data-src")))
        }
        return pages
    }

    override fun imageUrlParse(response: Response) = throw Exception("Not used")
}
