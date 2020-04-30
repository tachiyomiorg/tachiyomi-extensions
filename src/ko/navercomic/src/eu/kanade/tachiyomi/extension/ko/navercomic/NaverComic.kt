package eu.kanade.tachiyomi.extension.ko.navercomic

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import okhttp3.Response
import org.jsoup.nodes.Element

class NaverWebtoon : NaverComicBase("webtoon") {
    override val name = "Naver Webtoon"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$mType/weekday.nhn")
    override fun popularMangaSelector() = ".list_area.daily_all .col ul > li"
    override fun popularMangaNextPageSelector() = null
    override fun popularMangaFromElement(element: Element): SManga {
        val thumb = element.select("div.thumb img").first().attr("src")
        val title = element.select("a.title").first()

        val manga = SManga.create()
        manga.url = title.attr("href").substringBefore("&week")
        manga.title = title.text().trim()
        manga.thumbnail_url = thumb
        return manga
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$mType/weekday.nhn?order=Update")
    override fun latestUpdatesSelector() = ".list_area.daily_all .col.col_selected ul > li"
    override fun latestUpdatesNextPageSelector() = null
    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)
}

class NaverBestChallenge : NaverComicChallengeBase("bestChallenge") {
    override val name = "Naver Webtoon Best Challenge"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn?m=main&order=StarScore")
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn?m=main&order=Update")
}

class NaverChallenge : NaverComicChallengeBase("challenge") {
    override val name = "Naver Webtoon Challenge"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn")
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn?m=list&order=Update")

    // Chapter list is paginated, but there are no mobile pages to work with
    override fun chapterListRequest(manga: SManga) = GET("$baseUrl${manga.url}", headers)

    override fun chapterListSelector() = "tbody tr:not([class])"

    override fun chapterListParse(response: Response): List<SChapter> {
        var document = response.asJsoup()
        val chapters = mutableListOf<SChapter>()
        document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
        while (document.select(paginationNextPageSelector).hasText()) {
            document.select(paginationNextPageSelector).let {
                document = client.newCall(GET(it.attr("abs:href"))).execute().asJsoup()
                document.select(chapterListSelector()).map { chapters.add(chapterFromElement(it)) }
            }
        }
        return chapters
    }

    override val paginationNextPageSelector = "div.paginate a.next"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        element.select("td + td a").let {
            val rawName = it.text()
            chapter.url = it.attr("href")
            chapter.chapter_number = parseChapterNumber(rawName)
            chapter.name = rawName
            chapter.date_upload = parseChapterDate(element.select("td.num").text().trim())
        }
        return chapter
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        return if (date.contains(":")) { Calendar.getInstance().timeInMillis
        } else {
            return try {
                SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).parse(date).time
            } catch (e: Exception) {
                e.printStackTrace()
                0
            }
        }
    }
}
