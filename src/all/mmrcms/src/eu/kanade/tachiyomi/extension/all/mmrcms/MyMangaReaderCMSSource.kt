package eu.kanade.tachiyomi.extension.all.mmrcms

import android.net.Uri
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

class MyMangaReaderCMSSource(override val lang: String,
                             override val name: String,
                             override val baseUrl: String,
                             override val supportsLatest: Boolean,
                             private val itemUrl: String,
                             private val categoryMappings: List<Pair<String, String>>,
                             private val tagMappings: List<Pair<String, String>>?) : HttpSource() {
    private val jsonParser = JsonParser()
    private val itemUrlPath = Uri.parse(itemUrl).pathSegments.first()
    private val parsedBaseUrl = Uri.parse(baseUrl)

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/filterList?page=$page&sortBy=views&asc=false")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //Query overrides everything
        val url: Uri.Builder
        if(query.isNotBlank()) {
            url = Uri.parse("$baseUrl/search")!!.buildUpon()
            url.appendQueryParameter("query", query)
        } else {
            url = Uri.parse("$baseUrl/filterList?page=$page")!!.buildUpon()
            filters.filterIsInstance<UriFilter>()
                    .forEach { it.addToUri(url) }
        }
        return GET(url.toString())
    }
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/filterList?page=$page&sortBy=last_release&asc=false")

    override fun popularMangaParse(response: Response) = internalMangaParse(response)
    override fun searchMangaParse(response: Response): MangasPage {
        return if(response.request().url().queryParameter("query")?.isNotBlank() == true) {
            //If a search query was specified, use search instead!
            MangasPage(jsonParser
                    .parse(response.body()!!.string())["suggestions"].array
                    .map {
                        SManga.create().apply {
                            val segment = it["data"].string
                            url = getUrlWithoutBaseUrl(itemUrl + segment)
                            title = it["value"].string

                            // Guess thumbnails
                            thumbnail_url = "$baseUrl/uploads/manga/$segment/cover/cover_250x350.jpg"
                        }
                    }, false)
        } else {
            internalMangaParse(response)
        }
    }
    override fun latestUpdatesParse(response: Response) = internalMangaParse(response)

    private fun internalMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return MangasPage(document.getElementsByClass("col-sm-6").map {
            SManga.create().apply {
                val urlElement = it.getElementsByClass("chart-title")
                url = getUrlWithoutBaseUrl(urlElement.attr("href"))
                title = urlElement.text().trim()
                thumbnail_url = it.select(".media-left img").attr("src")

                // Guess thumbnails on broken websites
                if (thumbnail_url?.isBlank() != false || thumbnail_url?.endsWith("no-image.png") != false) {
                    thumbnail_url = "$baseUrl/uploads/manga/${url.substringAfterLast('/')}/cover/cover_250x350.jpg"
                }
            }
        }, document.select(".pagination a[rel=next]").isNotEmpty())
    }

    private fun getUrlWithoutBaseUrl(newUrl: String): String {
        val parsedNewUrl = Uri.parse(newUrl)
        val newPathSegments = parsedNewUrl.pathSegments.toMutableList()

        for(i in parsedBaseUrl.pathSegments) {
            if(i.trim().equals(newPathSegments.first(), true)) {
                newPathSegments.removeAt(0)
            } else break
        }

        val builtUrl = parsedNewUrl.buildUpon().path("/")
        newPathSegments.forEach { builtUrl.appendPath(it) }

        var out = builtUrl.build().encodedPath
        if (parsedNewUrl.encodedQuery != null)
            out += "?" + parsedNewUrl.encodedQuery
        if (parsedNewUrl.encodedFragment != null)
            out += "#" + parsedNewUrl.encodedFragment

        return out
    }

    override fun mangaDetailsParse(response:Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.getElementsByClass("widget-title").text().trim()
        thumbnail_url = document.select(".row .img-responsive").attr("src")
        description = document.select(".row .well p").text().trim()

        var cur: String? = null
        for(element in document.select(".row .dl-horizontal").select("dt,dd")) {
            when(element.tagName()) {
                "dt" -> cur = element.text().trim().toLowerCase()
                "dd" -> when(cur) {
                    "author(s)",
                    "autor(es)",
                    "auteur(s)",
                    "著作",
                    "yazar(lar)",
                    "mangaka(lar)",
                    "pengarang/penulis",
                    "pengarang",
                    "penulis",
                    "autor",
                    "المؤلف",
                    "перевод" -> author = element.text()

                    "artist(s)",
                    "artiste(s)",
                    "sanatçi(lar)",
                    "artista(s)",
                    "artist(s)/ilustrator",
                    "الرسام",
                    "seniman" -> artist = element.text()

                    "categories",
                    "categorías",
                    "catégories",
                    "ジャンル",
                    "kategoriler",
                    "categorias",
                    "kategorie",
                    "التصنيفات",
                    "жанр",
                    "kategori" -> genre = element.getElementsByTag("a").joinToString {
                        it.text().trim()
                    }

                    "status",
                    "statut",
                    "estado",
                    "状態",
                    "durum",
                    "الحالة",
                    "статус" -> status = when(element.text().trim().toLowerCase()) {
                        "complete",
                        "مكتملة",
                        "complet" -> SManga.COMPLETED
                        "ongoing",
                        "مستمرة",
                        "en cours" -> SManga.ONGOING
                        else -> SManga.UNKNOWN
                    }
                }
            }
        }
    }

    /**
     * Parses the response from the site and returns a list of chapters.
     *
     * Overriden to allow for null chapters
     *
     * @param response the response from the site.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).mapNotNull { nullableChapterFromElement(it) }
    }

    /**
     * Returns the Jsoup selector that returns a list of [Element] corresponding to each chapter.
     */
    fun chapterListSelector() = ".chapters > li:not(.btn)"

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    private fun nullableChapterFromElement(element: Element): SChapter? {
        val titleWrapper = element.getElementsByClass("chapter-title-rtl").first()
        val url = titleWrapper.getElementsByTag("a").attr("href")

        // Ensure chapter actually links to a manga
        // Some websites use the chapters box to link to post announcements
        if (!Uri.parse(url).pathSegments.firstOrNull().equals(itemUrlPath, true)) {
            return null
        }

        val chapter = SChapter.create()

        chapter.url = getUrlWithoutBaseUrl(url)
        chapter.name = titleWrapper.text()

        // Parse date
        val dateText = element.getElementsByClass("date-chapter-title-rtl").text().trim()
        val formattedDate = try {
            DATE_FORMAT.parse(dateText).time
        } catch (e: ParseException) {
            0L
        }
        chapter.date_upload = formattedDate

        return chapter
    }

    override fun pageListParse(response: Response)
            = response.asJsoup().select("#all > .img-responsive")
            .mapIndexed { i, e ->
                val url = e.attr("data-src").trim()
                Page(i, url, url)
            }

    override fun imageUrlParse(response: Response)
            = throw UnsupportedOperationException("Unused method called!")

    private fun getInitialFilterList() = listOf<Filter<*>>(
            Filter.Header("NOTE: Ignored if using text search!"),
            Filter.Separator(),
            AuthorFilter(),
            UriSelectFilter("Category",
                    "cat",
                    arrayOf("" to "Any",
                            *categoryMappings.toTypedArray()
                    )
            ),
            UriSelectFilter("Begins with",
                    "alpha",
                    arrayOf("" to "Any",
                            *"#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray().map {
                                Pair(it.toString(), it.toString())
                            }.toTypedArray()
                    )
            ),
            UriSelectFilter("Sort by",
                    "sortBy",
                    arrayOf(
                            "name" to "Name",
                            "views" to "Popularity",
                            "last_release" to "Last update"
                    ), false),
            UriSelectFilter("Sort direction",
                    "asc",
                    arrayOf(
                            "true" to "Ascending",
                            "false" to "Descending"
                    ), false)
    )

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = FilterList(
            if(tagMappings != null)
                (getInitialFilterList() + UriSelectFilter("Tag",
                        "tag",
                        arrayOf("" to "Any",
				*tagMappings.toTypedArray()
			)))
            else getInitialFilterList()
    )

    /**
     * Class that creates a select filter. Each entry in the dropdown has a name and a display name.
     * If an entry is selected it is appended as a query parameter onto the end of the URI.
     * If `firstIsUnspecified` is set to true, if the first entry is selected, nothing will be appended on the the URI.
     */
    //vals: <name, display>
    open class UriSelectFilter(displayName: String, val uriParam: String, val vals: Array<Pair<String, String>>,
                               val firstIsUnspecified: Boolean = true,
                               defaultValue: Int = 0) :
            Filter.Select<String>(displayName, vals.map { it.second }.toTypedArray(), defaultValue), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            if (state != 0 || !firstIsUnspecified)
                uri.appendQueryParameter(uriParam, vals[state].first)
        }
    }

    class AuthorFilter: Filter.Text("Author"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("author", state)
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("d MMM. yyyy", Locale.US)
    }
}
