package eu.kanade.tachiyomi.extension.ko.navercomic

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat

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

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn")
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn?m=main&order=Update")
}

class NaverChallenge : NaverComicChallengeBase("challenge") {
    override val name = "Naver Webtoon Challenge"
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn")
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/$mType.nhn?m=list&order=Update")

    // Need to override again because there's no mobile page.
    override fun chapterPagedListRequest(manga: SManga, page: Int): Request {
        return GET("$baseUrl${manga.url}&page=$page")
    }

    override fun chapterListSelector() = ".viewList > tbody > tr:not([class])"

    override fun chapterFromElement(element: Element): SChapter {
        val nameElement = element.select("td.title > a").first()
        val rawName = nameElement.text().trim()

        val chapter = SChapter.create()
        chapter.url = nameElement.attr("src")
        chapter.chapter_number = parseChapterNumber(rawName)
        chapter.name = rawName
        chapter.date_upload = parseChapterDate(element.select("td.num").last().text().trim())
        return chapter
    }

    @SuppressLint("SimpleDateFormat")
    private fun parseChapterDate(date: String): Long {
        return try {
            SimpleDateFormat("yyyy.MM.dd").parse(date).time
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }
}