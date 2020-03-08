package eu.kanade.tachiyomi.extension.en.ciayo

import android.util.Log
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.jsonObject
import com.github.salomonbrys.kotson.long
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Date

class Ciayo : HttpSource() {

    //Info
    override val name: String = "Ciayo Comics"
    override val baseUrl: String = "https://www.ciayo.com"
    private val apiUrl = "https://vueserion.ciayo.com/3.3/comics"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    //Page Helpers
    private var next: String? = ""
    private var previous: String? = ""

    //Popular
    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body()!!.string()
        val json = JsonParser().parse(body)["c"]
        //Log.i("TachiDebug","Json => $json")
        val data = json["data"].asJsonArray

        val mangas = data.map { jsonObject ->
            popularMangaFromJson(jsonObject)
        }

        previous = next
        next = json["meta"]["cursor"]["next"].nullString

        val hasNextPage = json["meta"]["more"].string == "true"

        return MangasPage(mangas, hasNextPage)
    }
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/?current=$next&previous=$previous&app=desktop&type=comic&count=15&language=$lang&with=image,genres"
        Log.i("TachiDebug","URL => $url")
        return GET(url)
    }
    private fun popularMangaFromJson(json: JsonElement): SManga = SManga.create().apply {
        title = json["title"].string
        setUrlWithoutDomain(json["share_url"].string)
        //Log.i("TachiDebug",url)
        thumbnail_url = json["image"]["cover"].string
    }

    //Latest

    override fun latestUpdatesParse(response: Response): MangasPage {
        val body = response.body()!!.string()
        val json = JsonParser().parse(body)["c"]
        val data = json["data"].asJsonArray

        val mangas = data.map { jsonObject ->
            latestUpdatesFromJson(jsonObject)
        }

        previous = next
        next = json["meta"]["cursor"]["next"].nullString

        val hasNextPage = json["meta"]["more"].string == "true"

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/new-release?app=desktop&language=$lang&current=$next&previous=$previous&count=10&with=image,genres&type=comic"
        Log.i("TachiDebug","URL => $url")
        return GET(url)
    }

    private fun latestUpdatesFromJson(json: JsonElement): SManga = popularMangaFromJson(json)

    //Search

    override fun searchMangaParse(response: Response): MangasPage {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    //Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val script = document.select("script:containsdata(NEXT_DATA)").html()
        val data = script.substringAfter("__NEXT_DATA__ =").substringBefore("};").trim()+"}"
        val json = JsonParser().parse(data)["props"]["pageProps"]["comicProfile"]
        return SManga.create().apply {
            title = json["title"].string
            author = json["author"].string
            artist = author
            description = json["description"].string
            genre = json["genres"].asJsonArray.joinToString(", ") {it["name"].string}
            thumbnail_url = json["image"]["cover"].string
        }
    }

    //Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/$slug/chapters?current=&count=999&app=desktop&language=$lang&with=image,comic&sort=desc")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val body = response.body()!!.string()
        val json = JsonParser().parse(body)["c"]
        val data = json["data"].asJsonArray
        return data.map {
            SChapter.create().apply {
                name = "${it["episode"].string} - ${it["name"].string}"
                scanlator = "[${it["status"].string}]"
                setUrlWithoutDomain(it["share_url"].string)
                Log.i("TachiDebug","Date => ${it["release_date"].string}")
                date_upload = it["release_date"].long*1000

            }
        }

    }

    //Pages

    override fun pageListParse(response: Response): List<Page> = mutableListOf<Page>().apply {
        val document = response.asJsoup()
        document.select("div.chapterViewer img").forEach {
            add(Page(size,"", it.attr("abs:src")))
        }

    }
    override fun imageUrlParse(response: Response): String = throw Exception("ImgParse Not Used")

}
