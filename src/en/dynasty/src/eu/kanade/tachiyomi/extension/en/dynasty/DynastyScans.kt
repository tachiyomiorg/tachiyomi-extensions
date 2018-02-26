package eu.kanade.tachiyomi.extension.en.dynasty

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.text.SimpleDateFormat
import java.util.*

abstract class DynastyScans : ParsedHttpSource() {

    override val baseUrl = "http://dynasty-scans.com"

    abstract fun popularMangaInitialUrl(): String

    override val lang = "en"

    override val supportsLatest = false

    var parent: List<Node> = ArrayList()

    var list = InternalList(ArrayList(), "")

    var imgList = InternalList(ArrayList(), "")

    var _valid: Validate = Validate(false, -1)

    override fun popularMangaRequest(page: Int): Request {
        return GET(popularMangaInitialUrl(), headers)
    }

    override fun popularMangaSelector() = "ul.thumbnails > li.span2"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("href"))
        manga.title = element.select("div.caption").text()
        return manga
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select(popularMangaSelector()).map { element ->
            popularMangaFromElement(element)
        }
        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = "a.name"

    override fun searchMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.text()
        return manga
    }

    override fun searchMangaNextPageSelector() = "div.pagination > ul > li.active + li > a"

    private fun buildListfromResponse(): List<Node> {
        return client.newCall(Request.Builder().headers(headers)
                .url(popularMangaInitialUrl()).build()).execute().asJsoup()
                .select("div#main").filter { it.hasText() }.first().childNodes()
    }

    protected fun parseThumbnail(manga: SManga) {
        if (_valid.isManga) manga.thumbnail_url = baseUrl + imgList[_valid.pos].substringBefore('?')
    }

    protected fun parseHeader(document: Document, manga: SManga): Boolean {
        val elements = document.select("div.tags > h2.tag-title").first().getElementsByTag("a")
        if (elements.isEmpty()) {
            return false
        }
        if (elements.lastIndex == 0) {
            manga.author = elements[0].text()
        } else {
            manga.artist = elements[0].text()
            manga.author = elements[1].text()
        }
        manga.status = document.select("div.tags > h2.tag-title > small").text().let {
            when {
                it.contains("Ongoing") -> SManga.ONGOING
                it.contains("Completed") -> SManga.COMPLETED
                it.contains("Licensed") -> SManga.LICENSED
                else -> SManga.UNKNOWN
            }
        }
        return true
    }

    protected fun parseGenres(document: Document, manga: SManga) {
        manga.genre = ""
        val glist = document.select("div.tags > div.tag-tags").first().getElementsByTag("a")
        if (!glist.isEmpty()) {
            for (g in glist) {
                val s = g.text()
                manga.genre += if (glist.last() == (g)) s else "$s, "
            }
        }
    }

    protected fun parseDescription(document: Document, manga: SManga) {
        manga.description = document.select("div.tags > div.row div.description").text()
    }

    private fun getValid(manga: SManga): Validate {
        if (parent.isEmpty()) parent = buildListfromResponse()
        if (list.isEmpty()) list = InternalList(parent, "href")
        if (imgList.isEmpty()) imgList = InternalList(parent, "src")
        val pos = list.indexOf(manga.url.substringBeforeLast("/") + "/" + Uri.encode(manga.url.substringAfterLast("/")))
        return Validate((pos > -1), pos)
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        _valid = getValid(manga)
        return manga
    }

    override fun chapterListSelector() = "div.span10 > dl.chapter-list > dd"

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).asReversed()
    }

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val nodes = InternalList(element.childNodes(), "text")

        chapter.setUrlWithoutDomain(element.select("a.name").attr("href"))
        chapter.name = nodes[0]
        if (nodes.contains(" by ")) {
            chapter.name += " by ${nodes[nodes.indexOfPartial(" by ") + 1]}"
            if (nodes.contains(" and ")) {
                chapter.name += " and ${nodes[nodes.indexOfPartial(" and ") + 1]}"
            }
        }
        chapter.date_upload = nodes[nodes.indexOfPartial("released")].let {
            SimpleDateFormat("MMM dd yy", Locale.ENGLISH).parse(it.substringAfter("released ").replace("\'", "")).time
        }
        return chapter
    }

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        try {
            val imageUrl = document.select("script").last().html().substringAfter("var pages = [").substringBefore("];")
            var imageUrls = JSONArray("[$imageUrl]")

            (0..imageUrls.length() - 1)
                    .map { imageUrls.getJSONObject(it) }
                    .map { baseUrl + it.get("image") }
                    .forEach { pages.add(Page(pages.size, "", it)) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return pages
    }

    class InternalList : ArrayList<String> {

        constructor(nodes: List<Node>, type: String) {
            if (type == "text") {
                for (node in nodes) {
                    if (node is TextNode) {
                        if (node.text() != " " && !node.text().contains("\n")) {
                            this.add(node.text())
                        }
                    } else if (node is Element) this.add(node.text())
                }
            }
            if (type == "src") {
                nodes
                        .filter { it is Element && it.hasClass("thumbnails") }
                        .flatMap { it.childNodes() }
                        .filterIsInstance<Element>()
                        .filter { it.hasClass("span2") }
                        .forEach { this.add(it.child(0).child(0).attr(type)) }
            }
            if (type == "href") {
                nodes
                        .filter { it is Element && it.hasClass("thumbnails") }
                        .flatMap { it.childNodes() }
                        .filterIsInstance<Element>()
                        .filter { it.hasClass("span2") }
                        .forEach { this.add(it.child(0).attr(type)) }
            }
        }

        fun indexOfPartial(partial: String): Int {
            return (0..this.lastIndex).firstOrNull { this[it].contains(partial) }
                    ?: -1
        }

        fun getItem(partial: String): String {
            return (0..this.lastIndex)
                    .firstOrNull { super.get(it).contains(partial) }
                    ?.let { super.get(it) }
                    ?: ""
        }
    }

    class Validate(_isManga: Boolean, _pos: Int) {
        val isManga = _isManga
        val pos = _pos
    }

    override fun popularMangaNextPageSelector() = ""
    override fun latestUpdatesSelector() = ""
    override fun latestUpdatesNextPageSelector() = ""
    override fun imageUrlParse(document: Document): String = ""
    override fun latestUpdatesFromElement(element: Element): SManga {
        return popularMangaFromElement(element)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return popularMangaRequest(page)
    }

}