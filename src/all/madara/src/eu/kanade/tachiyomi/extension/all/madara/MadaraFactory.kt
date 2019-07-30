package eu.kanade.tachiyomi.extension.all.madara


import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import java.text.SimpleDateFormat
import java.util.*
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Mangasushi(),
        NinjaScans(),
        ReadManhua(),
        ZeroScans(),
        IsekaiScanCom(),
        HappyTeaScans(),
        JustForFun(),
        AoCTranslations(),
        Kanjiku(),
        KomikGo(),
        LuxyScans(),
        TritiniaScans(),
        TsubakiNoScan(),
        YokaiJump(),
        ZManga(),
        MangazukiMe(),
        MangazukiOnline(),
        MangazukiClubJP(),
        MangazukiClubKO(),
        FirstKissManga(),
        Mangalike(),
        MangaSY(),
        ManwhaClub(),
        WuxiaWorld(),
        YoManga(),
        ManyToon(),
        ChibiManga(),
        ZinManga()
    )
}

class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en")
class NinjaScans : Madara("NinjaScans", "https://ninjascans.com", "en")
class ReadManhua : Madara("ReadManhua", "https://readmanhua.net", "en", dateFormat = SimpleDateFormat("dd MMM yy", Locale.US))
class ZeroScans : Madara("ZeroScans", "https://zeroscans.com", "en")
class IsekaiScanCom : Madara("IsekaiScan.com", "http://isekaiscan.com/", "en")
class HappyTeaScans : Madara("Happy Tea Scans", "https://happyteascans.com/", "en")
class JustForFun : Madara("Just For Fun", "https://just-for-fun.ru/", "ru", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))
class AoCTranslations : Madara("Agent of Change Translations", "https://aoc.moe/", "en") {
    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()

        // For when it's a normal chapter list
        if (document.select(chapterListSelector()).hasText()) {
            document.select(chapterListSelector())
                .filter { it.select("a").attr("href").contains(baseUrl)}
                .map { chapters.add(chapterFromElement(it)) }
        } else {
        // For their "fancy" volume/chapter lists
            document.select("div.wpb_wrapper:contains(volume) a")
                .filter { it.attr("href").contains(baseUrl) && !it.attr("href").contains("imgur")}
                .map {
                    val chapter = SChapter.create()
                    if (it.attr("href").contains("volume")) {
                        val volume = it.attr("href").substringAfter("volume-").substringBefore("/")
                        val volChap = it.attr("href").substringAfter("volume-$volume/").substringBefore("/").replace("-", " ").capitalize()
                        chapter.name = "Volume $volume - $volChap"
                    } else {
                        chapter.name = it.attr("href").substringBefore("/p").substringAfterLast("/").replace("-", " ").capitalize()
                    }
                    it.attr("href").let {
                        chapter.setUrlWithoutDomain(it.substringBefore("?") + if (!it.endsWith("?style=list")) "?style=list" else "")
                        chapters.add(chapter)
                    }
                }
        }
        return chapters.reversed()
    }
}
class Kanjiku : Madara("Kanjiku", "https://kanjiku.net/", "de", dateFormat = SimpleDateFormat("dd. MMM yyyy", Locale.GERMAN))
class KomikGo : Madara("KomikGo", "https://komikgo.com/", "id")
class LuxyScans : Madara("Luxy Scans", "https://luxyscans.com/", "en")
class TritiniaScans : Madara("Tritinia Scans", "http://tritiniascans.ml/", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?m_orderby=latest", headers)
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun popularMangaNextPageSelector(): String? = null

    // Source's search seems broken (doesn't return results)
    private var searchQuery = ""

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        searchQuery = query.toLowerCase()
        return popularMangaRequest(1)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchMatches = mutableListOf<SManga>()
        val document = response.asJsoup()

        document.select(searchMangaSelector())
            .filter { it.text().toLowerCase().contains(searchQuery) }
            .map { searchMatches.add(searchMangaFromElement(it)) }

        return MangasPage(searchMatches, false)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "Not needed"

}
class TsubakiNoScan : Madara("Tsubaki No Scan", "https://tsubakinoscan.com/", "fr", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))
class YokaiJump : Madara("Yokai Jump", "https://yokaijump.fr/", "fr", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))
class ZManga : Madara("ZManga", "https://zmanga.org/", "es")
class MangazukiMe : Madara("Mangazuki.me", "https://mangazuki.me/", "en"){
    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        response.asJsoup().select(chapterListSelector())
            .filter { it.select("a").attr("href").contains(baseUrl) }
            .map { chapters.add(chapterFromElement(it)) }
        return chapters
    }
}
class MangazukiOnline : Madara("Mangazuki.online", "https://www.mangazuki.online/", "en") {
    override fun chapterListSelector() = "li.wp-manga-chapter:has(a)"
    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        response.asJsoup().select(chapterListSelector())
            .filter { it.select("a").attr("href").contains(baseUrl) }
            .map { chapters.add(chapterFromElement(it)) }
        return chapters
    }
}
class MangazukiClubJP : Madara("Mangazuki.club", "https://mangazuki.club/", "ja")
class MangazukiClubKO : Madara("Mangazuki.club", "https://mangazuki.club/", "ko")
class FirstKissManga : Madara("1st Kiss", "https://1stkissmanga.com/", "en") {
    override val pageListParseSelector = "div.reading-content img"
}
class Mangalike : Madara("Mangalike", "https://mangalike.net/", "en")
class MangaSY : Madara("Manga SY", "https://www.mangasy.com/", "en")
class ManwhaClub : Madara("Manwha Club", "https://manhwa.club/", "en") {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse("$baseUrl/page/$page/")!!.newBuilder()
        url.addQueryParameter("s", query)
        url.addQueryParameter("post_type", "wp-manga")
        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    if(filter.state.isNotBlank()) {
                        url.addQueryParameter("author", filter.state)
                    }
                }
                is ArtistFilter -> {
                    if(filter.state.isNotBlank()) {
                        url.addQueryParameter("artist", filter.state)
                    }
                }
                is YearFilter -> {
                    if(filter.state.isNotBlank()) {
                        url.addQueryParameter("release", filter.state)
                    }
                }
                is StatusFilter -> {
                    filter.state.forEach {
                        if (it.state) {
                            url.addQueryParameter("status[]", it.id)
                        }
                    }
                }
                is GenreFilter -> {
                    filter.state.forEach {
                        if (it.state) {
                            url.addQueryParameter("genre[]", it.id)
                        }
                    }
                }
                is OrderByFilter -> {
                    if(filter.state != 0) {
                        url.addQueryParameter("m_orderby", filter.toUriPart())
                    }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaNextPageSelector() = "div.nav-previous a"

    private class AuthorFilter : Filter.Text("Author")
    private class ArtistFilter : Filter.Text("Artist")
    private class YearFilter : Filter.Text("Year of Released")
    private class StatusFilter(status: List<Tag>) : Filter.Group<Tag>("Status", status)
    private class GenreFilter(genres: List<Tag>) : Filter.Group<Tag>("Genres", genres)
    private class OrderByFilter : UriPartFilter("Order By", arrayOf(
            Pair("<select>", ""),
            Pair("Latest", "latest"),
            Pair("A-Z", "alphabet"),
            Pair("Rating", "rating"),
            Pair("Trending", "trending"),
            Pair("Most Views", "views"),
            Pair("New", "new-manga")
    ))

    override fun getFilterList() = FilterList(
            AuthorFilter(),
            ArtistFilter(),
            YearFilter(),
            StatusFilter(getStatusList()),
            GenreFilter(getGenreList()),
            OrderByFilter()
    )

    private fun getStatusList() = listOf(
            Tag("end" , "Completed"),
            Tag("on-going" , "Ongoing"),
            Tag("canceled" , "Canceled"),
            Tag("on-hold" , "On Hold")
    )

    private fun getGenreList() = listOf(
            Tag("action" , "Action"),
            Tag("adventure" , "Adventure"),
            Tag("comedy" , "Comedy"),
            Tag("drama" , "Drama"),
            Tag("fantasy" , "Fantasy"),
            Tag("gender-bender" , "Gender bender"),
            Tag("gossip" , "Gossip"),
            Tag("harem" , "Harem"),
            Tag("historical" , "Historical"),
            Tag("horror" , "Horror"),
            Tag("incest" , "Incest"),
            Tag("martial-arts" , "Martial arts"),
            Tag("mecha" , "Mecha"),
            Tag("medical" , "Medical"),
            Tag("mystery" , "Mystery"),
            Tag("one-shot" , "One shot"),
            Tag("psychological" , "Psychological"),
            Tag("romance" , "Romance"),
            Tag("school-life" , "School LIfe"),
            Tag("sci-fi" , "Sci Fi"),
            Tag("slice-of-life" , "Slice of Life"),
            Tag("smut" , "Smut"),
            Tag("sports" , "Sports"),
            Tag("supernatural" , "Supernatural"),
            Tag("tragedy" , "Tragedy"),
            Tag("yaoi" , "Yaoi"),
            Tag("yuri" , "Yuri")
    )

    private open class UriPartFilter(displayName: String, val vals: Array<Pair<String, String>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private class Tag(val id: String, name: String) : Filter.CheckBox(name)
}
class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site/", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tag/webcomics/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tag/webcomics/page/$page/?m_orderby=latest", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/?s=$query&post_type=wp-manga&genre[]=webcomics", headers)
    override fun latestUpdatesNextPageSelector(): String? = "div.nav-previous"
    override fun popularMangaNextPageSelector(): String? = "div.nav-previous"
}
class YoManga : Madara("Yo Manga", "https://yomanga.info/", "en")
class ManyToon : Madara("ManyToon", "https://manytoon.com/", "en")
class ChibiManga : Madara("Chibi Manga", "http://www.cmreader.info/", "en")
class ZinManga : Madara("Zin Translator", "https://zinmanga.com/", "en")
