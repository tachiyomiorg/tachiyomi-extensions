package eu.kanade.tachiyomi.extension.en.questionablecontent

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.util.Date
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class QuestionableContent : ParsedHttpSource() {

    override val name = "Questionable Content"

    override val baseUrl = "https://www.questionablecontent.net"

    override val lang = "en"

    override val supportsLatest = false

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        val manga = SManga.create().apply {
            title = "Questionable Content"
            artist = "Jeph Jacques"
            author = "Jeph Jacques"
            status = SManga.ONGOING
            url = "/archive.php"
            description = "An internet comic strip about romance and robots"
            thumbnail_url = "https://i.ibb.co/ZVL9ncS/qc-teh.png"
            initialized = true
        }

        return Observable.just(MangasPage(arrayListOf(manga), false))
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.just(MangasPage(emptyList(), false))

    override fun fetchMangaDetails(manga: SManga) = fetchPopularManga(1).map { it.mangas.first() }

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response).distinct()
        // set date of most recent chapter to today, use SharedPreferences so that we aren't changing it needlessly on refreshes
        if (chapters.first().url != preferences.getString(LAST_CHAPTER_URL, null)) {
            val date = Date().time
            chapters.first().date_upload = date
            preferences.edit().putString(LAST_CHAPTER_URL, chapters.first().url).apply()
            preferences.edit().putLong(LAST_CHAPTER_DATE, date).apply()
        } else {
            chapters.first().date_upload = preferences.getLong(LAST_CHAPTER_DATE, 0L)
        }
        return chapters
    }

    override fun chapterListSelector() = """div#container a[href^="view.php?comic="]"""

    override fun chapterFromElement(element: Element): SChapter {
        val urlregex = """view\.php\?comic=(.*)""".toRegex()
        val chapterUrl = element.attr("href")
        val number = urlregex.find(chapterUrl)!!.groupValues[1]

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain("/$chapterUrl")
        chapter.name = element.text()
        chapter.chapter_number = number.toFloat()
        return chapter
    }

    override fun pageListParse(document: Document) = document.select("#strip").mapIndexed { i, element -> Page(i, "", baseUrl + element.attr("src").substring(1)) }

    companion object {
        private const val LAST_CHAPTER_URL = "QC_LAST_CHAPTER_URL"
        private const val LAST_CHAPTER_DATE = "QC_LAST_CHAPTER_DATE"
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun popularMangaSelector(): String = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun searchMangaSelector(): String = throw Exception("Not used")

    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw Exception("Not used")

    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun mangaDetailsParse(document: Document): SManga = throw Exception("Not used")

    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")

    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    override fun latestUpdatesSelector(): String = throw Exception("Not used")
}
