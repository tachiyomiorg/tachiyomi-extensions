package eu.kanade.tachiyomi.extension.tr.turkcemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class TurkceManga : Madara("Türkçe Manga", "https://turkcemanga.com", "tr") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=views", headers)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=latest", headers)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)
}
