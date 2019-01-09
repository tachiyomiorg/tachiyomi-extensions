package eu.kanade.tachiyomi.extension.all.nhentai

import android.net.Uri
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.Request
import okhttp3.Response
import rx.Observable

/**
 * NHentai source
 */

open class NHentai(override val lang: String, val nhLang: String) : HttpSource() {
    override val name = "nhentai"

    override val baseUrl = "https://nhentai.net"

    override val supportsLatest = true

    //TODO There is currently no way to get the most popular mangas
    //TODO Instead, we delegate this to the latest updates thing to avoid confusing users with an empty screen
    override fun fetchPopularManga(page: Int) = fetchLatestUpdates(page)

    override fun popularMangaRequest(page: Int)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun popularMangaParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/api/galleries/search").buildUpon()
        uri.appendQueryParameter("query", "language:$nhLang $query")
        uri.appendQueryParameter("page", page.toString())
        filters.forEach {
            if (it is UriFilter)
                it.addToUri(uri)
        }
        return nhGet(uri.toString(), page)
    }

    override fun searchMangaParse(response: Response) = parseResultPage(response)

    override fun fetchLatestUpdates(page: Int) = fetchSearchManga(page, "", getFilterList())

    override fun latestUpdatesRequest(page: Int)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun latestUpdatesParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun mangaDetailsParse(response: Response)
            = parseGallery(jsonParser.parse(response.body()!!.string()).obj)

    //Hack so we can use a different URL for fetching manga details and opening the details in the browser
    override fun fetchMangaDetails(manga: SManga)
            = client.newCall(urlToDetailsRequest(manga.url))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }

    override fun mangaDetailsRequest(manga: SManga) = nhGet( baseUrl + manga.url )

    fun urlToDetailsRequest(url: String) = nhGet("$baseUrl/api/gallery/${url.substringAfterLast('/')}")

    fun parseResultPage(response: Response): MangasPage {
        val res = jsonParser.parse(response.body()!!.string()).obj

        res["error"]?.let {
            throw RuntimeException("An error occurred while performing the search: $it")
        }

        val results = res.getAsJsonArray("result")?.map {
            parseGallery(it.obj)
        }
        val numPages = res["num_pages"].nullInt
        if (results != null && numPages != null)
            return MangasPage(results, numPages > response.request().tag() as Int)
        return MangasPage(emptyList(), false)
    }

    fun rawParseGallery(obj: JsonObject) = NHentaiMetadata().apply {
        uploadDate = obj["upload_date"].nullLong

        favoritesCount = obj["num_favorites"].nullLong

        mediaId = obj["media_id"].nullString

        obj["title"].nullObj?.let {
            japaneseTitle = it["japanese"].nullString
            shortTitle = it["pretty"].nullString
            englishTitle = it["english"].nullString
        }

        obj["images"].nullObj?.let {
            coverImageType = it["cover"]?.get("t").nullString
            it["pages"].nullArray?.map {
                it.nullObj?.get("t").nullString
            }?.filterNotNull()?.let {
                pageImageTypes.clear()
                pageImageTypes.addAll(it)
            }
            thumbnailImageType = it["thumbnail"]?.get("t").nullString
        }

        scanlator = obj["scanlator"].nullString

        id = obj["id"]?.asLong

        obj["tags"].nullArray?.map {
            val asObj = it.obj
            Pair(asObj["type"].nullString, asObj["name"].nullString)
        }?.apply {
            tags.clear()
        }?.forEach {
            if (it.first != null && it.second != null)
                tags.getOrPut(it.first!!) { mutableListOf() }.add(Tag(it.second!!, false))
        }!!
    }

    fun parseGallery(obj: JsonObject) = SManga.create().apply {
        rawParseGallery(obj).copyTo(this)
    }

    fun lazyLoadMetadata(url: String) =
            client.newCall(urlToDetailsRequest(url))
                    .asObservableSuccess()
                    .map {
                        rawParseGallery(jsonParser.parse(it.body()!!.string()).obj)
                    }!!

    override fun fetchChapterList(manga: SManga)
            = Observable.just(listOf(SChapter.create().apply {
        url = manga.url
        name = "Chapter"
        chapter_number = 1f
    }))!!

    override fun fetchPageList(chapter: SChapter)
            = lazyLoadMetadata(chapter.url).map { metadata ->
        if (metadata.mediaId == null) emptyList()
        else
            metadata.pageImageTypes.mapIndexed { index, s ->
                val imageUrl = imageUrlFromType(metadata.mediaId!!, index + 1, s)
                Page(index, imageUrl!!, imageUrl)
            }
    }!!

    override fun fetchImageUrl(page: Page) = Observable.just(page.imageUrl!!)!!

    fun imageUrlFromType(mediaId: String, page: Int, t: String) = NHentaiMetadata.typeToExtension(t)?.let {
        "https://i.nhentai.net/galleries/$mediaId/$page.$it"
    }

    override fun chapterListParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun pageListParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun imageUrlParse(response: Response)
            = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList(SortFilter())

    private class SortFilter : UriSelectFilter("Sort", "sort", arrayOf(
            Pair("date", "Date"),
            Pair("popular", "Popularity")
    ), firstIsUnspecified = false)

    private fun nhGet(url: String, tag: Any? = null) = GET(url)
            .newBuilder()
            //Requested by nhentai admins to use a custom user agent
            .header("User-Agent",
                    "Mozilla/5.0 (X11; Linux x86_64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/56.0.2924.87 " +
                            "Safari/537.36 " +
                            "Tachiyomi/1.0")
            .tag(tag).build()!!

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    private open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                                       val firstIsUnspecified: Boolean = true,
                                       defaultValue: Int = 0) :
            Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    private interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    companion object {
        val jsonParser by lazy {
            JsonParser()
        }
    }
}
