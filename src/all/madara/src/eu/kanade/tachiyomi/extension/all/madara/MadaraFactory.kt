package eu.kanade.tachiyomi.extension.all.madara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

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
        MangaKomi(),
        MangaSY(),
        ManwhaClub(),
        WuxiaWorld(),
        WordRain(),
        YoManga(),
        ManyToon(),
        ChibiManga(),
        ZinManga(),
        ManwahentaiMe(),
        Manga3asq(),
        NManhwa(),
        Indiancomicsonline(),
        AdonisFansub(),
        GetManhwa(),
        AllPornComic(),
        Milftoon(),
        ToonManga(),
        Hiperdex()
    )
}

class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class NinjaScans : Madara("NinjaScans", "https://ninjascans.com", "en")
class ReadManhua : Madara("ReadManhua", "https://readmanhua.net", "en",
    dateFormat = SimpleDateFormat("dd MMM yy", Locale.US)) {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class ZeroScans : Madara("ZeroScans", "https://zeroscans.com", "en")
class IsekaiScanCom : Madara("IsekaiScan.com", "https://isekaiscan.com/", "en")
class HappyTeaScans : Madara("Happy Tea Scans", "https://happyteascans.com/", "en")
class JustForFun : Madara("Just For Fun", "https://just-for-fun.ru/", "ru",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)) {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class AoCTranslations : Madara("Agent of Change Translations", "https://aoc.moe/", "en") {
    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val document = response.asJsoup()

        // For when it's a normal chapter list
        if (document.select(chapterListSelector()).hasText()) {
            document.select(chapterListSelector())
                .map { chapters.add(chapterFromElement(it)) }
        } else {
            // For their "fancy" volume/chapter lists
            document.select("div.wpb_wrapper:contains(volume) a")
                .filter { it.attr("href").contains(baseUrl) && !it.attr("href").contains("imgur") }
                .map { it ->
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

class KomikGo : Madara("KomikGo", "https://komikgo.com", "id") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class LuxyScans : Madara("Luxy Scans", "https://luxyscans.com/", "en")
class TritiniaScans : Madara("Tritinia Scans", "http://ghajik.ml/", "en",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?m_orderby=latest", headers)
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun popularMangaNextPageSelector(): String? = null
}

class TsubakiNoScan : Madara("Tsubaki No Scan", "https://tsubakinoscan.com/",
    "fr", dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))

class YokaiJump : Madara("Yokai Jump", "https://yokaijump.fr/", "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)) {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class ZManga : Madara("ZManga", "https://zmanga.org/", "es") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class MangazukiMe : Madara("Mangazuki.me", "https://mangazuki.me/", "en")
class MangazukiOnline : Madara("Mangazuki.online", "https://www.mangazuki.online/", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class MangazukiClubJP : Madara("Mangazuki.club", "https://mangazuki.club/", "ja") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class MangazukiClubKO : Madara("Mangazuki.club", "https://mangazuki.club/", "ko") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class FirstKissManga : Madara("1st Kiss", "https://1stkissmanga.com/", "en") {
    override val pageListParseSelector = "div.reading-content img"
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
    private val cdnUrl = "cdn.1stkissmanga.com"
    override fun imageRequest(page: Page): Request {
        val cdnHeaders = Headers.Builder().apply {
            add("Referer", baseUrl)
            add("Host", cdnUrl)
        }.build()
        return if (page.imageUrl!!.contains(cdnUrl)) GET(page.imageUrl!!, cdnHeaders) else GET(page.imageUrl!!, headers)
    }
}

class MangaKomi : Madara("MangaKomi", "https://mangakomi.com/", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class MangaSY : Madara("Manga SY", "https://www.mangasy.com/", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class ManwhaClub : Madara("Manwha Club", "https://manhwa.club/", "en")
class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site/", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=latest", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = super.searchMangaRequest(page, "$query comics", filters)
    override fun popularMangaNextPageSelector() = "div.nav-previous.float-left"
}

class WordRain : Madara("WordRain Translation", "https://wordrain69.com", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga-genre/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga-genre/manga/page/$page/?m_orderby=latest", headers)
    override fun searchMangaParse(response: Response): MangasPage {

        val url = HttpUrl.parse(response.request().url().toString())!!.newBuilder()
            .addQueryParameter("genre[]", "manga").build()
        val request: Request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        val res: Response = call.execute()
        return super.searchMangaParse(res)
    }
}

class YoManga : Madara("Yo Manga", "https://yomanga.info/", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class ManyToon : Madara("ManyToon", "https://manytoon.com/", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class ChibiManga : Madara("Chibi Manga", "http://www.cmreader.info/", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class ZinManga : Madara("Zin Translator", "https://zinmanga.com/", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class ManwahentaiMe : Madara("Manwahentai.me", "https://manhwahentai.me", "en")

class Manga3asq : Madara("مانجا العاشق", "https://3asq.org", "ar") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class NManhwa : Madara("N Manhwa", "https://nmanhwa.com", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}

class Indiancomicsonline : Madara("Indian Comics Online", "http://www.indiancomicsonline.com", "hi")

class AdonisFansub : Madara("Adonis Fansub", "https://manga.adonisfansub.com", "tr") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}
class GetManhwa : Madara("GetManhwa", "https://getmanhwa.co", "en") {
    override fun searchMangaNextPageSelector() = "nav.navigation-ajax"
}
class AllPornComic : Madara("AllPornComic", "https://allporncomic.com", "en") {
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
    override fun searchMangaNextPageSelector() = "a[rel=next]"
    override fun getGenreList() = listOf(
        Genre("3D", "3d"),
        Genre("Hentai", "hentai"),
        Genre("Western", "western")
    )
}
class Milftoon : Madara("Milftoon", "https://milftoon.xxx", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=latest", headers)
}

class ToonManga : Madara("ToonManga", "https://toonmanga.com/", "en")

class Hiperdex : Madara("Hiperdex", "https://hiperdex.com", "en") {
    override fun getGenreList() = listOf(
        Genre( "Adult",  "adult"),
        Genre( "Action",  "action"),
        Genre( "Adventure",  "adventure"),
        Genre( "Bully",  "bully"),
        Genre( "Comedy",  "comedy"),
        Genre( "Drama",  "drama"),
        Genre( "Ecchi",  "ecchi"),
        Genre( "Fantasy",  "fantasy"),
        Genre( "Gender Bender",  "gender-bender"),
        Genre( "Harem",  "harem"),
        Genre( "Historical",  "historical"),
        Genre( "Horror",  "horror"),
        Genre( "Isekai",  "isekai"),
        Genre( "Josei",  "josei"),
        Genre( "Martial Arts",  "martial-arts"),
        Genre( "Mature",  "mature"),
        Genre( "Mystery",  "mystery"),
        Genre( "Psychological",  "psychological"),
        Genre( "Romance",  "romance"),
        Genre( "School Life",  "school-life"),
        Genre( "Sci-Fi",  "sci-fi"),
        Genre( "Seinen",  "seinen"),
        Genre( "Shoujo",  "shoujo"),
        Genre( "Shounen",  "shounen"),
        Genre( "Slice of Life",  "slice-of-life"),
        Genre( "Smut",  "smut"),
        Genre( "Sports",  "sports"),
        Genre( "Supernatural",  "supernatural"),
        Genre( "Thriller",  "thriller"),
        Genre( "Tragedy",  "tragedy"),
        Genre( "Yaoi",  "yaoi"),
        Genre( "Yuri",  "yuri")
    )
}
