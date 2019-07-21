package eu.kanade.tachiyomi.extension.en.guya

import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import okhttp3.*
import org.json.JSONObject
import rx.Observable
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

open class Guya() : HttpSource() {

    override val name = "Guya"
    override val baseUrl = "https://guya.moe"
    override val supportsLatest = false
    override val lang = "en"

    private val SCANLATORS = HashMap<String, String>()

    init {
        // Update the scanlator list if we need to on initialization
        updateScanlators()
    }

    // Request builder for the "browse" page of the manga
    override fun popularMangaRequest(page: Int): Request {
        updateScanlators()
        return GET("$baseUrl/api/get_all_series")
    }

    // Gets the request object from the request
    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.body()!!.string()
        return parseManga(res)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return clientBuilder().newCall(GET("$baseUrl/api/get_all_series/#" + manga.title))
                .asObservableSuccess()
                .map {response ->
                    mangaDetailsParse(response)
                }
    }

    // Called when the series is loaded, or when opening in browser
    override fun mangaDetailsRequest(manga: SManga): Request {
        updateScanlators()
        // We store the metadata of the series we're interested in at the end
        return GET("$baseUrl/reader/series/" + manga.url)
    }

    // Called after the request from mangaDetailsRequest is done
    override fun mangaDetailsParse(response: Response): SManga {
        // I'm not proud of this hack
        val res = response.body()!!.string()
        // We pull this metadata from the request URL
        val series = response.toString().substring(response.toString().lastIndexOf("#") + 1, response.toString().length - 1)
        return parseMangaFromJson(JSONObject(res).getJSONObject(series), series)
    }

    // Gets the chapter list based on the series being viewed
    override fun chapterListRequest(manga: SManga): Request {
        updateScanlators()
        return GET("$baseUrl/api/series/" + manga.url)
    }

    // Called after the request
    override fun chapterListParse(response: Response): List<SChapter> {
        val res = response.body()!!.string()
        return parseChapterList(res)
    }

    // This is called from the chapter.url, so we're getting all the pages anyway
    override fun pageListParse(response: Response): List<Page> {
        // Same hack to grab the metadata of our chapter
        val parser = response.toString()
            .substring(response.toString().lastIndexOf("#") + 1, response.toString().length - 1)
            .split("/")

        val res = response.body()!!.string()

        val json = JSONObject(res)
        val pages = json.getJSONObject("chapters").getJSONObject(parser[0]).getJSONObject("groups")
        val metadata = JSONObject()

        metadata.put("chapter", parser[0])
        metadata.put("scanlator", parser[1])
        metadata.put("slug", json.getString("slug"))
        metadata.put("folder", json.getJSONObject("chapters").getJSONObject(parser[0]).getString("folder"))

        return parsePageFromJson(pages, metadata)
    }

    // ------------- Helpers and whatnot ---------------

    private fun parseChapterList(payload: String): List<SChapter> {
        val response = JSONObject(payload)
        val chapters = response.getJSONObject("chapters")

        val chapterList = ArrayList<SChapter>()

        val iter = chapters.keys()

        while (iter.hasNext()) {
            val chapter = iter.next()
            val chapterObj = chapters.getJSONObject(chapter)
            chapterList.add(parseChapterFromJson(chapterObj, chapter, response.getString("slug")))
        }

        return chapterList.reversed()
    }

    // Helper function to get all the listings
    private fun parseManga(payload: String) : MangasPage {
        val response = JSONObject(payload)
        val mangas = ArrayList<SManga>()

        val iter = response.keys()

        while (iter.hasNext()) {
            val series = iter.next()
            val json = response.getJSONObject(series)
            val manga = parseMangaFromJson(json, series)
            mangas.add(manga)
        }

        return MangasPage(mangas, false)
    }

    // Takes a json of the manga to parse
    private fun parseMangaFromJson(json: JSONObject, title: String): SManga {
        val manga = SManga.create()
        manga.title = title
        manga.artist = json.getString("artist")
        manga.author = json.getString("author")
        manga.description = json.getString("description")
        manga.url = json.getString("slug")
        manga.thumbnail_url = "$baseUrl/" + json.getString("cover")
        return manga
    }

    private fun parseChapterFromJson(json: JSONObject, num: String, slug: String): SChapter {
        val chapter = SChapter.create()

        // Get the scanlator info based on group ranking; do it first since we need it later
        val firstGroupId = json.getJSONObject("groups").keys().next()
        chapter.scanlator = SCANLATORS[firstGroupId]
        chapter.name = (if (num.toFloat() % 1.0 == 0.0) num.toInt() else num.toFloat()).toString() +
                " - " + json.getString("title")
        chapter.chapter_number = num.toFloat()
        chapter.url = "/api/series/$slug/#$num/$firstGroupId"

        return chapter
    }

    private fun parsePageFromJson(json: JSONObject, metadata: JSONObject): List<Page> {
        val pages = json.getJSONArray(metadata.getString("scanlator"))
        val pageArray = ArrayList<Page>()

        for (i in 0 until pages.length()) {
            val page = Page(i + 1, "", pageBuilder(metadata.getString("slug"),
                metadata.getString("folder"),
                pages[i].toString(),
                metadata.getString("scanlator")))
            pageArray.add(page)
        }

        return pageArray
    }

    private fun pageBuilder(slug: String, folder: String, filename: String, groupId: String): String {
        return "$baseUrl/media/manga/$slug/chapters/$folder/$groupId/$filename"
    }

    // Called on every other request function to make sure we
    // have the most up to date scanlator array
    private fun updateScanlators() {
        if (SCANLATORS.isEmpty()) {
            clientBuilder().newCall(GET("$baseUrl/api/get_all_groups")).enqueue(
                    object: Callback {
                        override fun onResponse(call: Call, response: Response) {
                            val json = JSONObject(response.body()!!.string())
                            val iter = json.keys()
                            while (iter.hasNext()) {
                                val scanId = iter.next()
                                SCANLATORS[scanId] = json.getString(scanId)
                            }
                        }
                        override fun onFailure(call: Call, e: IOException) {}
                    }
            )
        }
    }

    private fun clientBuilder(): OkHttpClient = network.cloudflareClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val newReq = chain
                        .request()
                        .newBuilder()
                        .build()
                chain.proceed(newReq)
            }.build()!!

    // ----------------- Things we aren't supporting -----------------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        throw Exception("Searching isn't supported.")
    }

    override fun searchMangaParse(response: Response): MangasPage {
        throw Exception("Searching isn't supported.")
    }

    override fun imageUrlParse(response: Response): String {
        throw Exception("imageUrlParse not supported.")
    }

    override fun latestUpdatesRequest(page: Int): Request {
        throw Exception("Latest updates not supported.")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        throw Exception("Latest updates not supported.")
    }

}
