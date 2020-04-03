package eu.kanade.tachiyomi.extension.all.madara

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.*
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        Mangasushi(),
        NinjaScans(),
        ReadManhua(),
        IsekaiScanCom(),
        HappyTeaScans(),
        JustForFun(),
        AoCTranslations(),
        KomikGo(),
        LuxyScans(),
        TsubakiNoScan(),
        YokaiJump(),
        ZManga(),
        MangazukiMe(),
        MangazukiOnline(),
        MangazukiClubJP(),
        MangazukiClubKO(),
        FirstKissManga(),
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
        Indiancomicsonline(),
        AdonisFansub(),
        GetManhwa(),
        AllPornComic(),
        Milftoon(),
        Hiperdex(),
        DoujinHentai(),
        Azora(),
        KMangaIn(),
        HunterFansub(),
        MangaArabTeam(),
        NightComic(),
        Toonily(),
        PlotTwistScan(),
        MangaKomi(),
        Wakamics(),
        TeabeerComics(),
        KingzManga(),
        YaoiToshokan(),
        GoldenManga(),
        Mangalek(),
        AstralLibrary(),
        NovelFrance(),
        KlikManga(),
        MiracleScans(),
        Manhuasnet(),
        MangaLaw(),
        EarlyManga(),
        MangaTX(),
        ATMSubs(),
        OnManga(),
        MangaAction(),
        NijiTranslations(),
        IchirinNoHanaYuri(),
        LilyManga(),
        MangaBob(),
        ThreeSixtyFiveManga(),
        DisasterScans(),
        MangaKiss(),
        MangaDods(),
        MangaWOW(),
        MangaStream(),
        NeoxScanlator(),
        MangaLord(),
        PornComix(),
        MangaRead(),
        UnknownScans()
    )
}

class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en")

class NinjaScans : Madara("NinjaScans", "https://ninjascans.com", "en")

class ReadManhua : Madara("ReadManhua", "https://readmanhua.net", "en",
    dateFormat = SimpleDateFormat("dd MMM yy", Locale.US))

class IsekaiScanCom : Madara("IsekaiScan.com", "https://isekaiscan.com", "en")

class HappyTeaScans : Madara("Happy Tea Scans", "https://happyteascans.com", "en")

class JustForFun : Madara("Just For Fun", "https://just-for-fun.ru", "ru",
    dateFormat = SimpleDateFormat("yy.MM.dd", Locale.US))

class AoCTranslations : Madara("Agent of Change Translations", "https://aoc.moe", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
    override fun popularMangaSelector() = "div.page-item-detail.manga:has(span.chapter)"
    override fun chapterListSelector() = "li.wp-manga-chapter:has(a)"
    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().let { document ->
            document.select(chapterListSelector()).let { normalChapters ->
                if (normalChapters.isNotEmpty()) {
                    normalChapters.map { chapterFromElement(it) }
                } else {
                    // For their "fancy" volume/chapter lists
                    document.select("div.wpb_wrapper:contains(volume) a")
                        .filter { it.attr("href").contains(baseUrl) && !it.attr("href").contains("imgur") }
                        .map { volumeChapter ->
                            SChapter.create().apply {
                                volumeChapter.attr("href").let { url ->
                                    name = if (url.contains("volume")) {
                                        val volume = url.substringAfter("volume-").substringBefore("/")
                                        val volChap = url.substringAfter("volume-$volume/").substringBefore("/").replace("-", " ").capitalize()
                                        "Volume $volume - $volChap"
                                    } else {
                                        url.substringBefore("/p").substringAfterLast("/").replace("-", " ").capitalize()
                                    }
                                    setUrlWithoutDomain(url.substringBefore("?") + "?style=list")
                                }
                            }
                        }
                }.reversed()
            }
        }
    }
}

class KomikGo : Madara("KomikGo", "https://komikgo.com", "id")

class LuxyScans : Madara("Luxy Scans", "https://luxyscans.com", "en")

class TsubakiNoScan : Madara("Tsubaki No Scan", "https://tsubakinoscan.com", "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))

class YokaiJump : Madara("Yokai Jump", "https://yokaijump.fr", "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US))

class ZManga : Madara("ZManga", "https://zmanga.org", "es")

class MangazukiMe : Madara("Mangazuki.me", "https://mangazuki.me", "en")

class MangazukiOnline : Madara("Mangazuki.online", "https://www.mangazuki.online", "en") {
    override val client: OkHttpClient = super.client.newBuilder().followRedirects(true).build()
}

class MangazukiClubJP : Madara("Mangazuki.club", "https://mangazuki.club", "ja")

class MangazukiClubKO : Madara("Mangazuki.club", "https://mangazuki.club", "ko")

class FirstKissManga : Madara("1st Kiss", "https://1stkissmanga.com", "en",
    dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
    override fun detailsThumbnail(element: Element): String {
        return if (element.hasAttr("data-lazy-src")) element.attr("abs:data-lazy-src") else super.detailsThumbnail(element)
    }
}

class MangaSY : Madara("Manga SY", "https://www.mangasy.com", "en")

class ManwhaClub : Madara("Manwha Club", "https://manhwa.club", "en")

class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site", "en") {
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

class YoManga : Madara("Yo Manga", "https://yomanga.info", "en")

class ManyToon : Madara("ManyToon", "https://manytoon.com", "en")

class ChibiManga : Madara("Chibi Manga", "http://www.cmreader.info", "en",
    dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)) {
    override fun chapterListParse(response: Response): List<SChapter> {
        response.asJsoup().let { documet ->
            documet.select("li.parent.has-child").let { volumes ->
                return if (volumes.isNullOrEmpty()) {
                    documet.select(chapterListSelector()).map { chapterFromElement(it) }
                } else {
                    val chapters = mutableListOf<SChapter>()
                    volumes.reversed().forEach { v ->
                        val vName = v.select("a[href^=javascript]").text()
                        v.select(chapterListSelector()).map { chapters.add(chapterFromElement(it).apply { name = "$vName - $name" }) }
                    }
                    chapters
                }
            }
        }
    }
}

class ZinManga : Madara("Zin Translator", "https://zinmanga.com", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "https://zinmanga.com/")
}

class ManwahentaiMe : Madara("Manwahentai.me", "https://manhwahentai.me", "en")

class Manga3asq : Madara("مانجا العاشق", "https://3asq.org", "ar")

class Indiancomicsonline : Madara("Indian Comics Online", "http://www.indiancomicsonline.com", "hi")

class AdonisFansub : Madara("Adonis Fansub", "https://manga.adonisfansub.com", "tr") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}

class GetManhwa : Madara("GetManhwa", "https://getmanhwa.co", "en")

class AllPornComic : Madara("AllPornComic", "https://allporncomic.com", "en") {
    override val client: OkHttpClient = network.client
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.122 Safari/537.36"
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", userAgent)
        .add("Referer", "$baseUrl/manga/?m_orderby=views")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
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

class Hiperdex : Madara("Hiperdex", "https://hiperdex.com", "en") {
    override fun getGenreList() = listOf(
        Genre("Adult", "adult"),
        Genre("Action", "action"),
        Genre("Adventure", "adventure"),
        Genre("Bully", "bully"),
        Genre("Comedy", "comedy"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "fantasy"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "isekai"),
        Genre("Josei", "josei"),
        Genre("Martial Arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mystery", "mystery"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri")
    )
}

class DoujinHentai : Madara("DoujinHentai", "https://doujinhentai.net", "es", SimpleDateFormat("d MMM. yyyy", Locale.ENGLISH)) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=views&page=$page", headers)
    override fun popularMangaSelector() = "div.col-md-3 a"
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select("h5").text()
        manga.thumbnail_url = element.select("img").attr("abs:data-src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "a[rel=next]"
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/lista-manga-hentai?orderby=last&page=$page", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = HttpUrl.parse(baseUrl)!!.newBuilder()
        if (query.isNotBlank()) {
            url.addPathSegment("search")
            url.addQueryParameter("query", query) // query returns results all on one page
        } else {
            filters.forEach { filter ->
                when (filter) {
                    is GenreSelectFilter -> {
                        if (filter.state != 0) {
                            url.addPathSegments("lista-manga-hentai/category/${filter.toUriPart()}")
                            url.addQueryParameter("page", page.toString())
                        }
                    }
                }
            }
        }
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaSelector() = "div.c-tabs-item__content > div.c-tabs-item__content, ${popularMangaSelector()}"
    override fun searchMangaFromElement(element: Element): SManga {
        return if (element.hasAttr("href")) {
            popularMangaFromElement(element) // genre search results
        } else {
            super.searchMangaFromElement(element) // query search results
        }

    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()
    override fun chapterListSelector() = "ul.main.version-chap > li.wp-manga-chapter:not(:last-child)" // removing empty li
    override val pageListParseSelector = "div#all > img.img-responsive"
    override fun getFilterList() = FilterList(
        Filter.Header("Solo funciona si la consulta está en blanco"),
        GenreSelectFilter()
    )

    class GenreSelectFilter : UriPartFilter("Búsqueda de género", arrayOf(
        Pair("<seleccionar>", ""),
        Pair("Ecchi", "ecchi"),
        Pair("Yaoi", "yaoi"),
        Pair("Yuri", "yuri"),
        Pair("Anal", "anal"),
        Pair("Tetonas", "tetonas"),
        Pair("Escolares", "escolares"),
        Pair("Incesto", "incesto"),
        Pair("Virgenes", "virgenes"),
        Pair("Masturbacion", "masturbacion"),
        Pair("Maduras", "maduras"),
        Pair("Lolicon", "lolicon"),
        Pair("Bikini", "bikini"),
        Pair("Sirvientas", "sirvientas"),
        Pair("Enfermera", "enfermera"),
        Pair("Embarazada", "embarazada"),
        Pair("Ahegao", "ahegao"),
        Pair("Casadas", "casadas"),
        Pair("Chica Con Pene", "chica-con-pene"),
        Pair("Juguetes Sexuales", "juguetes-sexuales"),
        Pair("Orgias", "orgias"),
        Pair("Harem", "harem"),
        Pair("Romance", "romance"),
        Pair("Profesores", "profesores"),
        Pair("Tentaculos", "tentaculos"),
        Pair("Mamadas", "mamadas"),
        Pair("Shota", "shota"),
        Pair("Interracial", "interracial"),
        Pair("Full Color", "full-colo"),
        Pair("Sin Censura", "sin-censura"),
        Pair("Futanari", "futanari"),
        Pair("Doble Penetracion", "doble-penetracion"),
        Pair("Cosplay", "cosplay")
    )
    )
}

class Azora : Madara("Azora", "https://www.azoramanga.com", "ar") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun chapterListSelector() = "li.wp-manga-chapter:not(:has(img))" // Filter fake chapters
    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()

        element.select("a").let {
            chapter.url = it.attr("href").substringAfter(baseUrl)
            chapter.name = it.text()
        }
        return chapter
    }
}

class KMangaIn : Madara("Kissmanga.in", "https://kissmanga.in", "en")

class HunterFansub : Madara("Hunter Fansub", "https://hunterfansub.com", "es") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/biblioteca/page/$page?m_orderby=views", headers)
    override fun popularMangaNextPageSelector() = "div.nav-previous"
    override val popularMangaUrlSelector = "div.post-title a:last-child"
}

class MangaArabTeam : Madara("مانجا عرب تيم Manga Arab Team", "https://mangaarabteam.com", "ar")

class NightComic : Madara("Night Comic", "http://www.nightcomic.com", "en")

class Toonily : Madara("Toonily", "https://toonily.com", "en")

class PlotTwistScan : Madara("Plot Twist No Fansub", "https://www.plotwistscan.com", "es") {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).asReversed()
}

class MangaKomi : Madara("MangaKomi", "https://mangakomi.com", "en")

class Wakamics : Madara("Wakamics", "https://wakamics.com", "en")

class TeabeerComics : Madara("Teabeer Comics", "https://teabeercomics.com", "en")

class KingzManga : Madara("KingzManga", "https://kingzmanga.com", "ar")

class YaoiToshokan : Madara("Yaoi Toshokan", "https://www.yaoitoshokan.com.br", "pt-BR") {
    override val popularMangaUrlSelector = "div.post-title a:not([target])" //Page has custom link to scan website
    override fun chapterListParse(response: Response): List<SChapter> { //Chapters are listed old to new
        return super.chapterListParse(response).reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector).mapIndexed { index, element ->
            Page(index, "", element.select("img").attr("data-src").trim())  //had to add trim because of white space in source
        }
    }

}

class GoldenManga : Madara("موقع لترجمة المانجا", "https://golden-manga.ml", "ar", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class Mangalek : Madara("مانجا ليك", "https://mangalek.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))

class AstralLibrary : Madara("Astral Library", "https://astrallibrary.net", "en") {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}

class NovelFrance : Madara("Novel France", "http://novel-france.fr", "fr", SimpleDateFormat("dd MMM yyyy", Locale.FRENCH))

class KlikManga : Madara("KlikManga", "https://klikmanga.com", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id")))

class MiracleScans : Madara("Miracle Scans", "https://miraclescans.com", "en")

class Manhuasnet : Madara("Manhuas.net", "https://manhuas.net", "en")

class MangaLaw : Madara("MangaLaw", "https://mangalaw.com", "ja", SimpleDateFormat("MM/dd/yyyy", Locale.US))

class EarlyManga : Madara("EarlyManga", "https://earlymanga.website", "en"){
    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer","$baseUrl/manga/")
    }
}

class MangaTX : Madara("MangaTX", "https://mangatx.com", "en")

class ATMSubs : Madara("ATM-Subs", "https://atm-subs.fr", "fr", SimpleDateFormat("d MMMM yyyy", Locale("fr")))

class OnManga : Madara("OnManga", "https://onmanga.com", "en")

class MangaAction : Madara("Manga Action", "https://manga-action.com", "ar", SimpleDateFormat("yyyy-MM-dd", Locale("ar")))

class NijiTranslations : Madara("Niji Translations", "https://niji-translations.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))

class IchirinNoHanaYuri : Madara("Ichirin No Hana Yuri", "https://ichirinnohanayuri.com.br", "pt-BR", SimpleDateFormat("dd/MM/yyyy", Locale("pt")))

class LilyManga: Madara("Lily Manga","https://lilymanga.com","en",SimpleDateFormat("yyyy-MM-dd", Locale.US))

class MangaBob: Madara("MangaBob","https://mangabob.com","en")

class ThreeSixtyFiveManga: Madara("365Manga","https://365manga.com","en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}

class DisasterScans: Madara("Disaster Scans","https://disasterscans.com","en") {
    override val popularMangaUrlSelector = "div.post-title a:last-child"

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = super.mangaDetailsParse(document)

        with(document) {
            select("div.post-title h1").first()?.let {
                manga.title = it.ownText()
            }
        }

        return manga
    }
}

class MangaKiss: Madara("MangaKiss", "https://mangakiss.org", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}

class MangaDods: Madara("MangaDods", "https://www.mangadods.com", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US))

class MangaWOW: Madara("MangaWOW", "https://mangawow.com", "tr")

class MangaStream: Madara("MangaStream", "https://www.mangastream.cc", "en")

class NeoxScanlator : Madara("Neox Scanlator", "https://neoxscan.com/newsite", "pt-BR", SimpleDateFormat("dd 'de' MMM 'de' yyyy", Locale("pt", "BR"))) {
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    // Only status and order by filter work.
    override fun getFilterList(): FilterList = FilterList(super.getFilterList().slice(3 .. 4))

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/80.0.3987.87 Safari/537.36"
    }
}

class MangaLord : Madara("Manga Lord", "https://mangalord.com", "en")

class PornComix : Madara("PornComix", "https://www.porncomixonline.net", "en")

class MangaRead : Madara("Manga Read", "https://mangaread.co", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class UnknownScans : Madara("Unknown Scans", "https://unknoscans.com", "en")
