package eu.kanade.tachiyomi.extension.all.ehentai

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Element
import rx.Observable
import java.net.URLEncoder

open class EHentai(override val lang: String, private val ehLang: String) : HttpSource() {

    override val name = "E-Hentai"

    override val baseUrl = "https://e-hentai.org"

    override val supportsLatest = true

    private fun genericMangaParse(response: Response): MangasPage {
        val doc = response.asJsoup()
        val parsedMangas = doc.select("table.itg td.glname").map {
            SManga.create().apply {
                //Get title
                it.select("a")?.first()?.apply {
                    title = this.select(".glink").text()
                    url = ExGalleryMetadata.normalizeUrl(attr("href"))
                }
                //Get image
                it.parent().select(".glthumb img")?.first().apply {
                    thumbnail_url = this?.attr("data-src")?.nullIfBlank()
                            ?: this?.attr("src")
                }
            }
        }

        //Add to page if required
        val hasNextPage = doc.select("a[onclick=return false]").last()?.text() == ">"

        return MangasPage(parsedMangas, hasNextPage)
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.just(listOf(SChapter.create().apply {
        url = manga.url
        name = "Chapter"
        chapter_number = 1f
    }))

    override fun fetchPageList(chapter: SChapter) = fetchChapterPage(chapter, "$baseUrl/${chapter.url}").map {
        it.mapIndexed { i, s ->
            Page(i, s)
        }
    }!!

    /**
     * Recursively fetch chapter pages
     */
    private fun fetchChapterPage(chapter: SChapter, np: String,
                                 pastUrls: List<String> = emptyList()): Observable<List<String>> {
        val urls = ArrayList(pastUrls)
        return chapterPageCall(np).flatMap {
            val jsoup = it.asJsoup()
            urls += parseChapterPage(jsoup)
            nextPageUrl(jsoup)?.let {
                fetchChapterPage(chapter, it, urls)
            } ?: Observable.just(urls)
        }
    }

    private fun parseChapterPage(response: Element) = with(response) {
        select(".gdtm a").map {
            Pair(it.child(0).attr("alt").toInt(), it.attr("href"))
        }.sortedBy(Pair<Int, String>::first).map { it.second }
    }

    private fun chapterPageCall(np: String) = client.newCall(chapterPageRequest(np)).asObservableSuccess()
    private fun chapterPageRequest(np: String) = exGet(np, null, headers)

    private fun nextPageUrl(element: Element) = element.select("a[onclick=return false]").last()?.let {
        if (it.text() == ">") it.attr("href") else null
    }

    override fun popularMangaRequest(page: Int) = latestUpdatesRequest(page)
    //This source supports finding popular manga but will not respect language filters on the popular manga page!
    //We currently display the latest updates instead until this is fixed
    //override fun popularMangaRequest(page: Int) = exGet("$baseUrl/toplist.php?tl=15", page)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl$QUERY_PREFIX").buildUpon()
        uri.appendQueryParameter("f_search", query)
        filters.forEach {
            if (it is UriFilter) it.addToUri(uri)
        }
        return exGet(uri.toString(), page)
    }

    override fun latestUpdatesRequest(page: Int) = exGet(baseUrl, page)

    override fun popularMangaParse(response: Response) = genericMangaParse(response)
    override fun searchMangaParse(response: Response) = genericMangaParse(response)
    override fun latestUpdatesParse(response: Response) = genericMangaParse(response)

    private fun exGet(url: String, page: Int? = null, additionalHeaders: Headers? = null, cache: Boolean = true) = GET(page?.let {
        addParam(url, "page", (it - 1).toString())
    } ?: url, additionalHeaders?.let {
        val headers = headers.newBuilder()
        it.toMultimap().forEach { (t, u) ->
            u.forEach {
                headers.add(t, it)
            }
        }
        headers.build()
    } ?: headers).let {
        if (!cache)
            it.newBuilder().cacheControl(CacheControl.FORCE_NETWORK).build()
        else
            it
    }!!

    /**
     * Parse gallery page to metadata model
     */
    override fun mangaDetailsParse(response: Response) = with(response.asJsoup()) {
        with(ExGalleryMetadata()) {
            url = response.request().url().encodedPath()
            title = select("#gn").text().nullIfBlank()?.trim()

            altTitle = select("#gj").text().nullIfBlank()?.trim()

            //Thumbnail is set as background of element in style attribute
            thumbnailUrl = select("#gd1 div").attr("style").nullIfBlank()?.let {
                it.substring(it.indexOf('(') + 1 until it.lastIndexOf(')'))
            }
            genre = select("#gdc div").text().nullIfBlank()?.trim()?.toLowerCase()

            uploader = select("#gdn").text().nullIfBlank()?.trim()

            //Parse the table
            select("#gdd tr").forEach {
                it.select(".gdt1")
                        .text()
                        .nullIfBlank()
                        ?.trim()
                        ?.let { left ->
                            it.select(".gdt2")
                                    .text()
                                    .nullIfBlank()
                                    ?.trim()
                                    ?.let { right ->
                                        ignore {
                                            when (left.removeSuffix(":")
                                                    .toLowerCase()) {
                                                "posted" -> datePosted = EX_DATE_FORMAT.parse(right).time
                                                "visible" -> visible = right.nullIfBlank()
                                                "language" -> {
                                                    language = right.removeSuffix(TR_SUFFIX).trim().nullIfBlank()
                                                    translated = right.endsWith(TR_SUFFIX, true)
                                                }
                                                "file size" -> size = parseHumanReadableByteCount(right)?.toLong()
                                                "length" -> length = right.removeSuffix("pages").trim().nullIfBlank()?.toInt()
                                                "favorited" -> favorites = right.removeSuffix("times").trim().nullIfBlank()?.toInt()
                                            }
                                        }
                                    }
                        }
            }

            //Parse ratings
            ignore {
                averageRating = select("#rating_label")
                        .text()
                        .removePrefix("Average:")
                        .trim()
                        .nullIfBlank()
                        ?.toDouble()
                ratingCount = select("#rating_count")
                        .text()
                        .trim()
                        .nullIfBlank()
                        ?.toInt()
            }

            //Parse tags
            tags.clear()
            select("#taglist tr").forEach {
                val namespace = it.select(".tc").text().removeSuffix(":")
                val currentTags = it.select("div").map {
                    Tag(it.text().trim(),
                            it.hasClass("gtl"))
                }
                tags[namespace] = currentTags
            }

            //Copy metadata to manga
            SManga.create().apply {
                copyTo(this)
            }
        }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun pageListParse(response: Response) = throw UnsupportedOperationException("Unused method was called somehow!")

    override fun fetchImageUrl(page: Page) = client.newCall(imageUrlRequest(page))
            .asObservableSuccess()
            .map { realImageUrlParse(it, page) }!!

    private fun realImageUrlParse(response: Response, page: Page) = with(response.asJsoup()) {
        val currentImage = getElementById("img").attr("src")
        //TODO We cannot currently do this as page.url is immutable
        //Each press of the retry button will choose another server
        /*select("#loadfail").attr("onclick").nullIfBlank()?.let {
            page.url = addParam(page.url, "nl", it.substring(it.indexOf('\'') + 1 until it.lastIndexOf('\'')))
        }*/
        currentImage
    }!!

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Unused method was called somehow!")

    private val cookiesHeader by lazy {
        val cookies = mutableMapOf<String, String>()

        //Setup settings
        val settings = mutableListOf<String>()

        //Do not show popular right now pane as we can't parse it
        settings += "prn_n"

        //Exclude every other language except the one we have selected
        settings += "xl_" + languageMappings.filter { it.first != ehLang }
                .flatMap { it.second }
                .joinToString("x")

        cookies["uconfig"] = buildSettings(settings)

        // Bypass "Offensive For Everyone" content warning
        cookies["nw"] = "1"

        buildCookies(cookies)
    }

    //Headers
    override fun headersBuilder() = super.headersBuilder().add("Cookie", cookiesHeader)!!

    private fun buildSettings(settings: List<String?>) = settings.filterNotNull().joinToString(separator = "-")

    private fun buildCookies(cookies: Map<String, String>) = cookies.entries.joinToString(separator = "; ", postfix = ";") {
        "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
    }

    private fun addParam(url: String, param: String, value: String) = Uri.parse(url)
            .buildUpon()
            .appendQueryParameter(param, value)
            .toString()

    override val client = network.client.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor { chain ->
                val newReq = chain
                        .request()
                        .newBuilder()
                        .removeHeader("Cookie")
                        .addHeader("Cookie", cookiesHeader)
                        .build()

                chain.proceed(newReq)
            }.build()!!

    //Filters
    override fun getFilterList() = FilterList(
            GenreGroup(),
            AdvancedGroup()
    )

    class GenreOption(name: String, private val genreId: String) : Filter.CheckBox(name, false), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            builder.appendQueryParameter("f_$genreId", if (state) "1" else "0")
        }
    }

    class GenreGroup : UriGroup<GenreOption>("Genres", listOf(
            GenreOption("Dōjinshi", "doujinshi"),
            GenreOption("Manga", "manga"),
            GenreOption("Artist CG", "artistcg"),
            GenreOption("Game CG", "gamecg"),
            GenreOption("Western", "western"),
            GenreOption("Non-H", "non-h"),
            GenreOption("Image Set", "imageset"),
            GenreOption("Cosplay", "cosplay"),
            GenreOption("Asian Porn", "asianporn"),
            GenreOption("Misc", "misc")
    ))

    class AdvancedOption(name: String, private val param: String, defValue: Boolean = false) : Filter.CheckBox(name, defValue), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state)
                builder.appendQueryParameter(param, "on")
        }
    }

    class RatingOption : Filter.Select<String>("Minimum Rating", arrayOf(
            "Any",
            "2 stars",
            "3 stars",
            "4 stars",
            "5 stars"
    )), UriFilter {
        override fun addToUri(builder: Uri.Builder) {
            if (state > 0) builder.appendQueryParameter("f_srdd", (state + 1).toString())
        }
    }

    //Explicit type arg for listOf() to workaround this: KT-16570
    class AdvancedGroup : UriGroup<Filter<*>>("Advanced Options", listOf<Filter<*>>(
            AdvancedOption("Search Gallery Name", "f_sname", true),
            AdvancedOption("Search Gallery Tags", "f_stags", true),
            AdvancedOption("Search Gallery Description", "f_sdesc"),
            AdvancedOption("Search Torrent Filenames", "f_storr"),
            AdvancedOption("Only Show Galleries With Torrents", "f_sto"),
            AdvancedOption("Search Low-Power Tags", "f_sdt1"),
            AdvancedOption("Search Downvoted Tags", "f_sdt2"),
            AdvancedOption("Show Expunged Galleries", "f_sh"),
            RatingOption()
    ))

    //map languages to their internal ids
    private val languageMappings = listOf(
            Pair("japanese", listOf("0", "1024", "2048")),
            Pair("english", listOf("1", "1025", "2049")),
            Pair("chinese", listOf("10", "1034", "2058")),
            Pair("dutch", listOf("20", "1044", "2068")),
            Pair("french", listOf("30", "1054", "2078")),
            Pair("german", listOf("40", "1064", "2088")),
            Pair("hungarian", listOf("50", "1074", "2098")),
            Pair("italian", listOf("60", "1084", "2108")),
            Pair("korean", listOf("70", "1094", "2118")),
            Pair("polish", listOf("80", "1104", "2128")),
            Pair("portuguese", listOf("90", "1114", "2138")),
            Pair("russian", listOf("100", "1124", "2148")),
            Pair("spanish", listOf("110", "1134", "2158")),
            Pair("thai", listOf("120", "1144", "2168")),
            Pair("vietnamese", listOf("130", "1154", "2178")),
            Pair("n/a", listOf("254", "1278", "2302")),
            Pair("other", listOf("255", "1279", "2303"))
    )

    companion object {
        const val QUERY_PREFIX = "?f_apply=Apply+Filter"
        const val TR_SUFFIX = "TR"
    }
}
