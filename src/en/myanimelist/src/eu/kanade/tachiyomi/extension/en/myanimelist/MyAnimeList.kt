package eu.kanade.tachiyomi.extension.en.myanimelist

import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class MyAnimeList : ParsedHttpSource() {

    override val name = "MyAnimeList Free Manga"

    override val baseUrl = "https://myanimelist.net"

    override val lang = "en"

    override val supportsLatest = false

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/read/manga")

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { imageIntercept(it) }
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/read/manga", headers)

    override fun popularMangaSelector(): String = "table.top-ranking-table tr.ranking-list td.title"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        val malId = element.select("div.detail a.fs14").first().attr("href").substringAfterLast("/")

        title = element.select("div.detail a.fs14").first().text().substringBefore(" (")
        thumbnail_url = element.select("a.fl-l img.lazyloaded").first()?.attr("src")
        url = "/read/manga/detail/$malId"
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map { MangasPage(it.mangas.filter { m -> m.title.contains(query, true) }, it.hasNextPage) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = popularMangaRequest(page)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET(baseUrl + manga.url, headers, CacheControl.FORCE_NETWORK)

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val infoElement = document.select("div#content div.membership-manager table[width='100%'] tr").first()
        val people = infoElement.select("td:nth-child(2) table span.information.studio.author").first().text().split("), ")

        val requireLogin = document.select("div.search_all div.content-left table tr:contains(Sign-in)").first() != null

        title = infoElement.select("td:nth-child(2) h1.comic-detail-title").first().text()
        author = people
            .filter { it.contains("Story") }
            .joinToString("; ") { it.substringBefore(" (") }
        artist = people
            .filter { it.contains("Art") }
            .joinToString("; ") { it.substringBefore(" (") }
        genre = infoElement.select("td:nth-child(2) table + div section a").joinToString { it.text() }
        description = (if (requireLogin) LOGIN_WARNING else "") +
            document.select("div.search_all div.content-right span[itemprop=description]").first().text()
        thumbnail_url = infoElement.select("td:nth-child(1) a img.lazyload").first().attr("data-src")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return super.fetchChapterList(manga)
            .map { it.reversed() }
    }

    override fun chapterListSelector() = "div.search_all div.content-left table.free-manga-detail-table " +
        "tr:not(:contains(Sign-in)):not(:contains(Buy)):not(:contains(Coming))"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val info = element.select("td:nth-child(2) div.clearfix a.fs15").first()

        name = info.text()
        setUrlWithoutDomain(info.attr("href"))
    }

    override fun pageListParse(document: Document): List<Page> {
        val chapterData = document.select("div.v-manga-store-viewer").first()
                ?: throw Exception("You must login to read this chapter.")
        val chapterJson = chapterData.attr("data-state")
        val chapterInfo = JSON_PARSER.parse(chapterJson).obj
        val imageBaseUrl = chapterInfo["manuscript"]["image_base_url"].string
        val queryParamsPart = chapterInfo["manuscript"]["query_params_part"].string

        return chapterInfo["manuscript"]["filenames"].array
            .mapIndexed { i, fileName -> Page(i, "", "$imageBaseUrl/${fileName.string}?$queryParamsPart") }
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (!request.url().queryParameterNames().contains("Key-Pair-Id")) {
            return response
        }

        val contentType = response.header("Content-Type", "image/jpeg")!!
        val image = decodeImage(response.body()!!.bytes())
        val body = ResponseBody.create(MediaType.parse(contentType), image)
        return response.newBuilder().body(body).build()
    }

    /**
     * Decodes the image of chapter page using the XOR Cipher.
     *
     * The [image] comes in a [ByteArray], with the following properties:
     *
     * image[0] = 1
     * image[1] = n (key size)
     * image[2 ~ 2 + n - 1] = i (key)
     * image[2 + n ~ image.length - 1] = r (image contents)
     *
     * To decrypt, for each byte b in the index o in r,
     * b needs to be replaced with b xor i[o % n].
     */
    private fun decodeImage(image: ByteArray): ByteArray {
        val n = image[1].toPositiveInt()
        val i = image.slice(2 until 2 + n).map { it.toPositiveInt() }
        val r = image.drop(2 + n).map { it.toPositiveInt() }.toMutableList()

        for ((o, b) in r.iterator().withIndex()) {
            r[o] = b xor i[o % n]
        }

        return ByteArray(r.size) { pos -> r[pos].toByte() }
    }

    private fun Byte.toPositiveInt() = toInt() and 0xFF

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("This method should not be called!")

    override fun latestUpdatesSelector() = throw Exception("This method should not be called!")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun latestUpdatesNextPageSelector() = throw Exception("This method should not be called!")

    override fun searchMangaSelector() = throw Exception("This method should not be called!")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("This method should not be called!")

    override fun searchMangaNextPageSelector() = throw Exception("This method should not be called!")

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.87 Safari/537.36"
        private val JSON_PARSER by lazy { JsonParser() }

        private const val LOGIN_WARNING = "This manga has chapters that require login. Use the WebView to login into your MyAnimeList account.\n\n"
    }
}
