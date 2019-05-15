package eu.kanade.tachiyomi.extension.ko.navercomic

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat

abstract class NaverComicBase(protected val mType: String) : ParsedHttpSource() {
    override val lang: String = "ko"
    override val baseUrl: String = "https://comic.naver.com"
    private val mobileUrl = "https://m.comic.naver.com"
    override val supportsLatest = true
    override val client: OkHttpClient = network.client

    private val mobileHeaders = super.headersBuilder()
            .add("Referer", mobileUrl)
            .build()

    override fun searchMangaSelector() = ".resultList > li h5 > a"
    override fun searchMangaNextPageSelector() = ".paginate a.next"
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("search.nhn?m=$mType&keyword=$query&type=title&page=$page")
    override fun searchMangaFromElement(element: Element): SManga {
        val url = element.attr("href").substringBefore("&week").substringBefore("&listPage=")
        val manga = SManga.create()
        manga.url = url
        manga.title = element.text().trim()
        return manga
    }

    override fun chapterListSelector() = "#ct > .toon_lst.lst2 > li > div a"

    // Need to override because the chapter list is paginated.
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = fetchChapterList(manga, 1)

    private fun fetchChapterList(manga: SManga, page: Int,
                                 pastChapters: List<SChapter> = emptyList()): Observable<List<SChapter>> {
        val chapters = pastChapters.toMutableList()
        fun isSamePage(list: List<SChapter>): Boolean = try {
            chapters.last().url == list.last().url
        } catch (_: Exception) {
            false
        }

        return fetchChapterListPage(manga, page)
                .flatMap {
                    if (isSamePage(it)) {
                        Observable.just(chapters)
                    } else {
                        chapters += it
                        fetchChapterList(manga, page + 1, chapters)
                    }
                }
    }

    private fun fetchChapterListPage(manga: SManga, page: Int): Observable<List<SChapter>> {
        return client.newCall(chapterPagedListRequest(manga, page))
                .asObservableSuccess()
                .map { response ->
                    chapterListParse(response)
                }
    }

    override fun chapterListRequest(manga: SManga): Request {
        return chapterPagedListRequest(manga, 1)
    }

    open fun chapterPagedListRequest(manga: SManga, page: Int): Request {
        return GET("$mobileUrl${manga.url}&page=$page", mobileHeaders)
    }

    override fun chapterFromElement(element: Element): SChapter {
        val rawName = element.select(".toon_name > strong").last().ownText()
        val url = element.attr("href").substringBefore("&week").substringBefore("&listPage")

        val chapter = SChapter.create()
        chapter.url = url
        chapter.chapter_number = parseChapterNumber(rawName)
        chapter.name = rawName
        chapter.date_upload = parseChapterDate(element.select(".toon_detail_info .if1").last().text().trim())
        return chapter
    }

    protected fun parseChapterNumber(name: String): Float {
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

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("YY.MM.dd").parse(date).time
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val element = document.select(".comicinfo")
        val titleElement = element.select(".detail > h2")

        val manga = SManga.create()
        manga.title = titleElement.first().ownText().trim()
        manga.author = titleElement.select("span").text().trim()
        manga.description = document.select(".comicinfo > p").text().trim()
        manga.thumbnail_url = element.select(".thumb > a > img").last().attr("src")
        return manga
    }


    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()
        try {
            document.select(".wt_viewer img")
                    .map {
                        it.attr("src")
                    }
                    .forEach {
                        pages.add(Page(pages.size, "", it))
                    }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return pages
    }

    //We are able to get the image URL directly from the page list
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("This method should not be called!")

    override fun getFilterList() = FilterList()
}

abstract class NaverComicChallengeBase(mType: String) : NaverComicBase(mType) {
    override fun popularMangaSelector() = ".weekchallengeBox tbody td:not([class])"
    override fun popularMangaNextPageSelector(): String? = ".paginate .page_wrap a.next"
    override fun popularMangaFromElement(element: Element): SManga {
        val thumb = element.select("a img").first().attr("src")
        val title = element.select(".challengeTitle a").first()

        val manga = SManga.create()
        manga.url = title.attr("href").substringBefore("&week")
        manga.title = title.text().trim()
        manga.thumbnail_url = thumb
        return manga
    }

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
}