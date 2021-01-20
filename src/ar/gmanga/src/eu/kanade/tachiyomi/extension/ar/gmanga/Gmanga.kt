package eu.kanade.tachiyomi.extension.ar.gmanga

import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.nullString
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_CHAPTER_LISTING
import eu.kanade.tachiyomi.extension.ar.gmanga.GmangaPreferences.Companion.PREF_CHAPTER_LISTING_SHOW_POPULAR
import eu.kanade.tachiyomi.lib.ratelimit.RateLimitInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response

class Gmanga : ConfigurableSource, HttpSource() {

    private val domain: String = "gmanga.me"

    override val baseUrl: String = "https://$domain"

    override val lang: String = "ar"

    override val name: String = "GMANGA"

    override val supportsLatest: Boolean = true

    private val gson = Gson()

    private val preferences = GmangaPreferences(id)

    private val rateLimitInterceptor = RateLimitInterceptor(4)

    override val client: OkHttpClient = network.client.newBuilder()
        .addNetworkInterceptor(rateLimitInterceptor)
        .build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", USER_AGENT)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = preferences.setupPreferenceScreen(screen)

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) = preferences.setupPreferenceScreen(screen)

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/mangas/$mangaId/releases", headers)
    }

    @ExperimentalStdlibApi
    override fun chapterListParse(response: Response): List<SChapter> {
        val data = decryptResponse(response)

        val chapters: List<JsonArray> = buildList {
            val allChapters = data["rows"][0]["rows"].asJsonArray.map { it.asJsonArray }

            when (preferences.getString(PREF_CHAPTER_LISTING)) {
                PREF_CHAPTER_LISTING_SHOW_POPULAR -> addAll(
                    allChapters.groupBy { it.asJsonArray[6].asFloat }
                        .map { (_: Float, versions: List<JsonArray>) -> versions.maxByOrNull { it[4].asLong }!! }
                )
                else -> addAll(allChapters)
            }
        }

        return chapters.map {
            SChapter.create().apply {
                chapter_number = it[6].asFloat

                val chapterName = it[8].asString.let { if (it.trim() != "") " - $it" else "" }

                url = "/r/${it[0]}"
                name = "${chapter_number.let { if (it % 1 > 0) it else it.toInt() }}$chapterName"
                date_upload = it[3].asLong * 1000
                scanlator = it[10].asString
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        return MangasPage(
            data["mangaDataAction"]["newMangas"].asJsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it["id"].asString}"
                    title = it["title"].asString
                    val thumbnail = "medium_${it["cover"].asString.substringBeforeLast(".")}.webp"
                    thumbnail_url = "https://media.$domain/uploads/manga/cover/${it["id"].asString}/$thumbnail"
                }
            },
            false
        )
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/releases", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        val mangaData = data["mangaDataAction"]["mangaData"].asJsonObject
        return SManga.create().apply {
            description = mangaData["summary"].nullString ?: ""
            artist = mangaData["artists"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
            author = mangaData["authors"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
            genre = mangaData["categories"].asJsonArray.joinToString(", ") { it.asJsonObject["name"].asString }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request().url().toString()
        val data = gson.fromJson<JsonObject>(response.asJsoup().select(".js-react-on-rails-component").html())
        val releaseData = data["readerDataAction"]["readerData"]["release"].asJsonObject

        val hasWebP = releaseData["webp_pages"].asJsonArray.size() > 0
        return releaseData[if (hasWebP) "webp_pages" else "pages"].asJsonArray.map { it.asString }.mapIndexed { index, pageUri ->
            Page(
                index,
                "$url#page_$index",
                "https://media.$domain/uploads/releases/${releaseData["storage_key"].asString}/mq${if (hasWebP) "_webp" else ""}/$pageUri"
            )
        }
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())

    override fun searchMangaParse(response: Response): MangasPage {
        val data = decryptResponse(response)
        val mangas = data["mangas"].asJsonArray
        return MangasPage(
            mangas.asJsonArray.map {
                SManga.create().apply {
                    url = "/mangas/${it["id"].asString}"
                    title = it["title"].asString
                    val thumbnail = "medium_${it["cover"].asString.substringBeforeLast(".")}.webp"
                    thumbnail_url = "https://media.$domain/uploads/manga/cover/${it["id"].asString}/$thumbnail"
                }
            },
            mangas.size() == 50
        )
    }

    private fun decryptResponse(response: Response): JsonObject {
        val encryptedData = gson.fromJson<JsonObject>(response.body()!!.string())["data"].asString
        val decryptedData = decrypt(encryptedData)
        return gson.fromJson(decryptedData)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GmangaFilters.buildSearchPayload(page, query, if (filters.isEmpty()) getFilterList() else filters).let {
            val body = RequestBody.create(MEDIA_TYPE, it.toString())
            POST("$baseUrl/api/mangas/search", headers, body)
        }
    }

    override fun getFilterList() = GmangaFilters.getFilterList()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        private val MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8")
    }
}
