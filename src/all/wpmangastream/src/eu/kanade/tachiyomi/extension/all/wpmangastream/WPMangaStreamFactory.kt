package eu.kanade.tachiyomi.extension.all.wpmangastream

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class WPMangaStreamFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Kiryuu(),
        KomikAV(),
        KomikStation(),
        KomikCast(),
        WestManga(),
        KomikGo(),
        KomikIndo(),
        LiebeSchneeHiver(),
        SekteKomik(),
        MangaSwat(),
        MangaRaw(),
        NonStopScans(),
        KomikTap(),
        Matakomik(),
        KomikindoCo(),
        ReadKomik(),
        MangaP(),
        MangaProZ(),
        Boosei(),
        Mangakyo(),
        AsuraScans(),
        SilenceScan(),
        SheaManga(),
        FlameScans(),
        GURUKomik(),
        Rawkuma(),
        KaisarKomik(),
        MasterKomik(),
        KomikRu(),
        MangaShiro(),
        ChiOtaku(),
        KlanKomik()
    )
}

class KlanKomik : WPMangaStream("KlanKomik", "https://klankomik.com", "id")

class ChiOtaku : WPMangaStream("ChiOtaku", "https://chiotaku.com", "id")

class LiebeSchneeHiver : WPMangaStream(
    "Liebe Schnee Hiver",
    "https://www.liebeschneehiver.com",
    "tr",
    SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("tr"))
)

class MangaShiro : WPMangaStream("MangaShiro", "https://mangashiro.co", "id")

class KomikRu : WPMangaStream("KomikRu", "https://komikru.com", "id", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")))

class MasterKomik : WPMangaStream("MasterKomik", "https://masterkomik.com", "id")

class KaisarKomik : WPMangaStream("Kaisar Komik", "https://kaisarkomik.com", "id")

class Rawkuma : WPMangaStream("Rawkuma", "https://rawkuma.com/", "ja")

class GURUKomik : WPMangaStream("GURU Komik", "https://gurukomik.com", "id", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id")))

class FlameScans : WPMangaStream("Flame Scans", "https://www.flame-scans.com", "en")

class SheaManga : WPMangaStream(
    "Shea Manga",
    "https://sheamanga.my.id",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))
)

class AsuraScans : WPMangaStream("AsuraScans", "https://asurascans.com", "en") {
    override val pageSelector = "div#readerarea img[lazy]"
}

class SekteKomik : WPMangaStream("Sekte Komik (WP Manga Stream)", "https://sektekomik.com", "id")

class Kiryuu : WPMangaStream("Kiryuu (WP Manga Stream)", "https://kiryuu.co", "id") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img").map { it.attr("abs:src") }
            .filterNot { it.substringAfterLast("/").contains(Regex("""(filerun|photothumb\.db)""")) }
            .mapIndexed { i, image -> Page(i, "", image) }
    }
}

class KomikAV : WPMangaStream(
    "Komik AV (WP Manga Stream)",
    "https://komikav.com",
    "id",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("id"))
) {
    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!, headers)
    }
}

class KomikStation : WPMangaStream("Komik Station (WP Manga Stream)", "https://komikstation.com", "id")

class KomikCast : WPMangaStream("Komik Cast (WP Manga Stream)", "https://komikcast.com", "id") {
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/daftar-komik/page/$page/?order=popular", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/komik/page/$page/", headers)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotBlank()) {
            val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
            val pattern = "\\s+".toRegex()
            val q = query.replace(pattern, "+")
            if (query.isNotEmpty()) {
                url.addQueryParameter("s", q)
            } else {
                url.addQueryParameter("s", "")
            }
            url.toString()
        } else {
            val url = HttpUrl.parse("$baseUrl/daftar-komik/page/$page")!!.newBuilder()
            var orderBy: String
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is StatusFilter -> url.addQueryParameter("status", arrayOf("", "ongoing", "completed")[filter.state])
                    is GenreListFilter -> {
                        val genreInclude = mutableListOf<String>()
                        filter.state.forEach {
                            if (it.state == 1) {
                                genreInclude.add(it.id)
                            }
                        }
                        if (genreInclude.isNotEmpty()) {
                            genreInclude.forEach { genre ->
                                url.addQueryParameter("genre[]", genre)
                            }
                        }
                    }
                    is SortByFilter -> {
                        orderBy = filter.toUriPart()
                        url.addQueryParameter("order", orderBy)
                    }
                }
            }
            url.toString()
        }
        return GET(url, headers)
    }

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.limit img").attr("src")
        element.select("div.bigor > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea img.size-full")
            .mapIndexed { i, img -> Page(i, "", img.attr("abs:Src")) }
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        SortByFilter(),
        Filter.Separator(),
        StatusFilter(),
        Filter.Separator(),
        GenreListFilter(getGenreList())
    )
}

class WestManga : WPMangaStream("West Manga (WP Manga Stream)", "https://westmanga.info", "id") {
    override fun getGenreList(): List<Genre> = listOf(
        Genre("4 Koma", "344"),
        Genre("Action", "13"),
        Genre("Adventure", "4"),
        Genre("Anthology", "1494"),
        Genre("Comedy", "5"),
        Genre("Cooking", "54"),
        Genre("Crime", "856"),
        Genre("Crossdressing", "1306"),
        Genre("Demon", "64"),
        Genre("Drama", "6"),
        Genre("Ecchi", "14"),
        Genre("Fantasy", "7"),
        Genre("Game", "36"),
        Genre("Gender Bender", "149"),
        Genre("Genderswap", "157"),
        Genre("Gore", "56"),
        Genre("Gyaru", "812"),
        Genre("Harem", "17"),
        Genre("Historical", "44"),
        Genre("Horror", "211"),
        Genre("Isekai", "20"),
        Genre("Isekai Action", "742"),
        Genre("Josei", "164"),
        Genre("Magic", "65"),
        Genre("Manga", "268"),
        Genre("Manhua", "32"),
        Genre("Martial Art", "754"),
        Genre("Martial Arts", "8"),
        Genre("Mature", "46"),
        Genre("Mecha", "22"),
        Genre("Medical", "704"),
        Genre("Medy", "1439"),
        Genre("Monsters", "91"),
        Genre("Music", "457"),
        Genre("Mystery", "30"),
        Genre("Office Workers", "1501"),
        Genre("Oneshot", "405"),
        Genre("Project", "313"),
        Genre("Psychological", "23"),
        Genre("Reincarnation", "57"),
        Genre("Reinkarnasi", "1170"),
        Genre("Romance", "15"),
        Genre("School", "102"),
        Genre("School Life", "9"),
        Genre("Sci-fi", "33"),
        Genre("Seinen", "18"),
        Genre("Shotacon", "1070"),
        Genre("Shoujo", "110"),
        Genre("Shoujo Ai", "113"),
        Genre("Shounen", "10"),
        Genre("Shounen Ai", "shounen-ai"),
        Genre("Si-fi", "776"),
        Genre("Slice of Lif", "773"),
        Genre("Slice of Life", "11"),
        Genre("Smut", "586"),
        Genre("Sports", "103"),
        Genre("Super Power", "274"),
        Genre("Supernatural", "34"),
        Genre("Suspense", "181"),
        Genre("Thriller", "170"),
        Genre("Tragedy", "92"),
        Genre("Urban", "1050"),
        Genre("Vampire", "160"),
        Genre("Video Games", "1093"),
        Genre("Webtoons", "486"),
        Genre("Yaoi", "yaoi"),
        Genre("Zombies", "377")
    )
}

class KomikGo : WPMangaStream("Komik GO (WP Manga Stream)", "https://komikgo.com", "id") {

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    }

    override fun popularMangaSelector() = "div.c-tabs-item__content"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = element.select("div.tab-thumb > a > img").attr("data-src")
        element.select("div.tab-thumb > a").first().let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.attr("title")
        }
        return manga
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/page/$page")!!.newBuilder()
        url.addQueryParameter("post_type", "wp-manga")
        val pattern = "\\s+".toRegex()
        val q = query.replace(pattern, "+")
        if (query.isNotEmpty()) {
            url.addQueryParameter("s", q)
        } else {
            url.addQueryParameter("s", "")
        }

        var orderBy: String

        (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
            when (filter) {
//                is Status -> url.addQueryParameter("manga_status", arrayOf("", "completed", "ongoing")[filter.state])
                is GenreListFilter -> {
                    val genreInclude = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            genreInclude.add(it.id)
                        }
                    }
                    if (genreInclude.isNotEmpty()) {
                        genreInclude.forEach { genre ->
                            url.addQueryParameter("genre[]", genre)
                        }
                    }
                }
                is StatusList -> {
                    val statuses = mutableListOf<String>()
                    filter.state.forEach {
                        if (it.state == 1) {
                            statuses.add(it.id)
                        }
                    }
                    if (statuses.isNotEmpty()) {
                        statuses.forEach { status ->
                            url.addQueryParameter("status[]", status)
                        }
                    }
                }

                is SortBy -> {
                    orderBy = filter.toUriPart()
                    url.addQueryParameter("m_orderby", orderBy)
                }
                is TextField -> url.addQueryParameter(filter.key, filter.state)
            }
        }

        return GET(url.toString(), headers)
    }

    override fun popularMangaNextPageSelector() = "#navigation-ajax"

    override fun mangaDetailsParse(document: Document): SManga {
        val infoElement = document.select("div.site-content").first()

        val manga = SManga.create()
        manga.author = infoElement.select("div.author-content")?.text()
        manga.artist = infoElement.select("div.artist-content")?.text()

        val genres = mutableListOf<String>()
        infoElement.select("div.genres-content a").forEach { element ->
            val genre = element.text()
            genres.add(genre)
        }
        manga.genre = genres.joinToString(", ")
        manga.status = parseStatus(infoElement.select("div.post-status > div:nth-child(2)  div").text())

        manga.description = document.select("div.description-summary")?.text()
        manga.thumbnail_url = document.select("div.summary_image > a > img").attr("data-src")

        return manga
    }

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter {
        val urlElement = element.select("a").first()
        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(urlElement.attr("href"))
        chapter.name = urlElement.text()
        chapter.date_upload = parseChapterDate(element.select("span.chapter-release-date i").text())
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reading-content * img").mapIndexed { i, img ->
            Page(i, "", img.imgAttr())
        }
    }

    private class TextField(name: String, val key: String) : Filter.Text(name)

    private class SortBy : UriPartFilter(
        "Sort by",
        arrayOf(
            Pair("Relevance", ""),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Rating", "rating"),
            Pair("Trending", "trending"),
            Pair("Most View", "views"),
            Pair("New", "new-manga")
        )
    )

    private class Status(name: String, val id: String = name) : Filter.TriState(name)
    private class StatusList(statuses: List<Status>) : Filter.Group<Status>("Status", statuses)

    override fun getFilterList() = FilterList(
        TextField("Author", "author"),
        TextField("Year", "release"),
        SortBy(),
        StatusList(getStatusList()),
        GenreListFilter(getGenreList())
    )

    private fun getStatusList() = listOf(
        Status("Completed", "end"),
        Status("Ongoing", "on-going"),
        Status("Canceled", "canceled"),
        Status("Onhold", "on-hold")
    )

    override fun getGenreList(): List<Genre> = listOf(
        Genre("Adventure", "Adventure"),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Cars", "cars"),
        Genre("4-Koma", "4-koma"),
        Genre("Comedy", "comedy"),
        Genre("Completed", "completed"),
        Genre("Cooking", "cooking"),
        Genre("Dementia", "dementia"),
        Genre("Demons", "demons"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Game", "game"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Kids", "kids"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Military", "military"),
        Genre("Music", "music"),
        Genre("Mystery", "mystery"),
        Genre("Old Comic", "old-comic"),
        Genre("One Shot", "one-shot"),
        Genre("Oneshot", "oneshot"),
        Genre("Parodi", "parodi"),
        Genre("Parody", "parody"),
        Genre("Police", "police"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("Samurai", "samurai"),
        Genre("School", "school"),
        Genre("School Life", "school-life"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Super Power", "super-power"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Vampire", "vampire"),
        Genre("Webtoons", "webtoons"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}

class KomikIndo : WPMangaStream("Komik Indo (WP Manga Stream)", "https://www.komikindo.web.id", "id")

class MangaSwat : WPMangaStream("MangaSwat", "https://mangaswat.com", "ar") {
    /**
     * Use IOException or the app crashes!
     * x-sucuri-cache header is never present on images; specify webpages or glide won't load images!
     */
    private class Sucuri : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            if (response.header("x-sucuri-cache").isNullOrEmpty() && response.request().url().toString().contains("//mangaswat.com"))
                throw IOException("Site protected, open webview | موقع محمي ، عرض ويب مفتوح")
            return response
        }
    }
    override val client: OkHttpClient = super.client.newBuilder().addInterceptor(Sucuri()).build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", baseUrl)
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:76.0) Gecko/20100101 Firefox/76.0")
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3")

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("div.bigcontent").firstOrNull()?.let { infoElement ->
                genre = infoElement.select("span:contains(التصنيف) a").joinToString { it.text() }
                status = parseStatus(infoElement.select("span:contains(الحالة)").firstOrNull()?.ownText())
                author = infoElement.select("span:contains(المؤلف) i").firstOrNull()?.ownText()
                artist = author
                description = infoElement.select("div.desc").text()
                thumbnail_url = infoElement.select("img").imgAttr()
            }
        }
    }
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(baseUrl + chapter.url + "?/", headers) // Bypass "linkvertise" ads
    }

    override fun getFilterList() = FilterList(
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreListFilter(getGenrePairs())
    )

    private class GenreListFilter(pairs: Array<Pair<String, String>>) : UriPartFilter("Genre", pairs)

    private fun getGenrePairs() = arrayOf(
        Pair("<--->", ""),
        Pair("آلات", "%d8%a2%d9%84%d8%a7%d8%aa"),
        Pair("أكشن", "%d8%a3%d9%83%d8%b4%d9%86"),
        Pair("إثارة", "%d8%a5%d8%ab%d8%a7%d8%b1%d8%a9"),
        Pair("إعادة", "%d8%a5%d8%b9%d8%a7%d8%af%d8%a9-%d8%a5%d8%ad%d9%8a%d8%a7%d8%a1"),
        Pair("الحياة", "%d8%a7%d9%84%d8%ad%d9%8a%d8%a7%d8%a9-%d8%a7%d9%84%d9%85%d8%af%d8%b1%d8%b3%d9%8a%d8%a9"),
        Pair("الحياة", "%d8%a7%d9%84%d8%ad%d9%8a%d8%a7%d8%a9-%d8%a7%d9%84%d9%8a%d9%88%d9%85%d9%8a%d8%a9"),
        Pair("العاب", "%d8%a7%d9%84%d8%b9%d8%a7%d8%a8-%d9%81%d9%8a%d8%af%d9%8a%d9%88"),
        Pair("ايتشي", "%d8%a7%d9%8a%d8%aa%d8%b4%d9%8a"),
        Pair("ايسكاي", "%d8%a7%d9%8a%d8%b3%d9%83%d8%a7%d9%8a"),
        Pair("بالغ", "%d8%a8%d8%a7%d9%84%d8%ba"),
        Pair("تاريخي", "%d8%aa%d8%a7%d8%b1%d9%8a%d8%ae%d9%8a"),
        Pair("تراجيدي", "%d8%aa%d8%b1%d8%a7%d8%ac%d9%8a%d8%af%d9%8a"),
        Pair("جوسيه", "%d8%ac%d9%88%d8%b3%d9%8a%d9%87"),
        Pair("جيندر", "%d8%ac%d9%8a%d9%86%d8%af%d8%b1-%d8%a8%d9%86%d8%af%d8%b1"),
        Pair("حربي", "%d8%ad%d8%b1%d8%a8%d9%8a"),
        Pair("حريم", "%d8%ad%d8%b1%d9%8a%d9%85"),
        Pair("خارق", "%d8%ae%d8%a7%d8%b1%d9%82-%d9%84%d9%84%d8%b7%d8%a8%d9%8a%d8%b9%d8%a9"),
        Pair("خيال", "%d8%ae%d9%8a%d8%a7%d9%84"),
        Pair("خيال", "%d8%ae%d9%8a%d8%a7%d9%84-%d8%b9%d9%84%d9%85%d9%8a"),
        Pair("دراما", "%d8%af%d8%b1%d8%a7%d9%85%d8%a7"),
        Pair("دموي", "%d8%af%d9%85%d9%88%d9%8a"),
        Pair("رعب", "%d8%b1%d8%b9%d8%a8"),
        Pair("رومانسي", "%d8%b1%d9%88%d9%85%d8%a7%d9%86%d8%b3%d9%8a"),
        Pair("رياضة", "%d8%b1%d9%8a%d8%a7%d8%b6%d8%a9"),
        Pair("زمكاني", "%d8%b2%d9%85%d9%83%d8%a7%d9%86%d9%8a"),
        Pair("زومبي", "%d8%b2%d9%88%d9%85%d8%a8%d9%8a"),
        Pair("سحر", "%d8%b3%d8%ad%d8%b1"),
        Pair("سينين", "%d8%b3%d9%8a%d9%86%d9%8a%d9%86"),
        Pair("شريحة", "%d8%b4%d8%b1%d9%8a%d8%ad%d8%a9-%d9%85%d9%86-%d8%a7%d9%84%d8%ad%d9%8a%d8%a7%d8%a9"),
        Pair("شوجو", "%d8%b4%d9%88%d8%ac%d9%88"),
        Pair("شونين", "%d8%b4%d9%88%d9%86%d9%8a%d9%86"),
        Pair("شياطين", "%d8%b4%d9%8a%d8%a7%d8%b7%d9%8a%d9%86"),
        Pair("طبخ", "%d8%b7%d8%a8%d8%ae"),
        Pair("طبي", "%d8%b7%d8%a8%d9%8a"),
        Pair("غموض", "%d8%ba%d9%85%d9%88%d8%b6"),
        Pair("فانتازي", "%d9%81%d8%a7%d9%86%d8%aa%d8%a7%d8%b2%d9%8a"),
        Pair("فنون", "%d9%81%d9%86%d9%88%d9%86-%d9%82%d8%aa%d8%a7%d9%84%d9%8a%d8%a9"),
        Pair("فوق", "%d9%81%d9%88%d9%82-%d8%a7%d9%84%d8%b7%d8%a8%d9%8a%d8%b9%d8%a9"),
        Pair("قوى", "%d9%82%d9%88%d9%89-%d8%ae%d8%a7%d8%b1%d9%82%d8%a9"),
        Pair("كوميدي", "%d9%83%d9%88%d9%85%d9%8a%d8%af%d9%8a"),
        Pair("لعبة", "%d9%84%d8%b9%d8%a8%d8%a9"),
        Pair("مافيا", "%d9%85%d8%a7%d9%81%d9%8a%d8%a7"),
        Pair("مصاصى", "%d9%85%d8%b5%d8%a7%d8%b5%d9%89-%d8%a7%d9%84%d8%af%d9%85%d8%a7%d8%a1"),
        Pair("مغامرات", "%d9%85%d8%ba%d8%a7%d9%85%d8%b1%d8%a7%d8%aa"),
        Pair("ميكا", "%d9%85%d9%8a%d9%83%d8%a7"),
        Pair("نفسي", "%d9%86%d9%81%d8%b3%d9%8a"),
        Pair("وحوش", "%d9%88%d8%ad%d9%88%d8%b4"),
        Pair("ويب-تون", "%d9%88%d9%8a%d8%a8-%d8%aa%d9%88%d9%86")
    )
}

class MangaRaw : WPMangaStream("Manga Raw", "https://mangaraw.org", "ja") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/search?order=popular&page=$page", headers)
    override fun popularMangaSelector() = "div.bsx"
    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("div.bigor > a").let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }
    override fun popularMangaNextPageSelector() = "a[rel=next]"
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/search?order=update&page=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search?s=$query&page=$page")
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document)
        .apply { description = document.select("div.bottom").firstOrNull()?.ownText() }
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response, baseUrl + chapter.url.removeSuffix("/"))
            }
    }
    private fun pageListParse(response: Response, chapterUrl: String): List<Page> {
        return response.asJsoup().select("span.page-link").first().ownText().substringAfterLast(" ").toInt()
            .let { lastNum -> IntRange(1, lastNum) }
            .map { num -> Page(num, "$chapterUrl/$num") }
    }
    override fun imageUrlParse(document: Document): String = document.select("a.img-block img").attr("abs:src")
    override fun getFilterList(): FilterList = FilterList()
}

class NonStopScans : WPMangaStream("Non-Stop Scans", "https://www.nonstopscans.com", "en")

class KomikTap : WPMangaStream("KomikTap", "https://komiktap.in/", "id")

class Matakomik : WPMangaStream("Matakomik", "https://matakomik.com", "id") {
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div#readerarea a").mapIndexed { i, a ->
            Page(i, "", a.attr("abs:href"))
        }
    }
}

class KomikindoCo : WPMangaStream("Komikindo.co", "https://komikindo.co", "id")

class ReadKomik : WPMangaStream("Readkomik", "https://readkomik.com", "en")

class MangaP : WPMangaStream("MangaP", "https://mangap.me", "ar")

class MangaProZ : WPMangaStream("Manga Pro Z", "https://mangaproz.com", "ar") {
    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply { name = name.removeSuffix(" free") }
}

class Boosei : WPMangaStream("Boosei", "https://boosei.com", "id")

class Mangakyo : WPMangaStream("Mangakyo", "https://www.mangakyo.me", "id")

class SilenceScan : WPMangaStream(
    "Silence Scan",
    "https://silencescan.net",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR"))
) {

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoEl = document.select("div.bigcontent, div.animefull").first()

        author = infoEl.select("b:contains(Autor) + span").text()
        artist = infoEl.select("b:contains(Artista) + span").text()
        status = parseStatus(infoEl.select("div.imptdt:contains(Status) i").text())
        description = infoEl.select("h2:contains(Sinopse) + div p").joinToString("\n") { it.text() }
        genre = infoEl.select("b:contains(Gêneros) + span a").joinToString { it.text() }
        thumbnail_url = infoEl.select("div.thumb img").imgAttr()
    }

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("span.chapternum").text()
        scanlator = this@SilenceScan.name
        date_upload = element.select("span.chapterdate").firstOrNull()?.text()
            ?.let { parseChapterDate(it) } ?: 0
        setUrlWithoutDomain(element.select("div.eph-num > a").attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val chapterObj = document.select("script:containsData(ts_reader)").first()
            .data()
            .substringAfter("run(")
            .substringBeforeLast(");")
            .let { JSON_PARSER.parse(it) }
            .obj

        if (chapterObj["sources"].array.size() == 0) {
            return emptyList()
        }

        val firstServerAvailable = chapterObj["sources"].array[0].obj

        return firstServerAvailable["images"].array
            .mapIndexed { i, pageUrl -> Page(i, "", pageUrl.string) }
    }

    override fun getGenreList(): List<Genre> = listOf(
        Genre("4-koma", "4-koma"),
        Genre("Ação", "acao"),
        Genre("Adulto", "adulto"),
        Genre("Artes marciais", "artes-marciais"),
        Genre("Comédia", "comedia"),
        Genre("Comedy", "comedy"),
        Genre("Culinária", "culinaria"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Esporte", "esporte"),
        Genre("Fantasia", "fantasia"),
        Genre("Gore", "gore"),
        Genre("Harém", "harem"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Militar", "militar"),
        Genre("Mistério", "misterio"),
        Genre("Oneshot", "oneshot"),
        Genre("Parcialmente Dropado", "parcialmente-dropado"),
        Genre("Psicológico", "psicologico"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo Ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Slice of life", "slice-of-life"),
        Genre("Sobrenatural", "sobrenatural"),
        Genre("Supernatural", "supernatural"),
        Genre("Tragédia", "tragedia"),
        Genre("Vida Escolar", "vida-escolar"),
        Genre("Violência sexual", "violencia-sexual"),
        Genre("Yuri", "yuri")
    )

    companion object {
        private val JSON_PARSER by lazy { JsonParser() }
    }
}
