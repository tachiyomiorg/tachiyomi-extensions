package eu.kanade.tachiyomi.extension.en.mangadog

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Mangadog : HttpSource() {

    override val name = "MangaDog"

    override val baseUrl = "https://mangadog.club"

    private val cdn = "https://cdn.mangadog.club"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/index/classification/search_test?page=$page&state=all&demographic=all&genre=all", headers)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/index/latestupdate/getUpdateResult?page=$page", headers)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/index/keywordsearch/index").buildUpon()
            .appendQueryParameter("query", query)
        return GET(uri.toString(), headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val jsonResult = json.parseToJsonElement(response.body!!.string()).jsonObject

        val mangaList = jsonResult["data"]!!.jsonObject["data"]!!.jsonArray.map { jsonEl ->
            popularMangaFromJson(jsonEl.jsonObject)
        }

        return MangasPage(mangaList, hasNextPage = true)
    }

    private fun popularMangaFromJson(jsonObj: JsonObject): SManga = SManga.create().apply {
        title = jsonObj["name"]!!.jsonPrimitive.content.trim()
        thumbnail_url = cdn + jsonObj["image"]!!.jsonPrimitive.content.replace("\\/", "/")

        val searchName = jsonObj["search_name"]!!.jsonPrimitive.content
        val id = jsonObj["id"]!!.jsonPrimitive.content
        url = "/detail/$searchName/$id.html"
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val jsonResult = json.parseToJsonElement(response.body!!.string()).jsonObject

        val mangaList = jsonResult["data"]!!.jsonArray.map { jsonEl ->
            popularMangaFromJson(jsonEl.jsonObject)
        }

        return MangasPage(mangaList, hasNextPage = true)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val jsonResult = json.parseToJsonElement(response.body!!.string()).jsonObject

        val mangaList = jsonResult["suggestions"]!!.jsonArray.map { jsonEl ->
            searchMangaFromJson(jsonEl.jsonObject)
        }

        return MangasPage(mangaList, hasNextPage = false)
    }

    private fun searchMangaFromJson(jsonObj: JsonObject): SManga = SManga.create().apply{
        title = jsonObj["value"]!!.jsonPrimitive.content.trim()

        val dataValue = jsonObj["data"]!!.jsonPrimitive.content.replace("\\/", "/")
        url = "/detail/$dataValue.html"
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/").substringBefore(".html")
        return GET("$baseUrl/index/detail/getChapterList?comic_id=$id&page=1", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonResult = json.parseToJsonElement(response.body!!.string()).jsonObject

        return jsonResult["data"]!!.jsonObject["data"]!!.jsonArray.map { jsonEl ->
            chapterFromJson(jsonEl.jsonObject)
        }
    }

    private fun chapterFromJson(jsonObj: JsonObject): SChapter = SChapter.create().apply {
        // The url should include the manga name but it doesn't seem to matter
        val searchname = jsonObj["search_name"]!!.jsonPrimitive.content
        val id = jsonObj["comic_id"]!!.jsonPrimitive.content
        url = "/read/read/$searchname/$id.html"

        name = jsonObj["name"]!!.jsonPrimitive.content.trim()
        chapter_number = jsonObj["obj_id"]!!.jsonPrimitive.float
        date_upload = parseDate(jsonObj["create_date"]!!.jsonPrimitive.content)
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time ?: 0L
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply{
        val document = response.asJsoup()

        thumbnail_url = document.select("img.detail-post-img").attr("abs:src")
        description = document.select("h2.fs15 + p").text().trim()
        author = document.select("a[href*=artist]").text()
        artist = document.select("a[href*=artist]").text()
        genre = document.select("div.col-sm-10.col-xs-9.text-left.toe.mlr0.text-left-m a[href*=genre]")
            .joinToString { it.text().substringAfter(",").capitalize(Locale.ROOT) }
        status = when (document.select("span.label.label-success").first().text()) {
            "update" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select("img[data-src]").mapIndexed { i, el ->
            Page(i, "", el.select("img").attr("data-src"))
        }
    }

    override fun imageUrlParse(response: Response) = ""
}
