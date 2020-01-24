package eu.kanade.tachiyomi.extension.all.mangaplus

import android.os.Build
import com.google.gson.Gson
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.*
import rx.Observable
import java.lang.Exception
import java.util.UUID

abstract class MangaPlus(override val lang: String,
                         private val internalLang: String,
                         private val langCode: Language) : HttpSource() {

    override val name = "Manga Plus by Shueisha"

    override val baseUrl = "https://jumpg-webapi.tokyo-cdn.com/api"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", WEB_URL)
        .add("Referer", WEB_URL)
        .add("User-Agent", USER_AGENT)
        .add("SESSION-TOKEN", UUID.randomUUID().toString())

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { imageIntercept(it) }
        .build()

    private val protobufJs: String by lazy {
        client.newCall(GET(PROTOBUFJS_CDN, headers)).execute().body()!!.string()
    }

    private val gson: Gson by lazy { Gson() }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/title_list/ranking", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null)
            return MangasPage(emptyList(), false)

        val mangas = result.success.titleRankingView!!.titles
            .filter { it.language == langCode }
            .map {
                SManga.create().apply {
                    title = it.name
                    thumbnail_url = getImageUrl(it.portraitImageUrl)
                    url = "#/titles/${it.titleId}"
                }
            }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/web/web_home?lang=$internalLang", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null)
            return MangasPage(emptyList(), false)

        val mangas = result.success.webHomeView!!.groups
            .flatMap { it.titles }
            .mapNotNull { it.title }
            .filter { it.language == langCode }
            .map {
                SManga.create().apply {
                    title = it.name
                    thumbnail_url = getImageUrl(it.portraitImageUrl)
                    url = "#/titles/${it.titleId}"
                }
            }
            .distinctBy { it.title }

        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return super.fetchSearchManga(page, query, filters)
            .map { MangasPage(it.mangas.filter { m -> m.title.contains(query, true) }, it.hasNextPage) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/title_list/all", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result.success == null)
            return MangasPage(emptyList(), false)

        val mangas = result.success.allTitlesView!!.titles
            .filter { it.language == langCode }
            .map {
                SManga.create().apply {
                    title = it.name
                    thumbnail_url = getImageUrl(it.portraitImageUrl)
                    url = "#/titles/${it.titleId}"
                }
            }

        return MangasPage(mangas, false)
    }

    private fun titleDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/title_detail?title_id=$mangaId", headers)
    }

    // Workaround to allow "Open in browser" use the real URL.
    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(titleDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response).apply { initialized = true }
            }
    }

    // Always returns the real URL for the "Open in browser".
    override fun mangaDetailsRequest(manga: SManga): Request = GET(WEB_URL + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(mapLangToErrorProperty(result.error!!).body)

        val details = result.success.titleDetailView!!
        val title = details.title

        return SManga.create().apply {
            author = title.author
            artist = title.author
            description = details.overview + "\n\n" + details.viewingPeriodDescription
            status = SManga.ONGOING
            thumbnail_url = getImageUrl(title.portraitImageUrl)
        }
    }

    override fun chapterListRequest(manga: SManga): Request = titleDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(mapLangToErrorProperty(result.error!!).body)

        val titleDetailView = result.success.titleDetailView!!

        val chapters = titleDetailView.firstChapterList + titleDetailView.lastChapterList

        return chapters.reversed()
            // If the subTitle is null, then the chapter time expired.
            .filter { it.subTitle != null }
            .map {
                SChapter.create().apply {
                    name = "${it.name} - ${it.subTitle}"
                    scanlator = "Shueisha"
                    date_upload = 1000L * it.startTimeStamp
                    url = "#/viewer/${it.chapterId}"
                    chapter_number = it.name.substringAfter("#").toFloatOrNull() ?: 0f
                }
            }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$baseUrl/manga_viewer?chapter_id=$chapterId&split=yes&img_quality=high", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asProto()

        if (result.success == null)
            throw Exception(mapLangToErrorProperty(result.error!!).body)

        return result.success.mangaViewer!!.pages
            .mapNotNull { it.page }
            .mapIndexed { i, page ->
                val encryptionKey = if (page.encryptionKey == null) "" else "&encryptionKey=${page.encryptionKey}"
                Page(i, "", "${page.imageUrl}$encryptionKey")
            }
    }

    override fun fetchImageUrl(page: Page): Observable<String> {
        return Observable.just(page.imageUrl!!)
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageRequest(page: Page): Request {
        val newHeaders = Headers.Builder()
            .add("Referer", WEB_URL)
            .add("User-Agent", USER_AGENT)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    private fun mapLangToErrorProperty(error: ErrorResult): Popup = when (lang) {
        "es" -> error.spanishPopup
        else -> error.englishPopup
    }

    // Maybe removing the duration parameter make the image accessible forever.
    private fun getImageUrl(url: String): String {
        val imageUrl = url.substringBefore("&duration")

        return HttpUrl.parse(IMAGES_WESERV_URL)!!.newBuilder()
            .addEncodedQueryParameter("url", imageUrl)
            .toString()
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (!request.url().queryParameterNames().contains("encryptionKey")) {
            return chain.proceed(request)
        }

        val encryptionKey = request.url().queryParameter("encryptionKey")!!

        // Change the url and remove the encryptionKey to avoid detection.
        val newUrl = request.url().newBuilder().removeAllQueryParameters("encryptionKey").build()
        request = request.newBuilder().url(newUrl).build()

        val response = chain.proceed(request)

        val image = decodeImage(encryptionKey, response.body()!!.bytes())

        val body = ResponseBody.create(MediaType.parse("image/jpeg"), image)
        return response.newBuilder().body(body).build()
    }

    private fun decodeImage(encryptionKey: String, image: ByteArray): ByteArray {
        val keyStream = HEX_GROUP
            .findAll(encryptionKey)
            .toList()
            .map { it.groupValues[1].toInt(16) }

        val content = image
            .map { it.toInt() }
            .toMutableList()

        val blockSizeInBytes = keyStream.size

        for ((i, value) in content.iterator().withIndex()) {
            content[i] = value xor keyStream[i % blockSizeInBytes]
        }

        return ByteArray(content.size) { pos -> content[pos].toByte() }
    }

    private fun Response.asProto(): MangaPlusResponse {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT)
            return ProtoBuf.load(MangaPlusSerializer, body()!!.bytes())

        // Apparently, the Kotlinx Serialization lib causes a crash on KitKat devices (see #1678).
        // So, if the device is running KitKat or lower, we use the old method of parsing
        // their API -- using ProtobufJS + Duktape + Gson. When the repository drops the
        // support for KitKat devices, the old way can be removed.

        val bytes = body()!!.bytes()
        val messageBytes = "var BYTE_ARR = new Uint8Array([${bytes.joinToString()}]);"

        val res = Duktape.create().use {
            it.set("helper", DuktapeHelper::class.java, object : DuktapeHelper {
                override fun getProtobuf(): String = protobufJs
            })
            it.evaluate(messageBytes + DECODE_SCRIPT) as String
        }

        // The Json.parse method of the Kotlinx Serialization causes the app to crash too,
        // so unfortunately we have to use Gson to deserialize.
        return gson.fromJson(res, MangaPlusResponse::class.java)
    }

    private interface DuktapeHelper {
        @Suppress("unused")
        fun getProtobuf(): String
    }

    companion object {
        private const val WEB_URL = "https://mangaplus.shueisha.co.jp"
        private const val IMAGES_WESERV_URL = "https://images.weserv.nl"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"

        private val HEX_GROUP = "(.{1,2})".toRegex()

        private const val PROTOBUFJS_CDN = "https://cdn.rawgit.com/dcodeIO/protobuf.js/6.8.8/dist/light/protobuf.min.js"
    }
}
