package eu.kanade.tachiyomi.extension.en.mangaplus

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
import java.util.UUID.randomUUID

class MangaPlus : HttpSource() {
    override val name = "Manga Plus by Shueisha"

    override val baseUrl = "https://jumpg-webapi.tokyo-cdn.com/api"

    override val lang = "en"

    override val supportsLatest = true

    private val catalogHeaders = Headers.Builder().apply {
        add("Origin", WEB_URL)
        add("Referer", WEB_URL)
        add("User-Agent", USER_AGENT)
        add("SESSION-TOKEN", randomUUID().toString())
    }.build()

    override val client = network.client.newBuilder().addInterceptor {
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
    }.build()

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/title_list/ranking", catalogHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result["success"] == null)
            return MangasPage(emptyList(), false)

        val mangas = result["success"]["titleRankingView"]["titles"].array.map {
            SManga.create().apply {
                title = it["name"].string
                thumbnail_url = it["portraitImageUrl"].string
                url = "#/titles/${it["titleId"].int}"
            }
        }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/web/web_home?lang=eng", catalogHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result["success"] == null)
            return MangasPage(emptyList(), false)

        val mangas = result["success"]["webHomeView"]["groups"].array
                .flatMap { it["titles"].array }
                .mapNotNull { it["title"].obj }
                .map {
                    SManga.create().apply {
                        title = it["name"].string
                        thumbnail_url = it["portraitImageUrl"].string
                        url = "#/titles/${it["titleId"].int}"
                    }
                }

        return MangasPage(mangas, false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response -> 
                    val details = mangaDetailsParse(response)
                    details.url = "#/titles/$realQuery"
                    MangasPage(listOf(details), false)
                }
        } else if (query.startsWith(PREFIX_CID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_CID_SEARCH)
            client.newCall(searchMangaByCidRequest(realQuery))
                    .asObservableSuccess()
                    .map { response ->
                        val result = response.asProto()
                        if (result["data"].string == "sucess") {
                            val titleId = result["success"]["mangaViewer"]["titleId"].string as String
                            client.newCall(searchMangaByIdRequest(titleId))
                            .asObservableSuccess()
                            .map { response ->
                                val details = mangaDetailsParse(response)
                                details.url = "#/titles/$titleId"
                                MangasPage(listOf(details), false)
                            }
                        } else {
                            Observable.from(MangasPage(emptyList(), false))
                        }
                    }
                    .flatMap { e -> e }
        } else {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it) }
                .map { MangasPage(it.mangas.filter { m -> m.title.contains(query, true) }, it.hasNextPage) }
        }
    }

    private fun searchMangaByIdRequest(mangaId: String): Request {
        return GET("$baseUrl/title_detail?title_id=$mangaId", catalogHeaders)
    }

    private fun searchMangaByCidRequest(chapterId: String): Request {
        return GET("$baseUrl/manga_viewer?chapter_id=$chapterId&split=yes&img_quality=low", catalogHeaders)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/title_list/all", catalogHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.asProto()

        if (result["success"] == null)
            return MangasPage(emptyList(), false)

        val mangas = result["success"]["allTitlesView"]["titles"].array.map {
            SManga.create().apply {
                title = it["name"].string
                thumbnail_url = it["portraitImageUrl"].string
                url = "#/titles/${it["titleId"].int}"
            }
        }

        return MangasPage(mangas, false)
    }

    private fun titleDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$baseUrl/title_detail?title_id=$mangaId", catalogHeaders)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = titleDetailsRequest(manga)

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.asProto()

        if (result["success"] == null)
            throw Exception("Some error happened when trying to obtain the title details.")

        val details = result["success"]["titleDetailView"].obj
        val title = details["title"].obj

        return SManga.create().apply {
            author = title["author"].string
            artist = title["author"].string
            description = details["overview"].string + "\n\n" + details["viewingPeriodDescription"].string
            status = SManga.ONGOING
        }
    }

    override fun chapterListRequest(manga: SManga): Request = titleDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.asProto()

        if (result["success"] == null)
            return emptyList()

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
        return GET("$baseUrl/manga_viewer?chapter_id=$chapterId&split=yes&img_quality=high", catalogHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.asProto()

        if (result["success"] == null)
            return emptyList()

        return result["success"]["mangaViewer"]["pages"].array
                .mapNotNull { it["page"].obj }
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
        val newHeaders = Headers.Builder().apply {
            add("Referer", WEB_URL)
            add("User-Agent", USER_AGENT)
        }.build()

        return GET(page.imageUrl!!, newHeaders)
    }

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

        private const val PREFIX_ID_SEARCH = "id:"
        private const val PREFIX_CID_SEARCH = "cid:"

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
            })()
            """

        private var PROTOBUFJS: String? = null
        private const val PROTOBUFJS_CDN = "https://cdn.rawgit.com/dcodeIO/protobuf.js/6.8.8/dist/light/protobuf.min.js"

        private const val PROTOBUFJS_DECODE_SRC = """
            Duktape.modSearch = function(id) {
                if (id == "protobufjs")
                    return helper.getProtobuf();
                throw new Error("Cannot find module: " + id);
            }


            var root = (protobuf.roots["default"] || (protobuf.roots["default"] = new protobuf.Root()))
            .addJSON({
            mangaplus: {
                nested: {
                Popup: {
                    fields: {
                    osDefault: { type: "OSDefault", id: 1 },
                    appDefault: { type: "AppDefault", id: 2 } },
                    nested: {
                    AppDefault: {
                        fields: {
                        imageUrl: { type: "string", id: 1 },
                        action: { type: "TransitionAction", id: 2 } } },
                    Button: {
                        fields: {
                        text: { type: "string", id: 1 },
                        action: { type: "TransitionAction", id: 2 } } },
                    OSDefault: {
                        fields: {
                        subject: { type: "string", id: 1 },
                        body: { type: "string", id: 2 },
                        okButton: { type: "Button", id: 3 },
                        neautralButton: { type: "Button", id: 4 },
                        cancelButton: { type: "Button", id: 5 } } } } },
                HomeView: {
                    fields: {
                    topBanners: { rule: "repeated", type: "Banner", id: 1 },
                    groups: { rule: "repeated", type: "UpdatedTitleGroup", id: 2 },
                    popup: { type: "Popup", id: 9 } } },
                Feedback: {
                    fields: {
                    timeStamp: { type: "uint32", id: 1 },
                    body: { type: "string", id: 2 },
                    responseType: { type: "ResponseType", id: 3 } },
                    nested: {
                    ResponseType: {
                        values: { QUESTION: 0, ANSWER: 1 } } } },
                FeedbackView: {
                    fields: {
                    feedbackList: { rule: "repeated", type: "Feedback", id: 1 } } },
                RegistrationData: {
                    fields: {
                    deviceSecret: { type: "string", id: 1 } } },
                Sns: {
                    fields: {
                    body: { type: "string", id: 1 },
                    url: { type: "string", id: 2 } } },
                Chapter: {
                    fields: {
                    titleId: { type: "uint32", id: 1 },
                    chapterId: { type: "uint32", id: 2 },
                    name: { type: "string", id: 3 },
                    subTitle: { type: "string", id: 4 },
                    thumbnailUrl: { type: "string", id: 5 },
                    startTimeStamp: { type: "uint32", id: 6 },
                    endTimeStamp: { type: "uint32", id: 7 },
                    alreadyViewed: { type: "bool", id: 8 },
                    isVerticalOnly: { type: "bool", id: 9 } } },
                AdNetwork: {
                    oneofs: {
                    Network: {
                        oneof: [ "facebook", "admob", "adsense" ] } },
                    fields: {
                    facebook: { type: "Facebook", id: 1 },
                    admob: { type: "Admob", id: 2 },
                    adsense: { type: "Adsense", id: 3 } },
                    nested: {
                    Facebook: {
                        fields: {
                        placementID: { type: "string", id: 1 } } },
                    Admob: {
                        fields: {
                        unitID: { type: "string", id: 1 } } },
                    Adsense: {
                        fields: {
                        unitID: { type: "string", id: 1 } } } } },
                AdNetworkList: {
                    fields: {
                    adNetworks: { rule: "repeated", type: "AdNetwork", id: 1 } } },
                Page: {
                    oneofs: {
                    data: {
                        oneof: [ "mangaPage", "bannerList", "lastPage", "advertisement" ] } },
                    fields: {
                    mangaPage: { type: "MangaPage", id: 1 },
                    bannerList: { type: "BannerList", id: 2 },
                    lastPage: { type: "LastPage", id: 3 },
                    advertisement: { type: "AdNetworkList", id: 4 } },
                    nested: {
                    PageType: {
                        values: { SINGLE: 0, LEFT: 1, RIGHT: 2, DOUBLE: 3 } },
                    ChapterType: {
                        values: { LATEST: 0, SEQUENCE: 1, NOSEQUENCE: 2 } },
                    BannerList: {
                        fields: {
                        bannerTitle: { type: "string", id: 1 },
                        banners: { rule: "repeated", type: "Banner", id: 2 } } },
                    MangaPage: {
                        fields: {
                        imageUrl: { type: "string", id: 1 },
                        width: { type: "uint32", id: 2 },
                        height: { type: "uint32", id: 3 },
                        type: { type: "PageType", id: 4 },
                        encryptionKey: { type: "string", id: 5 } } },
                    LastPage: {
                        fields: {
                        currentChapter: { type: "Chapter", id: 1 },
                        nextChapter: { type: "Chapter", id: 2 },
                        topComments: { rule: "repeated", type: "Comment", id: 3 },
                        isSubscribed: { type: "bool", id: 4 },
                        nextTimeStamp: { type: "uint32", id: 5 },
                        chapterType: { type: "int32", id: 6 } } } } },
                MangaViewer: {
                    fields: {
                    pages: { rule: "repeated", type: "Page", id: 1 },
                    chapterId: { type: "uint32", id: 2 },
                    chapters: { rule: "repeated", type: "Chapter", id: 3 },
                    sns: { type: "Sns", id: 4 },
                    titleName: { type: "string", id: 5 },
                    chapterName: { type: "string", id: 6 },
                    numberOfComments: { type: "int32", id: 7 },
                    isVerticalOnly: { type: "bool", id: 8 },
                    titleId: { type: "uint32", id: 9 },
                    startFromRight: { type: "bool", id: 10 } } },
                Title: {
                    fields: {
                    titleId: { type: "uint32", id: 1 },
                    name: { type: "string", id: 2 },
                    author: { type: "string", id: 3 },
                    portaitImageUrl: { type: "string", id: 4 },
                    landscapeImageUrl: { type: "string", id: 5 },
                    viewCount: { type: "uint32", id: 6 },
                    language: { type: "Language", id: 7 } },
                    nested: {
                    Language: {
                        values: { ENGLISH: 0, SPANISH: 1 } } } },
                TitleDetailView: {
                    fields: {
                    title: { type: "Title", id: 1 },
                    titleImageUrl: { type: "string", id: 2 },
                    overview: { type: "string", id: 3 },
                    backgroundImageUrl: { type: "string", id: 4 },
                    nextTimeStamp: { type: "uint32", id: 5 },
                    updateTiming: { type: "UpdateTiming", id: 6 },
                    viewingPeriodDescription: { type: "string", id: 7 },
                    nonAppearanceInfo: { type: "string", id: 8 },
                    firstChapterList: { rule: "repeated", type: "Chapter", id: 9 },
                    lastChapterList: { rule: "repeated", type: "Chapter", id: 10 },
                    banners: { rule: "repeated", type: "Banner", id: 11 },
                    recommendedTitleList: { rule: "repeated", type: "Title", id: 12 },
                    sns: { type: "Sns", id: 13 },
                    isSimulReleased: { type: "bool", id: 14 },
                    isSubscribed: { type: "bool", id: 15 },
                    rating: { type: "Rating", id: 16 },
                    chaptersDescending: { type: "bool", id: 17 },
                    numberOfViews: { type: "uint32", id: 18 } },
                    nested: {
                    Rating: {
                        values: { ALLAGE: 0, TEEN: 1, TEENPLUS: 2, MATURE: 3 } },
                    UpdateTiming: {
                        values: { NOT_REGULARLY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6, SUNDAY: 7, DAY: 8 } } } },
                UpdatedTitle: {
                    fields: {
                    title: { type: "Title", id: 1 },
                    chapterId: { type: "uint32", id: 2 },
                    chapterName: { type: "string", id: 3 },
                    chapterSubTitle: { type: "string", id: 4 },
                    isLatest: { type: "bool", id: 5 },
                    isVerticalOnly: { type: "bool", id: 6 } } },
                UpdateProfileResultView: {
                    fields: {
                    result: { type: "Result", id: 1 } },
                    nested: {
                    Result: {
                        values: { SUCCESS: 0, DUPLICATED: 1, NG_WORD: 2 } } } },
                UpdatedTitleGroup: {
                    fields: {
                    groupName: { type: "string", id: 1 },
                    titles: { rule: "repeated", type: "UpdatedTitle", id: 2 } } },
                TransitionAction: {
                    fields: {
                    method: { type: "PresentationMethod", id: 1 },
                    url: { type: "string", id: 2 } },
                    nested: {
                    PresentationMethod: {
                        values: { PUSH: 0, MODAL: 1, EXTERNAL: 2 } } } },
                Banner: {
                    fields: {
                    imageUrl: { type: "string", id: 1 },
                    action: { type: "TransitionAction", id: 2 },
                    id: { type: "uint32", id: 3 } } },
                WebHomeView: {
                    fields: {
                    topBanners: { rule: "repeated", type: "Banner", id: 1 },
                    groups: { rule: "repeated", type: "UpdatedTitleGroup", id: 2 },
                    ranking: { rule: "repeated", type: "Title", id: 3 } } },
                TitleList: {
                    fields: {
                    listName: { type: "string", id: 1 },
                    featuredTitles: { rule: "repeated", type: "Title", id: 2 } } },
                FeaturedTitlesView: {
                    fields: {
                    mainBanner: { type: "Banner", id: 1 },
                    subBanner1: { type: "Banner", id: 2 },
                    subBanner2: { type: "Banner", id: 3 },
                    contents: { rule: "repeated", type: "Contents", id: 4 } },
                    nested: {
                    Contents: {
                        oneofs: {
                        data: {
                            oneof: [ "banner", "titleList" ] } },
                        fields: {
                        banner: { type: "Banner", id: 1 },
                        titleList: { type: "TitleList", id: 2 } } } } },
                ProfileSettingsView: {
                    fields: {
                    iconList: { rule: "repeated", type: "CommentIcon", id: 1 },
                    userName: { type: "string", id: 2 },
                    myIcon: { type: "CommentIcon", id: 3 } } },
                Comment: {
                    fields: {
                    id: { type: "uint32", id: 1 },
                    index: { type: "uint32", id: 2 },
                    userName: { type: "string", id: 3 },
                    iconUrl: { type: "string", id: 4 },
                    isMyComment: { type: "bool", id: 6 },
                    alreadyLiked: { type: "bool", id: 7 },
                    numberOfLikes: { type: "uint32", id: 9 },
                    body: { type: "string", id: 10 },
                    created: { type: "uint32", id: 11 } } },
                CommentIcon: {
                    fields: {
                    id: { type: "uint32", id: 1 },
                    imageUrl: { type: "string", id: 2 } } },
                CommentListView: {
                    fields: {
                    comments: { rule: "repeated", type: "Comment", id: 1 },
                    ifSetUserName: { type: "bool", id: 2 } } },
                InitialView: {
                    fields: {
                    gdprAgreementRequired: { type: "bool", id: 1 },
                    englishTitlesCount: { type: "uint32", id: 2 },
                    spanishTitlesCount: { type: "uint32", id: 3 } } },
                SettingsView: {
                    fields: {
                    myIcon: { type: "CommentIcon", id: 1 },
                    userName: { type: "string", id: 2 },
                    noticeOfNewsAndEvents: { type: "bool", id: 3 },
                    noticeOfUpdatesOfSubscribedTitles: { type: "bool", id: 4 },
                    englishTitlesCount: { type: "uint32", id: 5 },
                    spanishTitlesCount: { type: "uint32", id: 6 } } },
                TitleRankingView: {
                    fields: {
                    titles: { rule: "repeated", type: "Title", id: 1 } } },
                AllTitlesView: {
                    fields: {
                    titles: { rule: "repeated", type: "Title", id: 1 } } },
                SubscribedTitlesView: {
                    fields: {
                    titles: { rule: "repeated", type: "Title", id: 1 } } },
                ServiceAnnouncement: {
                    fields: {
                    title: { type: "string", id: 1 },
                    body: { type: "string", id: 2 },
                    date: { type: "int32", id: 3 } } },
                ServiceAnnouncementsView: {
                    fields: {
                    serviceAnnouncements: { rule: "repeated", type: "ServiceAnnouncement", id: 1 } } },
                SuccessResult: {
                    oneofs: {
                    data: {
                        oneof: [ "registerationData", "homeView", "featuredTitlesView", "allTitlesView", "titleRankingView", "subscribedTitlesView", "titleDetailView", "commentListView", "mangaViewer", "webHomeView", "settingsView", "profileSettingsView", "updateProfileResultView", "serviceAnnouncementsView", "initialView", "feedbackView" ] } },
                    fields: {
                    isFeaturedUpdated: { type: "bool", id: 1 },
                    registerationData: { type: "RegistrationData", id: 2 },
                    homeView: { type: "HomeView", id: 3 },
                    featuredTitlesView: { type: "FeaturedTitlesView", id: 4 },
                    allTitlesView: { type: "AllTitlesView", id: 5 },
                    titleRankingView: { type: "TitleRankingView", id: 6 },
                    subscribedTitlesView: { type: "SubscribedTitlesView", id: 7 },
                    titleDetailView: { type: "TitleDetailView", id: 8 },
                    commentListView: { type: "CommentListView", id: 9 },
                    mangaViewer: { type: "MangaViewer", id: 10 },
                    webHomeView: { type: "WebHomeView", id: 11 },
                    settingsView: { type: "SettingsView", id: 12 },
                    profileSettingsView: { type: "ProfileSettingsView", id: 13 },
                    updateProfileResultView: { type: "UpdateProfileResultView", id: 14 },
                    serviceAnnouncementsView: { type: "ServiceAnnouncementsView", id: 15 },
                    initialView: { type: "InitialView", id: 16 },
                    feedbackView: { type: "FeedbackView", id: 17 } } },
                ErrorResult: {
                    fields: {
                    action: { type: "Action", id: 1 },
                    englishPopup: { type: "Popup.OSDefault", id: 2 },
                    spanishPopup: { type: "Popup.OSDefault", id: 3 },
                    debugInfo: { type: "string", id: 4 } },
                    nested: {
                    Action: {
                        values: { DEFAULT: 0, UNAUTHORIZED: 1, MAINTAINENCE: 2, GEOIP_BLOCKING: 3 } } } },
                Response: {
                    oneofs: {
                    data: {
                        oneof: [ "success", "error" ] } },
                    fields: {
                    success: { type: "SuccessResult", id: 1 },
                    error: { type: "ErrorResult", id: 2 } } } } }
            });

            function decode(arr) {
                var Response = root.lookupType("Response");
                var message = Response.decode(arr);
                return Response.toObject(message);
            }

            (function () {
                return JSON.stringify(decode(BYTE_ARR)).replace(/\,\{\}/g, "");
            })();
            """
    }
}
