package eu.kanade.tachiyomi.extension.ko.jmana

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.EditTextPreference
import android.support.v7.preference.PreferenceScreen
import android.widget.Toast
import eu.kanade.tachiyomi.extension.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * JMana Source
 **/
class JMana : ConfigurableSource, ParsedHttpSource() {
    override val name = "JMana"
    override val baseUrl: String by lazy { getPrefBaseUrl()!!.removeSuffix("/") }
    override val lang: String = "ko"
    override val supportsLatest = true
    override val client: OkHttpClient = network.cloudflareClient

    private fun String.cleaned() = this.replace(" ", "%20").replace(Regex("/[0-9]+(?!.*?/)"), "")

    override fun popularMangaSelector() = "div.conts > ul > li a"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("href").cleaned())
            title = element.select("span.price").text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")

    // Do not add page parameter if page is 1 to prevent tracking.
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comic_main_frame?tag=null&keyword=null&chosung=null&page=${page - 1}", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }

        // Can not detect what page is last page but max mangas are 15 per page.
        val hasNextPage = mangas.size == 15

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")
    override fun searchMangaParse(response: Response) = popularMangaParse(response)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/comic_main_frame?page=${page - 1}&keyword=$query", headers)

    override fun mangaDetailsParse(document: Document): SManga {
        val descriptionElement = document.select(".media > .row > .media-body.col-9 > div")

        val manga = SManga.create()
        descriptionElement
            .map { it.text() }
            .forEach { text ->
                when {
                    DETAIL_TITLE in text -> manga.title = text.substringAfter(DETAIL_TITLE).trim()
                    DETAIL_AUTHOR in text -> manga.author = text.substringAfter(DETAIL_AUTHOR).trim()
                    DETAIL_GENRE in text -> manga.genre = text.substringAfter("장르 : [").substringBefore("]").trim()
                }
            }
        manga.description = descriptionElement.select("#desc").text().substringAfter(DETAIL_DESCRIPTION).trim()
        manga.thumbnail_url = document.select("div.media-left img").attr("abs:src")
        manga.status = SManga.UNKNOWN
        return manga
    }

    override fun chapterListSelector() = "div.section > .post > .post-content-list"

    override fun chapterFromElement(element: Element): SChapter {
        val linkElement = element.select(".entry-title a")
        val rawName = linkElement.text()

        return SChapter.create().apply {
            url = HttpUrl.parse(linkElement.attr("abs:href"))!!.let { "${it.encodedPath()}?${it.encodedQuery()}" }
            chapter_number = parseChapterNumber(rawName)
            name = rawName.trim()
            date_upload = parseChapterDate(element.select("li.publish-date span").last().text())
        }
    }

    private fun parseChapterNumber(name: String): Float {
        try {
            if (name.contains("[단편]")) return 1f
            // `특별` means `Special`, so It can be buggy. so pad `편`(Chapter) to prevent false return
            if (name.contains("번외") || name.contains("특별편")) return -2f
            val regex = Regex("([0-9]+)(?:[-.]([0-9]+))?(?:화)")
            val (ch_primal, ch_second) = regex.find(name)!!.destructured
            return (ch_primal + if (ch_second.isBlank()) "" else ".$ch_second").toFloatOrNull() ?: -1f
        } catch (e: Exception) {
            e.printStackTrace()
            return -1f
        }
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(date)?.time ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("ul.view img").mapIndexed { i, img ->
            Page(i, "", if (img.hasAttr("data-src")) img.attr("abs:data-src") else img.attr("abs:src"))
        }
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comic_recent", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = mutableListOf<SManga>()
        val lastPage = document.select("select#page option:last-of-type").text()
        val currentPage = document.select("select#page option[selected]").text()

        document.select(latestUpdatesSelector()).map { mangas.add(latestUpdatesFromElement(it)) }

        return MangasPage(mangas.distinctBy { it.url }, currentPage < lastPage)
    }

    override fun latestUpdatesSelector() = "div.contents div.detail ul:not(:first-of-type) li:has(a.btn)"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("a.btn").attr("href").cleaned())
            title = element.select("div.info a").text()
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("This method should not be called!")

    // We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    companion object {
        const val DETAIL_TITLE = "제목 : "
        const val DETAIL_GENRE = "장르 : "
        const val DETAIL_AUTHOR = "작가 : "
        const val DETAIL_DESCRIPTION = "설명 : "
        const val DEFAULT_BASEURL = "https://003.jmana2.net"
        private const val BASE_URL_PREF_TITLE = "Override BaseUrl"
        private const val BASE_URL_PREF = "overrideBaseUrl_v${BuildConfig.VERSION_NAME}"
        private const val BASE_URL_PREF_SUMMARY = "For temporary uses. Update extension will erase this setting."
        private const val RESTART_TACHIYOMI = "Restart Tachiyomi to apply new setting."
    }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val baseUrlPref = androidx.preference.EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(DEFAULT_BASEURL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $DEFAULT_BASEURL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val baseUrlPref = EditTextPreference(screen.context).apply {
            key = BASE_URL_PREF_TITLE
            title = BASE_URL_PREF_TITLE
            summary = BASE_URL_PREF_SUMMARY
            this.setDefaultValue(DEFAULT_BASEURL)
            dialogTitle = BASE_URL_PREF_TITLE
            dialogMessage = "Default: $DEFAULT_BASEURL"

            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val res = preferences.edit().putString(BASE_URL_PREF, newValue as String).commit()
                    Toast.makeText(screen.context, RESTART_TACHIYOMI, Toast.LENGTH_LONG).show()
                    res
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(baseUrlPref)
    }

    private fun getPrefBaseUrl() = preferences.getString(BASE_URL_PREF, DEFAULT_BASEURL)
}
