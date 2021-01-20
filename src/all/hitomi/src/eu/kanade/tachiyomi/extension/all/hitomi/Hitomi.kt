package eu.kanade.tachiyomi.extension.all.hitomi

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.CheckBoxPreference
import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import rx.Single
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import androidx.preference.CheckBoxPreference as AndroidXCheckBoxPreference
import androidx.preference.PreferenceScreen as AndroidXPreferenceScreen

/**
 * Ported from TachiyomiSy
 * Original work by NerdNumber9 for TachiyomiEH
 */

open class Hitomi(override val lang: String, private val nozomiLang: String) : HttpSource(), ConfigurableSource {

    override val supportsLatest = true

    override val name = if (nozomiLang == "all") "Hitomi.la unfiltered" else "Hitomi.la"

    override val baseUrl = BASE_URL

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .flatMap { responseToMangas(it) }
    }

    override fun popularMangaRequest(page: Int) = HitomiNozomi.rangedGet(
        "$LTN_BASE_URL/popular-$nozomiLang.nozomi",
        100L * (page - 1),
        99L + 100 * (page - 1)
    )

    private fun responseToMangas(response: Response): Observable<MangasPage> {
        val range = response.header("Content-Range")!!
        val total = range.substringAfter('/').toLong()
        val end = range.substringBefore('/').substringAfter('-').toLong()
        val body = response.body()!!
        return parseNozomiPage(body.bytes())
            .map {
                MangasPage(it, end < total - 1)
            }
    }

    private fun parseNozomiPage(array: ByteArray): Observable<List<SManga>> {
        val cursor = ByteCursor(array)
        val ids = (1..array.size / 4).map {
            cursor.nextInt()
        }

        return nozomiIdsToMangas(ids).toObservable()
    }

    private fun nozomiIdsToMangas(ids: List<Int>): Single<List<SManga>> {
        return Single.zip(
            ids.map { int ->
                client.newCall(GET("$LTN_BASE_URL/galleryblock/$int.html"))
                    .asObservableSuccess()
                    .subscribeOn(Schedulers.io()) // Perform all these requests in parallel
                    .map { parseGalleryBlock(it) }
                    .toSingle()
            }
        ) { it.map { m -> m as SManga } }
    }

    private fun Document.selectFirst(selector: String) = this.select(selector).first()

    private fun parseGalleryBlock(response: Response): SManga {
        val doc = response.asJsoup()
        return SManga.create().apply {
            val titleElement = doc.selectFirst("h1")
            title = titleElement.text()
            thumbnail_url = "https:" + if (useHqThumbPref()) {
                doc.selectFirst("img").attr("srcset").substringBefore(' ')
            } else {
                doc.selectFirst("img").attr("src")
            }
            url = titleElement.child(0).attr("href")
        }
    }

    override fun popularMangaParse(response: Response) = throw UnsupportedOperationException("Not used")

    // Latest

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .flatMap { responseToMangas(it) }
    }

    override fun latestUpdatesRequest(page: Int) = HitomiNozomi.rangedGet(
        "$LTN_BASE_URL/index-$nozomiLang.nozomi",
        100L * (page - 1),
        99L + 100 * (page - 1)
    )

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException("Not used")

    // Search

    private var cachedTagIndexVersion: Long? = null
    private var tagIndexVersionCacheTime: Long = 0
    private fun tagIndexVersion(): Single<Long> {
        val sCachedTagIndexVersion = cachedTagIndexVersion
        return if (sCachedTagIndexVersion == null ||
            tagIndexVersionCacheTime + INDEX_VERSION_CACHE_TIME_MS < System.currentTimeMillis()
        ) {
            HitomiNozomi.getIndexVersion(client, "tagindex").subscribeOn(Schedulers.io()).doOnNext {
                cachedTagIndexVersion = it
                tagIndexVersionCacheTime = System.currentTimeMillis()
            }.toSingle()
        } else {
            Single.just(sCachedTagIndexVersion)
        }
    }

    private var cachedGalleryIndexVersion: Long? = null
    private var galleryIndexVersionCacheTime: Long = 0
    private fun galleryIndexVersion(): Single<Long> {
        val sCachedGalleryIndexVersion = cachedGalleryIndexVersion
        return if (sCachedGalleryIndexVersion == null ||
            galleryIndexVersionCacheTime + INDEX_VERSION_CACHE_TIME_MS < System.currentTimeMillis()
        ) {
            HitomiNozomi.getIndexVersion(client, "galleriesindex").subscribeOn(Schedulers.io()).doOnNext {
                cachedGalleryIndexVersion = it
                galleryIndexVersionCacheTime = System.currentTimeMillis()
            }.toSingle()
        } else {
            Single.just(sCachedGalleryIndexVersion)
        }
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(GET("$baseUrl/cg/$id", headers)).asObservableSuccess()
                .map { MangasPage(listOf(mangaDetailsParse(it).apply { url = "/cg/$id" }), false) }
        } else {
            val splitQuery = query.split(" ")

            val positive = splitQuery.filter { !it.startsWith('-') }.toMutableList()
            if (nozomiLang != "all") positive += "language:$nozomiLang"
            val negative = (splitQuery - positive).map { it.removePrefix("-") }

            // TODO Cache the results coming out of HitomiNozomi (this TODO dates back to TachiyomiEH)
            val hn = Single.zip(tagIndexVersion(), galleryIndexVersion()) { tv, gv -> tv to gv }
                .map { HitomiNozomi(client, it.first, it.second) }

            var base = if (positive.isEmpty()) {
                hn.flatMap { n -> n.getGalleryIdsFromNozomi(null, "index", "all").map { n to it.toSet() } }
            } else {
                val q = positive.removeAt(0)
                hn.flatMap { n -> n.getGalleryIdsForQuery(q).map { n to it.toSet() } }
            }

            base = positive.fold(base) { acc, q ->
                acc.flatMap { (nozomi, mangas) ->
                    nozomi.getGalleryIdsForQuery(q).map {
                        nozomi to mangas.intersect(it)
                    }
                }
            }

            base = negative.fold(base) { acc, q ->
                acc.flatMap { (nozomi, mangas) ->
                    nozomi.getGalleryIdsForQuery(q).map {
                        nozomi to (mangas - it)
                    }
                }
            }

            base.flatMap { (_, ids) ->
                val chunks = ids.chunked(PAGE_SIZE)

                nozomiIdsToMangas(chunks[page - 1]).map { mangas ->
                    MangasPage(mangas, page < chunks.size)
                }
            }.toObservable()
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw UnsupportedOperationException("Not used")
    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        fun String.replaceSpaces() = this.replace(" ", "_")

        return SManga.create().apply {
            thumbnail_url = document.select("div.cover img").attr("abs:src")
            author = document.select("div.gallery h2 a").joinToString { it.text() }
            val tableInfo = document.select("table tr")
                .map { tr ->
                    val key = tr.select("td:first-child").text()
                    val value = with(tr.select("td:last-child a")) {
                        when (key) {
                            "Series", "Characters" -> {
                                if (text().isNotEmpty())
                                    joinToString { "${attr("href").removePrefix("/").substringBefore("/")}:${it.text().replaceSpaces()}" } else null
                            }
                            "Tags" -> joinToString { element ->
                                element.text().let {
                                    when {
                                        it.contains("♀") -> "female:${it.substringBeforeLast(" ").replaceSpaces()}"
                                        it.contains("♂") -> "male:${it.substringBeforeLast(" ").replaceSpaces()}"
                                        else -> it
                                    }
                                }
                            }
                            else -> joinToString { it.text() }
                        }
                    }
                    Pair(key, value)
                }
                .plus(Pair("Date uploaded", document.select("div.gallery span.date").text()))
                .toMap()
            description = tableInfo.filterNot { it.value.isNullOrEmpty() || it.key in listOf("Series", "Characters", "Tags") }.entries.joinToString("\n") { "${it.key}: ${it.value}" }
            genre = listOfNotNull(tableInfo["Series"], tableInfo["Characters"], tableInfo["Tags"]).joinToString()
        }
    }

    // Chapters

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return Observable.just(
            listOf(
                SChapter.create().apply {
                    url = manga.url
                    name = "Chapter"
                    chapter_number = 0.0f
                }
            )
        )
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException("Not used")

    // Pages

    private fun hlIdFromUrl(url: String) =
        url.split('/').last().split('-').last().substringBeforeLast('.')

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$LTN_BASE_URL/galleries/${hlIdFromUrl(chapter.url)}.js")
    }

    private val jsonParser = JsonParser()

    override fun pageListParse(response: Response): List<Page> {
        val str = response.body()!!.string()
        val json = jsonParser.parse(str.removePrefix("var galleryinfo = "))
        return json["files"].array.mapIndexed { i, jsonElement ->
            val hash = jsonElement["hash"].string
            val ext = if (jsonElement["haswebp"].string == "0" || !hitomiAlwaysWebp()) jsonElement["name"].string.split('.').last() else "webp"
            val path = if (jsonElement["haswebp"].string == "0" || !hitomiAlwaysWebp()) "images" else "webp"
            val hashPath1 = hash.takeLast(1)
            val hashPath2 = hash.takeLast(3).take(2)

            // https://ltn.hitomi.la/reader.js
            // function make_image_element()
            val secondSubdomain = if (jsonElement["haswebp"].string == "0" && jsonElement["hasavif"].string == "0") "b" else "a"

            Page(i, "", "https://${firstSubdomainFromGalleryId(hashPath2)}$secondSubdomain.hitomi.la/$path/$hashPath1/$hashPath2/$hash.$ext")
        }
    }

    // https://ltn.hitomi.la/common.js
    private fun firstSubdomainFromGalleryId(pathSegment: String): Char {
        var numberOfFrontends = 3
        var g = pathSegment.toInt(16)
        if (g < 0x30) numberOfFrontends = 2
        if (g < 0x09) g = 1

        return (97 + g.rem(numberOfFrontends)).toChar()
    }

    override fun imageRequest(page: Page): Request {
        val request = super.imageRequest(page)
        val hlId = request.url().pathSegments().let {
            it[it.lastIndex - 1]
        }
        return request.newBuilder()
            .header("Referer", "$BASE_URL/reader/$hlId.html")
            .build()
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used")

    companion object {
        private const val INDEX_VERSION_CACHE_TIME_MS = 1000 * 60 * 10
        private const val PAGE_SIZE = 25

        const val PREFIX_ID_SEARCH = "id:"

        // From HitomiSearchMetaData
        const val LTN_BASE_URL = "https://ltn.hitomi.la"
        const val BASE_URL = "https://hitomi.la"

        // Preferences
        private const val WEBP_PREF_KEY = "HITOMI_WEBP"
        private const val WEBP_PREF_TITLE = "Webp pages"
        private const val WEBP_PREF_SUMMARY = "Download webp pages instead of jpeg (when available)"
        private const val WEBP_PREF_DEFAULT_VALUE = true

        private const val COVER_PREF_KEY = "HITOMI_COVERS"
        private const val COVER_PREF_TITLE = "Use HQ covers"
        private const val COVER_PREF_SUMMARY = "See HQ covers while browsing"
        private const val COVER_PREF_DEFAULT_VALUE = true
    }

    // Preferences

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val webpPref = CheckBoxPreference(screen.context).apply {
            key = "${WEBP_PREF_KEY}_$lang"
            title = WEBP_PREF_TITLE
            summary = WEBP_PREF_SUMMARY
            setDefaultValue(WEBP_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${WEBP_PREF_KEY}_$lang", checkValue).commit()
            }
        }

        val coverPref = CheckBoxPreference(screen.context).apply {
            key = "${COVER_PREF_KEY}_$lang"
            title = COVER_PREF_TITLE
            summary = COVER_PREF_SUMMARY
            setDefaultValue(COVER_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${COVER_PREF_KEY}_$lang", checkValue).commit()
            }
        }

        screen.addPreference(webpPref)
        screen.addPreference(coverPref)
    }

    override fun setupPreferenceScreen(screen: AndroidXPreferenceScreen) {
        val webpPref = AndroidXCheckBoxPreference(screen.context).apply {
            key = "${WEBP_PREF_KEY}_$lang"
            title = WEBP_PREF_TITLE
            summary = WEBP_PREF_SUMMARY
            setDefaultValue(WEBP_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${WEBP_PREF_KEY}_$lang", checkValue).commit()
            }
        }

        val coverPref = AndroidXCheckBoxPreference(screen.context).apply {
            key = "${COVER_PREF_KEY}_$lang"
            title = COVER_PREF_TITLE
            summary = COVER_PREF_SUMMARY
            setDefaultValue(COVER_PREF_DEFAULT_VALUE)

            setOnPreferenceChangeListener { _, newValue ->
                val checkValue = newValue as Boolean
                preferences.edit().putBoolean("${COVER_PREF_KEY}_$lang", checkValue).commit()
            }
        }

        screen.addPreference(webpPref)
        screen.addPreference(coverPref)
    }

    private fun hitomiAlwaysWebp(): Boolean = preferences.getBoolean("${WEBP_PREF_KEY}_$lang", WEBP_PREF_DEFAULT_VALUE)
    private fun useHqThumbPref(): Boolean = preferences.getBoolean("${COVER_PREF_KEY}_$lang", COVER_PREF_DEFAULT_VALUE)
}
