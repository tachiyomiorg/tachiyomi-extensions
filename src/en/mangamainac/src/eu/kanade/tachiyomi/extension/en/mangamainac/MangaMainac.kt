package eu.kanade.tachiyomi.extension.en.mangamainac

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.util.*

//MangaManiac is a network of sites built by Animemaniac.co.

class MangaMainac : ParsedHttpSource() {

    companion object {
        val sourceList = listOf<Pair<String,String>>(
            Pair("Boku No Hero Academia","https://w15.readheroacademia.com"),
            Pair("One Punch Man","https://w12.readonepunchman.net"),
            Pair("One Punch Man (webcomic)","https://onewebcomic.net"),
            Pair("Solo Leveling","https://sololeveling.net"),
            Pair("Jojolion","https://readjojolion.com"),
            Pair("Hajime no Ippo","https://readhajimenoippo.com"),
            Pair("Berserk","http://berserkmanga.net"),
            Pair("The Quintessential Quintuplets","https://5-toubunnohanayome.net"),
            Pair("Kaguya Wants to be Confessed To","https://kaguyasama.net"),
            Pair("Domestic Girlfriend","https://domesticgirlfriend.net"),
            Pair("Black Clover","https://w5.blackclovermanga.com"),
            Pair("One Piece","https://1piecemanga.net"),
            Pair("The Promised Neverland","https://neverlandmanga.net"),
            Pair("Shingeki no Kyojin","https://readshingekinokyojin.com"),
            Pair("Nanatsu no Taizai","https://w1.readnanatsutaizai.net")
        ).sortedBy { it.first }.distinctBy { it.second }

    }

    //Info

    override val name: String = "MangaMainac (Multiple Sites)"
    override val baseUrl: String = "about:blank"
    override val lang: String = "en"
    override val supportsLatest: Boolean = false

    //Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return Observable.just(MangasPage(sourceList.map { popularMangaFromPair(it.first,it.second ) }, false))
    }
    private fun popularMangaFromPair(name: String, sourceurl: String ): SManga = SManga.create().apply {
        title = name
        url = sourceurl
    }
    override fun popularMangaRequest(page: Int): Request = throw Exception("Not used")
    override fun popularMangaNextPageSelector(): String? = throw Exception("Not used")
    override fun popularMangaSelector(): String = throw Exception("Not used")
    override fun popularMangaFromElement(element: Element) = throw Exception("Not used")


    //Latest
    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")
    override fun latestUpdatesNextPageSelector(): String? = throw Exception("Not used")
    override fun latestUpdatesSelector(): String = throw Exception("Not used")
    override fun latestUpdatesFromElement(element: Element): SManga = throw Exception("Not used")

    //Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList)= throw Exception("No Search Function")
    override fun searchMangaNextPageSelector() = throw Exception("Not used")
    override fun searchMangaSelector() = throw Exception("Not used")
    override fun searchMangaFromElement(element: Element) = throw Exception("Not used")

    //Get Override

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET( manga.url, headers)
    }
    override fun chapterListRequest(manga: SManga): Request {
        return GET( manga.url, headers)
    }
    override fun pageListRequest(chapter: SChapter): Request {
        return GET( chapter.url, headers)
    }

    //Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val info = document.select(".intro_content").text()
        title = document.select(".intro_content h2").text()
        author = if ("Author" in info) substringextract(info,"Author(s):","Released") else null
        artist = author
        genre = if ("Genre" in info) substringextract(info, "Genre(s):","Status") else null
        status = when (substringextract(info, "Status:","(")) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        description = if ("Description" in info) info.substringAfter("Description:").trim() else null
        thumbnail_url = document.select(".mangainfo_body img").attr("src")
    }
    private fun substringextract(text: String, start:String, end:String): String = text.substringAfter(start).substringBefore(end).trim()

    //Chapters

    override fun chapterListSelector(): String = ".chap_tab tr"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a").text()
        url = element.select("a").attr("abs:href")
        date_upload = parseRelativeDate(element.select("#time").text().substringBefore(" ago"))
    }

    // Subtract relative date (e.g. posted 3 days ago)
    private fun parseRelativeDate(date: String): Long {
        val calendar = Calendar.getInstance()

        if (date.contains("yesterday")) {
            calendar.apply{add(Calendar.DAY_OF_MONTH, -1)}
        } else {
            val trimmedDate = date.replace("one", "1").removeSuffix("s").split(" ")

            when (trimmedDate[1]) {
                "year" -> calendar.apply { add(Calendar.YEAR, -trimmedDate[0].toInt()) }
                "month" -> calendar.apply { add(Calendar.MONTH, -trimmedDate[0].toInt()) }
                "week" -> calendar.apply { add(Calendar.WEEK_OF_MONTH, -trimmedDate[0].toInt()) }
                "day" -> calendar.apply { add(Calendar.DAY_OF_MONTH, -trimmedDate[0].toInt()) }
            }
        }

        return calendar.timeInMillis
    }

    //Pages

    override fun pageListParse(document: Document): List<Page> = mutableListOf<Page>().apply {
        document.select(".img_container img").forEach { img ->
            add(Page(size,"",img.attr("src")))
        }
    }

    override fun imageUrlParse(document: Document): String = throw Exception("Not Used")

}


