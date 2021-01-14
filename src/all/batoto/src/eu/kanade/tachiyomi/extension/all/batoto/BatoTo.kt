package eu.kanade.tachiyomi.extension.all.batoto

import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Calendar
import java.util.concurrent.TimeUnit

open class BatoTo(
    override val lang: String,
    private val siteLang: String
) : ParsedHttpSource() {

    override val name: String = "Bato.to"
    override val baseUrl: String = "https://bato.to"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/browse?langs=$siteLang&sort=update&page=$page")
    }

    override fun latestUpdatesSelector() = "div#series-list div.col"

    override fun latestUpdatesFromElement(element: Element): SManga {
        val manga = SManga.create()
        val item = element.select("a.item-cover")
        val imgurl = item.select("img").attr("abs:src")
        manga.setUrlWithoutDomain(item.attr("href"))
        manga.title = element.select("a.item-title").text()
        manga.thumbnail_url = imgurl
        return manga
    }

    override fun latestUpdatesNextPageSelector() = "div#mainer .pagination .page-item:not(.disabled) a.page-link:contains(»)"

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/browse?langs=$siteLang&sort=views_w&page=$page")
    }

    override fun popularMangaSelector() = latestUpdatesSelector()

    override fun popularMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun popularMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search?word=$query&page=$page")
        } else {
            var author: String? = null
            val url = HttpUrl.parse("$baseUrl/browse")!!.newBuilder()
            url.addQueryParameter("page", page.toString())
            url.addQueryParameter("langs", siteLang)
            filters.forEach { filter ->
                when (filter) {
                    is OriginFilter -> {
                        val originToInclude = mutableListOf<String>()
                        filter.state.forEach { content ->
                            if (content.state) {
                                originToInclude.add(content.name)
                            }
                        }
                        if (originToInclude.isNotEmpty()) {
                            url.addQueryParameter(
                                "origs",
                                originToInclude
                                    .joinToString(",")
                            )
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state != 0) {
                            url.addQueryParameter("release", filter.toUriPart())
                        }
                    }
                    is GenreFilter -> {
                        val genreToInclude = mutableListOf<String>()
                        filter.state.forEach { content ->
                            if (content.state) {
                                genreToInclude.add(content.name)
                            }
                        }
                        if (genreToInclude.isNotEmpty()) {
                            url.addQueryParameter(
                                "genres",
                                genreToInclude
                                    .joinToString(",")
                            )
                        }
                    }
                    is ChapterFilter -> {
                        if (filter.state != 0) {
                            url.addQueryParameter("chapters", filter.toUriPart())
                        }
                    }
                    is SortBy -> {
                        if (filter.state != 0) {
                            url.addQueryParameter("sort", filter.toUriPart())
                        }
                    }
                }
            }
            GET(url.build().toString(), headers)
        }
    }

    override fun searchMangaSelector() = latestUpdatesSelector()

    override fun searchMangaFromElement(element: Element) = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector() = latestUpdatesNextPageSelector()

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.mangaDetailsRequest(manga)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div#mainer div.container-fluid")
        val manga = SManga.create()
        val genres = mutableListOf<String>()
        val status = infoElement.select("div.attr-item:contains(status) span").text()
        infoElement.select("div.attr-item:contains(genres) span").text().split(
            " / "
                .toRegex()
        ).forEach { element ->
            genres.add(element)
        }
        manga.title = infoElement.select("h3").text()
        manga.author = infoElement.select("div.attr-item:contains(author) a:first-child").text()
        manga.artist = infoElement.select("div.attr-item:contains(author) a:last-child").text()
        manga.status = parseStatus(status)
        manga.genre = infoElement.select(".attr-item b:contains(genres) + span ").joinToString { it.text() }
        manga.description = infoElement.select("h5:contains(summary) + pre").text()
        manga.thumbnail_url = document.select("div.attr-cover img")
            .attr("abs:src")
        return manga
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("http")) {
            return GET(manga.url, headers)
        }
        return super.chapterListRequest(manga)
    }

    override fun chapterListSelector() = "div.main div.p-2"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val urlElement = element.select("a.chapt")
        val time = element.select("i.pl-3").text()
            .replace("a ", "1 ")
            .replace("an ", "1 ")
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        if (time != "") {
            chapter.date_upload = parseChapterDate(time)
        }
        return chapter
    }

    private fun parseChapterDate(date: String): Long {
        val value = date.split(' ')[0].toInt()

        return when {
            "mins" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hours" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "days" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "weeks" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "months" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "years" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            "min" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hour" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "day" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "week" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "month" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "year" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.startsWith("http")) {
            return GET(chapter.url, headers)
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        val script = document.select("script").html()

        if (script.contains("var images =")) {
            val imgJson = JSONObject(script.substringAfter("var images = ").substringBefore(";"))
            val imgNames = imgJson.names()

            if (imgNames != null) {
                for (i in 0 until imgNames.length()) {
                    val imgKey = imgNames.getString(i)
                    val imgUrl = imgJson.getString(imgKey)
                    pages.add(Page(i, "", imgUrl))
                }
            }
        } else if (script.contains("const server =")) { // bato.to
            val duktape = Duktape.create()
            val encryptedServer = script.substringAfter("const server = ").substringBefore(";")
            val batojs = duktape.evaluate(script.substringAfter("const batojs = ").substringBefore(";")).toString()
            val decryptScript = cryptoJS + "CryptoJS.AES.decrypt($encryptedServer, \"$batojs\").toString(CryptoJS.enc.Utf8);"
            val server = duktape.evaluate(decryptScript).toString().replace("\"", "")
            duktape.close()

            val imgArray = JSONArray(script.substringAfter("const images = ").substringBefore(";"))
            if (imgArray != null) {
                if (script.contains("bato.to/images")) {
                    for (i in 0 until imgArray.length()) {
                        val imgUrl = imgArray.get(i)
                        pages.add(Page(i, "", "$imgUrl"))
                    }
                } else {
                    for (i in 0 until imgArray.length()) {
                        val imgUrl = imgArray.get(i)
                        if (server.startsWith("http"))
                            pages.add(Page(i, "", "${server}$imgUrl"))
                        else
                            pages.add(Page(i, "", "https:${server}$imgUrl"))
                    }
                }
            }
        }

        return pages
    }

    private val cryptoJS by lazy {
        client.newCall(
            GET(
                "https://cdnjs.cloudflare.com/ajax/libs/crypto-js/4.0.0/crypto-js.min.js",
                headers
            )
        ).execute().body()!!.string()
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")

    private class OriginFilter(genres: List<Tag>) : Filter.Group<Tag>("Origin", genres)
    private class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)


    private class ChapterFilter : UriPartFilter(
        "Chapters",
        arrayOf(
            Pair("<select>", ""),
            Pair("1 ~ 9", "1-9"),
            Pair("10 ~ 29", "10-29"),
            Pair("30 ~ 99", "30-99"),
            Pair("100 ~ 199", "100-199"),
            Pair("200+", "200"),
            Pair("100+", "100"),
            Pair("50+", "50"),
            Pair("10+", "10"),
            Pair("1+", "1")
        )
    )

    private class SortBy : UriPartFilter(
        "Sorts By",
        arrayOf(
            Pair("<select>", ""),
            Pair("A-Z", "title.az"),
            Pair("Z-A", "title"),
            Pair("Last Updated", "update"),
            Pair("Oldest Updated", "updated.az"),
            Pair("Newest Added", "create"),
            Pair("Oldest Added", "create.az"),
            Pair("Most Views Totally", "views_a"),
            Pair("Most Views 365 days", "views_y"),
            Pair("Most Views 30 days", "views_m"),
            Pair("Most Views 7 days", "views_w"),
            Pair("Most Views 24 hours", "views_d"),
            Pair("Most Views 60 minutes", "views_h"),
            Pair("Least Views Totally", "views_a.az"),
            Pair("Least Views 365 days", "views_y.az"),
            Pair("Least Views 30 days", "views_m.az"),
            Pair("Least Views 7 days", "views_w.az"),
            Pair("Least Views 24 hours", "views_d.az"),
            Pair("Least Views 60 minutes", "views_h.az")
        )
    )

    private class StatusFilter : UriPartFilter(
        "Status",
        arrayOf(
            Pair("<select>", ""),
            Pair("Pending", "pending"),
            Pair("Ongoing", "ongoing"),
            Pair("Completed", "completed"),
            Pair("Hiatus", "hiatus"),
            Pair("Cancelled", "cancelled")
        )
    )

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        ChapterFilter(),
        SortBy(),
        StatusFilter(),
        OriginFilter(getOriginList()),
        GenreFilter(getGenreList())
    )

    private fun getOriginList() = listOf(
        Tag("my"),
        Tag("ceb"),
        Tag("zh"),
        Tag("zh_hk"),
        Tag("en"),
        Tag("en_us"),
        Tag("fil"),
        Tag("id"),
        Tag("it"),
        Tag("ja"),
        Tag("ko"),
        Tag("ms"),
        Tag("pt_br"),
        Tag("th"),
        Tag("vi")
    )

    private fun getGenreList() = listOf(
        Tag("Artbook"),
        Tag("Cartoon"),
        Tag("Comic"),
        Tag("Doujinshi"),
        Tag("Imageset"),
        Tag("Manga"),
        Tag("Manhua"),
        Tag("Manhwa"),
        Tag("Webtoon"),
        Tag("Western"),
        Tag("Josei"),
        Tag("Seinen"),
        Tag("Shoujo"),
        Tag("Shoujo_Ai"),
        Tag("Shounen"),
        Tag("Shounen_Ai"),
        Tag("Yaoi"),
        Tag("Yuri"),
        Tag("Ecchi"),
        Tag("Mature"),
        Tag("Adult"),
        Tag("Gore"),
        Tag("Violence"),
        Tag("Smut"),
        Tag("Hentai"),
        Tag("4_Koma"),
        Tag("Action"),
        Tag("Adaptation"),
        Tag("Adventure"),
        Tag("Aliens"),
        Tag("Animals"),
        Tag("Anthology"),
        Tag("Comedy"),
        Tag("Cooking"),
        Tag("Crime"),
        Tag("Crossdressing"),
        Tag("Delinquents"),
        Tag("Dementia"),
        Tag("Demons"),
        Tag("Drama"),
        Tag("Fantasy"),
        Tag("Fan_Colored"),
        Tag("Full_Color"),
        Tag("Game"),
        Tag("Gender_Bender"),
        Tag("Genderswap"),
        Tag("Ghosts"),
        Tag("Gyaru"),
        Tag("Harem"),
        Tag("Halequin"),
        Tag("Historical"),
        Tag("Horror"),
        Tag("Incest"),
        Tag("Isekai"),
        Tag("Kids"),
        Tag("Loli"),
        Tag("Lolicon"),
        Tag("Magic"),
        Tag("Magical_Girls"),
        Tag("Martial_Arts"),
        Tag("Mecha"),
        Tag("Medical"),
        Tag("Military"),
        Tag("Monster_Girls"),
        Tag("Monsters"),
        Tag("Music"),
        Tag("Mystery"),
        Tag("Netorare"),
        Tag("Ninja"),
        Tag("Office_Workers"),
        Tag("Oneshot"),
        Tag("Parody"),
        Tag("Philosophical"),
        Tag("Police"),
        Tag("Post_Apocalyptic"),
        Tag("Psychological"),
        Tag("Reincarnation"),
        Tag("Reverse_Harem"),
        Tag("Romance"),
        Tag("Samurai"),
        Tag("School_Life"),
        Tag("Sci_Fi"),
        Tag("Shota"),
        Tag("Shotacon"),
        Tag("Slice_Of_Life"),
        Tag("SM_BDSM"),
        Tag("Space"),
        Tag("Sports"),
        Tag("Super_Power"),
        Tag("Superhero"),
        Tag("Supernatural"),
        Tag("Survival"),
        Tag("Thriller"),
        Tag("Time_Travel"),
        Tag("Tragedy"),
        Tag("Vampires"),
        Tag("Video_Games"),
        Tag("Virtual_Reality"),
        Tag("Wuxia"),
        Tag("Xianxia"),
        Tag("Xuanhuan"),
        Tag("Zombies"),
        Tag("award_winning"),
        Tag("youkai"),
        Tag("uncategorized")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(name: String) : Filter.CheckBox(name)
}
