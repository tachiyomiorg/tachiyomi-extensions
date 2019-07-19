package eu.kanade.tachiyomi.extension.all.mangaplus

import com.github.salomonbrys.kotson.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.squareup.duktape.Duktape
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.*
import rx.Observable
import java.lang.Exception
import java.util.UUID

abstract class MangaPlus(override val lang: String,
                         private val internalLang: String,
                         private val langCode: Int) : HttpSource() {

    override val name = "Manga Plus by Shueisha"

    override val baseUrl = "https://jumpg-webapi.tokyo-cdn.com/api"

    override val supportsLatest = true

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
            .add("Origin", WEB_URL)
            .add("Referer", WEB_URL)
            .add("User-Agent", USER_AGENT)
            .add("SESSION-TOKEN", UUID.randomUUID().toString())

    override val client: OkHttpClient = network.client.newBuilder()
            .addInterceptor {
                var request = it.request()

                if (!request.url().queryParameterNames().contains("encryptionKey")) {
                    return@addInterceptor it.proceed(request)
                }

                val encryptionKey = request.url().queryParameter("encryptionKey")!!

                // Change the url and remove the encryptionKey to avoid detection.
                val newUrl = request.url().newBuilder().removeAllQueryParameters("encryptionKey").build()
                request = request.newBuilder().url(newUrl).build()

                val response = it.proceed(request)

                val image = decodeImage(encryptionKey, response.body()!!.bytes())

                val body = ResponseBody.create(MediaType.parse("image/jpeg"), image)
                response.newBuilder().body(body).build()
            }
            .build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/title_list/ranking", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result["success"] == null)
            return MangasPage(emptyList(), false)

        val mangas = result["success"]["titleRankingView"]["titles"].array
                .filter { it["language"].int == langCode }
                .map {
                    SManga.create().apply {
                        title = it["name"].string
                        thumbnail_url = removeDuration(it["portraitImageUrl"].string)
                        url = "#/titles/${it["titleId"].int}"
                    }
                }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/web/web_home?lang=$internalLang", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result["success"] == null)
            return MangasPage(emptyList(), false)

        val mangas = result["success"]["webHomeView"]["groups"].array
                .flatMap { it["titles"].array }
                .mapNotNull { it["title"].obj }
                .filter { it["language"].int == langCode }
                .map {
                    SManga.create().apply {
                        title = it["name"].string
                        thumbnail_url = removeDuration(it["portraitImageUrl"].string)
                        url = "#/titles/${it["titleId"].int}"
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

        if (result["success"] == null)
            return MangasPage(emptyList(), false)

        val mangas = result["success"]["allTitlesView"]["titles"].array
                .filter { it["language"].int == langCode }
                .map {
                    SManga.create().apply {
                        title = it["name"].string
                        thumbnail_url = removeDuration(it["portraitImageUrl"].string)
                        url = "#/titles/${it["titleId"].int}"
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

        if (result["success"] == null)
            throw Exception(result["error"][mapLangToErrorProperty()]["body"].string)

        val details = result["success"]["titleDetailView"].obj
        val title = details["title"].obj

        return SManga.create().apply {
            author = title["author"].string
            artist = title["author"].string
            description = details["overview"].string + "\n\n" + details["viewingPeriodDescription"].string
            status = SManga.ONGOING
            thumbnail_url = removeDuration(title["portraitImageUrl"].string)
        }
    }

    override fun chapterListRequest(manga: SManga): Request = titleDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asProto()

        if (result["success"] == null)
            throw Exception(result["error"][mapLangToErrorProperty()]["body"].string)

        val titleDetailView = result["success"]["titleDetailView"].obj

        val chapters = titleDetailView["firstChapterList"].array +
                (titleDetailView["lastChapterList"].nullArray ?: emptyList())

        return chapters.reversed()
                // If the subTitle is null, then the chapter time expired.
                .filter { it.obj["subTitle"] != null }
                .map {
                    SChapter.create().apply {
                        name = "${it["name"].string} - ${it["subTitle"].string}"
                        scanlator = "Shueisha"
                        date_upload = 1000L * it["startTimeStamp"].long
                        url = "#/viewer/${it["chapterId"].int}"
                        chapter_number = it["name"].string.substringAfter("#").toFloatOrNull() ?: 0f
                    }
                }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$baseUrl/manga_viewer?chapter_id=$chapterId&split=yes&img_quality=high", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asProto()

        if (result["success"] == null)
            throw Exception(result["error"][mapLangToErrorProperty()]["body"].string)

        return result["success"]["mangaViewer"]["pages"].array
                .mapNotNull { it.obj["page"].nullObj }
                .mapIndexed { i, page ->
                    val encryptionKey = if (page["encryptionKey"] == null) "" else "&encryptionKey=${page["encryptionKey"].string}"
                    Page(i, "", "${page["imageUrl"].string}$encryptionKey")
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

    private fun mapLangToErrorProperty(): String = when (lang) {
        "es" -> "spanishPopup"
        else -> "englishPopup"
    }

    // Maybe removing the duration parameter make the image accessible forever.
    private fun removeDuration(url: String): String = url.substringBefore("&duration")

    private fun decodeImage(encryptionKey: String, image: ByteArray): ByteArray {
        val variablesSrc = """
            var ENCRYPTION_KEY = "$encryptionKey";
            var RESPONSE_BYTES = new Uint8Array([${image.joinToString()}]);
            """

        val res = Duktape.create().use {
            it.evaluate(variablesSrc + IMAGE_DECRYPT_SRC) as String
        }

        return res.substringAfter("[").substringBefore("]")
                .split(",")
                .map { it.toInt().toByte() }
                .toByteArray()
    }

    private fun Response.asProto(): JsonObject {
        val bytes = body()!!.bytes()
        val messageBytes = "var BYTE_ARR = new Uint8Array([${bytes.joinToString()}]);"

        val res = Duktape.create().use {
            it.set("helper", DuktapeHelper::class.java, object : DuktapeHelper {
                override fun getProtobuf(): String = getProtobufJSLib()
            })
            it.evaluate(messageBytes + PROTOBUFJS_DECODE_SRC) as String
        }

        return JSON_PARSER.parse(res).obj
    }

    private fun getProtobufJSLib(): String {
        if (PROTOBUFJS == null)
            PROTOBUFJS = client.newCall(GET(PROTOBUFJS_CDN, headers))
                    .execute().body()!!.string()
        return checkNotNull(PROTOBUFJS)
    }

    private interface DuktapeHelper {
        fun getProtobuf(): String
    }

    companion object {
        private const val WEB_URL = "https://mangaplus.shueisha.co.jp"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.92 Safari/537.36"
        private val JSON_PARSER by lazy { JsonParser() }

        private const val IMAGE_DECRYPT_SRC = """
            function hex2bin(hex) {
                return new Uint8Array(hex.match(/.{1,2}/g)
                        .map(function (x) { return parseInt(x, 16) }));
            }

            function decode(encryptionKey, bytes) {
                var keystream = hex2bin(encryptionKey);
                var content = bytes;
                var blockSizeInBytes = keystream.length;

                for (var i = 0; i < content.length; i++) {
                    content[i] ^= keystream[i % blockSizeInBytes];
                }

                return content;
            }

            (function() {
                var decoded = decode(ENCRYPTION_KEY, RESPONSE_BYTES);
                return JSON.stringify([].slice.call(decoded));
            })();
            """

        private var PROTOBUFJS: String? = null
        private const val PROTOBUFJS_CDN = "https://cdn.rawgit.com/dcodeIO/protobuf.js/6.8.8/dist/light/protobuf.min.js"

        private const val PROTOBUFJS_DECODE_SRC = """
            Duktape.modSearch = function(id) {
                if (id == "protobufjs")
                    return helper.getProtobuf();
                throw new Error("Cannot find module: " + id);
            }

            var protobuf = require("protobufjs");

            var Root = protobuf.Root;
            var Type = protobuf.Type;
            var Field = protobuf.Field;
            var Enum = protobuf.Enum;
            var OneOf = protobuf.OneOf;

            var Response = new Type("Response")
                  .add(
                      new OneOf("data")
                            .add(new Field("success", 1, "SuccessResult"))
                            .add(new Field("error", 2, "ErrorResult"))
                  );

            var ErrorResult = new Type("ErrorResult")
                  .add(new Field("action", 1, "Action"))
                  .add(new Field("englishPopup", 2, "Popup"))
                  .add(new Field("spanishPopup", 3, "Popup"));

            var Action = new Enum("Action")
                  .add("DEFAULT", 0)
                  .add("UNAUTHORIZED", 1)
                  .add("MAINTAINENCE", 2)
                  .add("GEOIP_BLOCKING", 3);

            var Popup = new Type("Popup")
                  .add(new Field("subject", 1, "string"))
                  .add(new Field("body", 2, "string"));

            var SuccessResult = new Type("SuccessResult")
                  .add(new Field("isFeaturedUpdated", 1, "bool"))
                  .add(
                      new OneOf("data")
                            .add(new Field("allTitlesView", 5, "AllTitlesView"))
                            .add(new Field("titleRankingView", 6, "TitleRankingView"))
                            .add(new Field("titleDetailView", 8, "TitleDetailView"))
                            .add(new Field("mangaViewer", 10, "MangaViewer"))
                            .add(new Field("webHomeView", 11, "WebHomeView"))
                  );

            var TitleRankingView = new Type("TitleRankingView")
                  .add(new Field("titles", 1, "Title", "repeated"));

            var AllTitlesView = new Type("AllTitlesView")
                  .add(new Field("titles", 1, "Title", "repeated"));

            var WebHomeView = new Type("WebHomeView")
                  .add(new Field("groups", 2, "UpdatedTitleGroup", "repeated"));

            var TitleDetailView = new Type("TitleDetailView")
                  .add(new Field("title", 1, "Title"))
                  .add(new Field("titleImageUrl", 2, "string"))
                  .add(new Field("overview", 3, "string"))
                  .add(new Field("backgroundImageUrl", 4, "string"))
                  .add(new Field("nextTimeStamp", 5, "uint32"))
                  .add(new Field("updateTiming", 6, "UpdateTiming"))
                  .add(new Field("viewingPeriodDescription", 7, "string"))
                  .add(new Field("firstChapterList", 9, "Chapter", "repeated"))
                  .add(new Field("lastChapterList", 10, "Chapter", "repeated"))
                  .add(new Field("isSimulReleased", 14, "bool"))
                  .add(new Field("chaptersDescending", 17, "bool"));

            var UpdateTiming = new Enum("UpdateTiming")
                  .add("NOT_REGULARLY", 0)
                  .add("MONDAY", 1)
                  .add("TUESDAY", 2)
                  .add("WEDNESDAY", 3)
                  .add("THURSDAY", 4)
                  .add("FRIDAY", 5)
                  .add("SATURDAY", 6)
                  .add("SUNDAY", 7)
                  .add("DAY", 8);

            var MangaViewer = new Type("MangaViewer")
                  .add(new Field("pages", 1, "Page", "repeated"));

            var Title = new Type("Title")
                  .add(new Field("titleId", 1, "uint32"))
                  .add(new Field("name", 2, "string"))
                  .add(new Field("author", 3, "string"))
                  .add(new Field("portraitImageUrl", 4, "string"))
                  .add(new Field("landscapeImageUrl", 5, "string"))
                  .add(new Field("viewCount", 6, "uint32"))
                  .add(new Field("language", 7, "Language", {"default": 0}));

            var Language = new Enum("Language")
                  .add("ENGLISH", 0)
                  .add("SPANISH", 1);

            var UpdatedTitleGroup = new Type("UpdatedTitleGroup")
                  .add(new Field("groupName", 1, "string"))
                  .add(new Field("titles", 2, "UpdatedTitle", "repeated"));

            var UpdatedTitle = new Type("UpdatedTitle")
                  .add(new Field("title", 1, "Title"))
                  .add(new Field("chapterId", 2, "uint32"))
                  .add(new Field("chapterName", 3, "string"))
                  .add(new Field("chapterSubtitle", 4, "string"));

            var Chapter = new Type("Chapter")
                  .add(new Field("titleId", 1, "uint32"))
                  .add(new Field("chapterId", 2, "uint32"))
                  .add(new Field("name", 3, "string"))
                  .add(new Field("subTitle", 4, "string", "optional"))
                  .add(new Field("startTimeStamp", 6, "uint32"))
                  .add(new Field("endTimeStamp", 7, "uint32"));

            var Page = new Type("Page")
                  .add(new Field("page", 1, "MangaPage"));

            var MangaPage = new Type("MangaPage")
                  .add(new Field("imageUrl", 1, "string"))
                  .add(new Field("width", 2, "uint32"))
                  .add(new Field("height", 3, "uint32"))
                  .add(new Field("encryptionKey", 5, "string", "optional"));

            var root = new Root()
                  .define("mangaplus")
                  .add(Response)
                  .add(ErrorResult)
                  .add(Action)
                  .add(Popup)
                  .add(SuccessResult)
                  .add(TitleRankingView)
                  .add(AllTitlesView)
                  .add(WebHomeView)
                  .add(TitleDetailView)
                  .add(UpdateTiming)
                  .add(MangaViewer)
                  .add(Title)
                  .add(Language)
                  .add(UpdatedTitleGroup)
                  .add(UpdatedTitle)
                  .add(Chapter)
                  .add(Page)
                  .add(MangaPage);

            function decode(arr) {
                var Response = root.lookupType("Response");
                var message = Response.decode(arr);
                return Response.toObject(message, {defaults: true});
            }

            (function () {
                return JSON.stringify(decode(BYTE_ARR)).replace(/\,\{\}/g, "");
            })();
            """
    }
}
