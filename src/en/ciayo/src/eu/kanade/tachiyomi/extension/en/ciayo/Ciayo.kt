package eu.kanade.tachiyomi.extension.en.ciayo

import android.util.Log
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.jsonObject
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

class Ciayo : HttpSource() {

    //Info
    override val name: String = "Ciayo Comics"
    override val baseUrl: String = "https://www.ciayo.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    //Popular
    override fun popularMangaParse(response: Response): MangasPage {
        val body = response.body()!!.string()
        val json = JsonParser().parse(body)["c"]
        Log.i("TachiDebug","Json => $json")
        val data = json["data"].asJsonArray

        val mangas = data.map { jsonObject ->
            popularMangaFromJson(jsonObject)
        }

        val hasNextPage = json["meta"]["more"].string == "true"

        return MangasPage(mangas, hasNextPage)
    }
    override fun popularMangaRequest(page: Int): Request {
        val current = if((page-1)*10 < 1) "" else (page-1)*10
        val previous = if ((page-1)*10-1 <1) "" else (page-1)*10-1
        val url = "https://vueserion.ciayo.com/3.3/comics/?current=$current&previous=$previous&app=desktop&type=comic&count=10&language=$lang&with=image,genres"
        Log.i("TachiDebug","URL => $url")
        return GET(url)
    }
    private fun popularMangaFromJson(json: JsonElement): SManga = SManga.create().apply {
        title = json["title"].string
        setUrlWithoutDomain(json["share_url"].string)
        Log.i("TachiDebug",url)
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

        val hasNextPage = json["meta"]["more"].string == "true"

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val current = if((page-1)*10 < 1) "" else (page-1)*10
        val previous = if ((page-1)*10-1 <1) "" else (page-1)*10-1
        return GET("https://vueserion.ciayo.com/3.3/comics/new-release?app=desktop&language=$lang&current=$current&previous=$previous&count=10&with=image,genres&type=comic")
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
        return GET("https://vueserion.ciayo.com/3.3/comics/$slug/chapters?current=&count=9999&app=desktop&language=$lang&with=image,comic&sort=desc")
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    //Pages

    override fun pageListParse(response: Response): List<Page> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
    override fun imageUrlParse(response: Response): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
