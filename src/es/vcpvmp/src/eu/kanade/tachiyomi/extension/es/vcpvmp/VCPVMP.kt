package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.source.model.*
import okhttp3.Request
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

open class VCPVMP(override val name: String, override val baseUrl: String) : ParsedHttpSource() {
    override val lang = "es"

    override val supportsLatest = true

    override fun chapterFromElement(element: Element): SChapter {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun chapterListSelector(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun imageUrlParse(document: Document): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun latestUpdatesNextPageSelector(): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun latestUpdatesRequest(page: Int): Request {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun latestUpdatesSelector(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun mangaDetailsParse(document: Document): SManga {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun pageListParse(document: Document): List<Page> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun popularMangaFromElement(element: Element): SManga {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun popularMangaNextPageSelector(): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun popularMangaRequest(page: Int): Request {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun popularMangaSelector(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaFromElement(element: Element): SManga {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaNextPageSelector(): String? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun searchMangaSelector(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
