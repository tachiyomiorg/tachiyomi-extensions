package eu.kanade.tachiyomi.extension.fr.mangakawaii

import android.net.Uri
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


class MangaKawaiiSource : HttpSource() {

    override val lang = "fr"
    override val name = "Mangakawaii"
    override val baseUrl="https://www.mangakawaii.to"
    override val supportsLatest = false
    private val itemUrl= "https://www.mangakawaii.to/manga/"

    private val jsonParser = JsonParser()
    private val itemUrlPath = Uri.parse(itemUrl).pathSegments.first()
    private val parsedBaseUrl = Uri.parse(baseUrl)

    private val categoriesJson = """{"categories":[{"id":"1","name":"Action"},{"id":"2","name":"Aventure"},{"id":"3","name":"Comédie"},{"id":"5","name":"Drame"},{"id":"7","name":"Fantastique"},{"id":"8","name":"Gender Bender"},{"id":"9","name":"Harem"},{"id":"10","name":"Historique"},{"id":"11","name":"Horreur"},{"id":"12","name":"Josei"},{"id":"13","name":"Arts Martiaux"},{"id":"14","name":"Mature"},{"id":"15","name":"Mecha"},{"id":"16","name":"Mystère"},{"id":"17","name":"One Shot"},{"id":"18","name":"Psychologique"},{"id":"19","name":"Romance"},{"id":"20","name":"Vie Scolaire"},{"id":"21","name":"Sci-fi"},{"id":"22","name":"Seinen"},{"id":"23","name":"Shojo"},{"id":"24","name":"Shojo Ai"},{"id":"25","name":"Shonen"},{"id":"26","name":"Shonen Ai"},{"id":"27","name":"Tranche de vie"},{"id":"28","name":"Sports"},{"id":"29","name":"Surnaturel"},{"id":"30","name":"Adulte"},{"id":"31","name":"Yaoi"},{"id":"32","name":"Yuri"},{"id":"33","name":"Webtoon"},{"id":"35","name":"Ecchi"},{"id":"36","name":"Doujin"}]}"""
    private val jsonCategories = jsonParser.parse(categoriesJson) as JsonObject
    private val categoryMappings = mapToPairs(jsonCategories["categories"].array)

    override val client: OkHttpClient = network.cloudflareClient

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/filterLists?page=$page&sortBy=views&asc=false")
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        //Query overrides everything
        val url: Uri.Builder
        if (query.isNotBlank()) {
            url = Uri.parse("$baseUrl/recherche")!!.buildUpon()
            url.appendQueryParameter("query", query)
        } else {
            url = Uri.parse("$baseUrl/filterLists?page=$page")!!.buildUpon()
            filters.filterIsInstance<UriFilter>()
                    .forEach { it.addToUri(url) }
        }
        return GET(url.toString())
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/filterLists?page=$page&sortBy=last_release&asc=false")

    override fun popularMangaParse(response: Response) = internalMangaParse(response)
    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request().url().queryParameter("query")?.isNotBlank() == true) {
            //If a search query was specified, use search instead!
            MangasPage(jsonParser
                    .parse(response.body()!!.string())["suggestions"].array
                    .map {
                        SManga.create().apply {
                            val segment = it["data"].string
                            url = getUrlWithoutBaseUrl(itemUrl + segment)
                            title = it["value"].string

                            // Guess thumbnails
                            // thumbnail_url = "$baseUrl/uploads/manga/$segment/cover/cover_250x350.jpg"
                        }
                    }, false)
        } else {
            internalMangaParse(response)
        }
    }

    override fun latestUpdatesParse(response: Response) = internalMangaParse(response)

    private fun internalMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        return MangasPage(document.select("div[class^=col-sm]").map {
            SManga.create().apply {
                val urlElement = it.getElementsByClass("infobubble")
                    url = getUrlWithoutBaseUrl(urlElement.attr("href"))
                    title = it.select("p.infotitle").text().trim()


                val cover = it.select(".media-left img").attr("data-src")
                thumbnail_url =
                        if (cover.isEmpty()) {
                            coverGuess(it.select("img").attr("data-src"), url)
                        } else {
                            coverGuess(cover, url)
                        }
            }
        }, document.select(".pagination a[rel=next]").isNotEmpty())
    }

    // Guess thumbnails on broken websites

    private fun coverGuess(url: String?, mangaUrl: String): String {
        // Guess thumbnails on broken websites
        if (url != null && url.isNotBlank()) {
            if (url.startsWith("//")) {
                return "$baseUrl/uploads/manga/${url.substringBeforeLast("/cover/").substringAfter("/manga/")}/cover/cover_250x350.jpg"
            }
            if (url.endsWith("no-image.png")) {
                return "$baseUrl/uploads/manga/${mangaUrl?.substringAfterLast('/')}/cover/cover_250x350.jpg"
            }
            return url
        }
        return ""
    }

    private fun getUrlWithoutBaseUrl(newUrl: String): String {
        val parsedNewUrl = Uri.parse(newUrl)
        val newPathSegments = parsedNewUrl.pathSegments.toMutableList()

        for (i in parsedBaseUrl.pathSegments) {
            if (i.trim().equals(newPathSegments.first(), true)) {
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

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select(".info-desc__content").text().trim()
        thumbnail_url = coverGuess(document.select(".manga__image .manga__cover").attr("src"), document.location())
        description = document.select(".info-desc__content").text().trim()

        var cur: String? = null
        for (element in document.select(".manga-info .info-list__row").select("strong,a,span")) {
            when (element.tagName()) {
                "strong" -> cur = element.text().trim().toLowerCase()
                "a","span" -> when (cur) {
                    "auteur(s)" -> author = element.text()
                    "artiste(s)" -> artist = element.text()
                    "categories" -> genre = element.getElementsByTag("a").joinToString {
                        it.text().trim()
                    }

                    "statut"-> status = when (element.text().trim().toLowerCase()) {
                        "terminé" -> SManga.COMPLETED
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
    fun chapterListSelector() = ".chapters-list > .chapter-item:not(.btn)"

    /**
     * Returns a chapter from the given element.
     *
     * @param element an element obtained from [chapterListSelector].
     */
    private fun nullableChapterFromElement(element: Element): SChapter? {
        val titleWrapper = element.getElementsByClass("list-item__title").first()
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
        val dateText = element.getElementsByClass("chapter-item__date").text().trim()
        val formattedDate = try {
            DATE_FORMAT.parse(dateText).time
        } catch (e: ParseException) {
            0L
        }
        chapter.date_upload = formattedDate

        return chapter
    }

    override fun pageListParse(response: Response) = response.asJsoup().select(".img-responsive:not(#ImgPage)")
            .mapIndexed { i, e ->
                var url = e.attr("data-src")

                if (url.isBlank()) {
                    url = e.attr("src")
                }

                url = url.trim()

                Page(i, url, url)
            }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused method called!")

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
            SortFilter()
    )

    /**
     * Returns the list of filters for the source.
     */
    override fun getFilterList() = FilterList(
            getInitialFilterList()
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

    class AuthorFilter : Filter.Text("Author"), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("author", state)
        }
    }

    class SortFilter : Filter.Sort("Sort",
            sortables.map { it.second }.toTypedArray(),
            Filter.Sort.Selection(0, true)), UriFilter {
        override fun addToUri(uri: Uri.Builder) {
            uri.appendQueryParameter("sortBy", sortables[state!!.index].first)
            uri.appendQueryParameter("asc", state!!.ascending.toString())
        }

        companion object {
            private val sortables = arrayOf(
                    "name" to "Name",
                    "views" to "Popularity",
                    "last_release" to "Last update"
            )
        }
    }

    /**
     * Represents a filter that is able to modify a URI.
     */
    interface UriFilter {
        fun addToUri(uri: Uri.Builder)
    }

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("DD.MM.yyyy", Locale.FRANCE)
    }

    /**
    * Map an array of JSON objects to pairs. Each JSON object must have
    * the following properties:
    *
    * id: first item in pair
    * name: second item in pair
    *
    * @param array The array to process
    * @return The new list of pairs
    */
    private fun mapToPairs(array: JsonArray): List<Pair<String, String>> = array.map {
        it as JsonObject

        it["id"].string to it["name"].string
    }
}
