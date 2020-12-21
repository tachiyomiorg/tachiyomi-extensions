package eu.kanade.tachiyomi.multisrc.madara


import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
//        AdonisFansub(), // 2
//        AkuManga(), // 3
        AlianzaMarcial(),
//        AllPornComic(), // 4
//        Aloalivn(), // 5
//        AniMangaEs(), // 6
//        AoCTranslations(), // 7
//        ApollComics(), // 8
//        ArangScans(), // 9
//        ArazNovel(), // 10
        ArgosScan(),
//        AsgardTeam(), // 11
//        AstralLibrary(), // 12
        Atikrost(),
//        ATMSubs(), // 1
//        Azora(), // 13
//        Bakaman(), // 14
//        BestManga(), // 15
//        BestManhua(), // 16
//        BoysLove(), // 17
//        CatOnHeadTranslations(), // 18
//        CatTranslator(), // 19
//        ChibiManga(), // 20
//        ComicKiba(), // 21
//        ComicsValley(), // 22
//        CopyPasteScan(), // 23
//        CutiePie(), // 24
//        DarkyuRealm(), // 25
//        DecadenceScans(), // 26
//        DetectiveConanAr(), // 27
//        DiamondFansub(), // 28
//        DisasterScans(), // 29
//        DoujinHentai(), // 30
//        DoujinYosh(), // 31
//        DropeScan(), // 32
        EarlyManga(),
//        EinherjarScan(), // 33
        FdmScan(),
//        FirstKissManga(), // 34
//        FirstKissManhua(), // 35
//        FreeWebtoonCoins(), // 36
//        FurioScans(), // 37
//        GoldenManga(), // 38
//        GuncelManga(), // 39
//        HeroManhua(), // 40
//        HerozScanlation(), // 41
        HikariScan(),
//        HimeraFansub(), // 42
//        Hiperdex(), // 43
        Hscans(),
        HunterFansub(),
        IchirinNoHanaYuri(),
        ImmortalUpdates(),
        IsekaiScanCom(),
        JJutsuScans(),
        JustForFun(),
        KingzManga(),
        KisekiManga(),
        KlanKomik(),
        KlikManga(),
        KMangaIn(),
        KnightNoScanlation(),
        Kombatch(),
        KomikGo(),
        LilyManga(),
        Manga18Fun(),
        Manga347(),
        Manga3asq(),
        Manga3S(),
        Manga68(),
        MangaAction(),
        MangaArabOnline(),
        MangaArabTeam(),
        MangaBaz(),
        MangaBob(),
        MangaClash(),
        MangaCultivator(),
        MangaDods(),
        MangaGecesi(),
        MangaHentai(),
        MangaKiss(),
        MangaKitsu(),
        MangaKomi(),
        MangaLandArabic(),
        Mangalek(),
        MangaLord(),
        ManganeloLink(),
        MangaNine(),
        MangaPhoenix(),
        MangaRave(),
        MangaRawr(),
        MangaRead(),
        MangaReadOrg(),
        Mangareceh(),
        MangaRockTeam(),
        MangaRocky(),
        MangaRoma(),
        MangaScantrad(),
        MangaSco(),
        MangaSpark(),
        MangaStarz(),
        MangaStein(),
        Mangasushi(),
        MangaSY(),
        MangaTeca(),
        MangaTurf(),
        MangaTX(),
        MangaWOW(),
        MangaWT(),
        MangaYaku(),
        MangaYosh(),
        MangazukiClubJP(),
        MangazukiClubKO(),
        MangazukiMe(),
        MangazukiOnline(),
        ManhuaBox(),
        ManhuaFast(),
        Manhuaga(),
        ManhuaPlus(),
        Manhuasnet(),
        ManhuasWorld(),
        ManhuaSY(),
        ManhuaUS(),
        ManhwaRaw(),
        ManhwaTop(),
        ManwahentaiMe(),
        ManwhaClub(),
        ManyToon(),
        ManyToonClub(),
        ManyToonMe(),
        MarkScans(),
        MartialScans(),
        MGKomik(),
        Milftoon(),
        MiracleScans(),
        MixedManga(),
        MysticalMerries(),
        NazarickScans(),
        NekoBreaker(),
        NekoScan(),
        NeoxScanlator(),
        NightComic(),
        NijiTranslations(),
        Ninjavi(),
        NTSVoidScans(),
        OffScan(),
        OlaoeManga(),
        OnManga(),
        OrigamiOrpheans(),
        PMScans(),
        PojokManga(),
        PornComix(),
        PrimeManga(),
        ProjetoScanlator(),
        QueensManga(),
        RaiderScans(),
        RandomTranslations(),
        ReadManhua(),
        RenaScans(),
        RuyaManga(),
        S2Manga(),
        ShoujoHearts(),
        Skymanga(),
        SpookyScanlations(),
        StageComics(),
        TheTopComic(),
        ThreeSixtyFiveManga(),
        ToonGod(),
        Toonily(),
        ToonilyNet(),
        ToonPoint(),
        TopManhua(),
        TritiniaScans(),
        TruyenTranhAudioCom(),
        TruyenTranhAudioOnline(),
        TsubakiNoScan(),
        TurkceManga(),
        TwilightScans(),
        Wakascan(),
        WebNovel(),
        WebToonily(),
        WebtoonXYZ(),
        WeScans(),
        WoopRead(),
        WuxiaWorld(),
        YaoiToshokan(),
        YokaiJump(),
        YuriVerso(),
        ZinManga(),
        ZManga(),

        // removed because scanlator site and they requested
        // AhStudios(),
    )
}

class Skymanga : Madara("Skymanga", "https://skymanga.co", "en")

class StageComics : Madara("StageComics", "https://stagecomics.com", "pt-BR", SimpleDateFormat("MMMM dd, yyyy", Locale("pt"))) {
    override fun chapterFromElement(element: Element): SChapter {
        val parsedChapter = super.chapterFromElement(element)

        parsedChapter.date_upload = element.select("img").firstOrNull()?.attr("alt")
            ?.let { parseChapterDate(it) }
            ?: parseChapterDate(element.select("span.chapter-release-date i").firstOrNull()?.text())

        return parsedChapter
    }
}

class SpookyScanlations : Madara("Spooky Scanlations", "https://spookyscanlations.xyz", "es", SimpleDateFormat("MMMM dd, yyyy", Locale("es")))

class RandomTranslations : Madara("Random Translations", "https://randomtranslations.com", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US))

class Atikrost : Madara("Atikrost", "https://atikrost.com", "tr", SimpleDateFormat("MMMM dd, yyyy", Locale("tr")))


class ManhuaFast : Madara("ManhuaFast", "https://manhuafast.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}


class ManhuaSY : Madara("Manhua SY", "https://www.manhuasy.com", "en")

class MangaRave : Madara("MangaRave", "http://www.mangarave.com", "en")

class NekoScan : Madara("NekoScan", "https://nekoscan.com", "en")

class Manga3S : Madara("Manga3S", "https://manga3s.com", "en")

class MGKomik : Madara("MG Komik", "https://mgkomik.my.id", "id")


class MangaSco : Madara("MangaSco", "https://mangasco.com", "en")

class MangaKitsu : Madara("Manga Kitsu", "https://mangakitsu.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale("tr")))

class PrimeManga : Madara("Prime Manga", "https://primemanga.com", "en")

class NekoBreaker : Madara("NekoBreaker", "https://nekobreaker.com", "pt-BR", SimpleDateFormat("MMMM dd, yyyy", Locale("pt")))

class ManganeloLink : Madara("Manganelo.link", "https://manganelo.link", "en")


class KisekiManga : Madara("KisekiManga", "https://kisekimanga.com", "en")


class MangaRoma : Madara("MangaRoma", "https://mangaroma.com", "en")

class NTSVoidScans : Madara("NTS Void Scans", "https://ntsvoidscans.com", "en")

class ToonGod : Madara("ToonGod", "https://www.toongod.com", "en", SimpleDateFormat("dd MMM yyyy", Locale.US))

class Hscans : Madara("Hscans", "https://hscans.com", "en", SimpleDateFormat("MMMM dd, yyyy", Locale.forLanguageTag("es")))

class WebToonily : Madara("WebToonily", "https://webtoonily.com", "en")

class Manga18Fun : Madara("Manga18 Fun", "https://manga18.fun", "en")


class MangaYaku : Madara("MangaYaku", "https://mangayaku.my.id", "id")

class RuyaManga : Madara("Rüya Manga", "https://www.ruyamanga.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")))


class MangaBaz : Madara("MangaBaz", "https://mangabaz.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")))

class Manhuaga : Madara("Manhuaga", "https://manhuaga.com", "en")

class MangaCultivator : Madara("MangaCultivator", "https://mangacultivator.com", "en")



class OffScan : Madara(
    "Off Scan",
    "https://offscan.top",
    "pt-BR",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
)

class MangaClash : Madara(
    "Manga Clash",
    "https://mangaclash.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yy", Locale.US)
)

class TritiniaScans : Madara("TritiniaScans", "https://tritiniaman.ga", "en") {
    // site is a bit broken
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/index_m_orderby=views.html", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/index_m_orderby=latest.html", headers)
    override fun latestUpdatesNextPageSelector(): String? = null
    private val imageRegex = Regex(""""(http[^"]*)"""")
    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("#chapter_preloaded_images").firstOrNull()?.data()
            ?: throw Exception("chapter_preloaded_images not found")
        return imageRegex.findAll(script).asIterable().mapIndexed { i, mr ->
            Page(i, "", mr.groupValues[1].replace("\\", ""))
        }
    }
}


class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en")

class MangaRawr : Madara("MangaRawr", "https://mangarawr.com", "en")

class ReadManhua : Madara(
    "ReadManhua",
    "https://readmanhua.net",
    "en",
    dateFormat = SimpleDateFormat("dd MMM yy", Locale.US)
)

class IsekaiScanCom : Madara("IsekaiScan.com", "https://isekaiscan.com", "en")

class JustForFun : Madara(
    "Just For Fun",
    "https://just-for-fun.ru",
    "ru",
    dateFormat = SimpleDateFormat("yy.MM.dd", Locale.US)
)


class KomikGo : Madara("KomikGo", "https://komikgo.com", "id")

class TsubakiNoScan : Madara(
    "Tsubaki No Scan",
    "https://tsubakinoscan.com",
    "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
)

class YokaiJump : Madara(
    "Yokai Jump",
    "https://yokaijump.fr",
    "fr",
    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
)

class ZManga : Madara("ZManga", "https://zmanga.org", "es")

class MangazukiMe : Madara("Mangazuki.me", "https://mangazuki.me", "en")

class MangazukiOnline : Madara("Mangazuki.online", "https://www.mangazuki.online", "en") {
    override val client: OkHttpClient = super.client.newBuilder().followRedirects(true).build()
}

class MangazukiClubJP : Madara("Mangazuki.club", "https://mangazuki.club", "ja")

class MangazukiClubKO : Madara("Mangazuki.club", "https://mangazuki.club", "ko")


class MangaSY : Madara("Manga SY", "https://www.mangasy.com", "en") {
    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()
}

class ManwhaClub : Madara("Manwha Club", "https://manhwa.club", "en")

class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=latest", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = super.searchMangaRequest(page, "$query comics", filters)
    override fun popularMangaNextPageSelector() = "div.nav-previous.float-left"
}

class ManyToon : Madara("ManyToon", "https://manytoon.com", "en")

class ManyToonMe : Madara("ManyToon.me", "https://manytoon.me", "en")


class ZinManga : Madara("Zin Translator", "https://zinmanga.com", "en") {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "https://zinmanga.com/")
}

class ManwahentaiMe : Madara("Manwahentai.me", "https://manhwahentai.me", "en")

class Manga3asq : Madara("مانجا العاشق", "https://3asq.org", "ar")


class Milftoon : Madara("Milftoon", "https://milftoon.xxx", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=latest", headers)
}




class Azora : Madara("Azora", "https://www.azoramanga.com", "ar") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
    override fun chapterListSelector() = "li.wp-manga-chapter:not(.premium-block)" // Filter fake chapters
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

class MangaArabOnline : Madara("Manga Arab Online مانجا عرب اون لاين", "https://mangaarabonline.com", "ar", SimpleDateFormat("MMM d, yyyy", Locale.forLanguageTag("ar")))

class MangaArabTeam : Madara("مانجا عرب تيم Manga Arab Team", "https://mangaarabteam.com", "ar") {
    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!.replace("http:", "https:"))
    }
}

class NightComic : Madara("Night Comic", "https://www.nightcomic.com", "en") {
    override val formHeaders: Headers = headersBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded")
        .add("X-MOD-SBB-CTYPE", "xhr")
        .build()
}

@Nsfw
class Toonily : Madara("Toonily", "https://toonily.com", "en") {
    override fun getGenreList(): List<Genre> = listOf(
        Genre("Action", "action-webtoon"),
        Genre("Adult", "adult-webtoon"),
        Genre("Adventure", "adventure-webtoon"),
        Genre("Comedy", "comedy-webtoon"),
        Genre("Drama", "drama-webtoon"),
        Genre("Fantasy", "fantasy-webtoon"),
        Genre("Gender Bender", "gender-bender"),
        Genre("Gossip", "gossip"),
        Genre("Harem", "harem-webtoon"),
        Genre("Historical", "webtoon-historical"),
        Genre("Horror", "horror-webtoon"),
        Genre("Josei", "josei-manga"),
        Genre("Mature", "mature-webtoon"),
        Genre("Mystery", "mystery-webtoon"),
        Genre("NTR", "ntr-webtoon"),
        Genre("Psychological", "psychological-webtoon"),
        Genre("Romance", "romance-webtoon"),
        Genre("School life", "school-life-webtoon"),
        Genre("Sci-Fi", "scifi-webtoon"),
        Genre("Seinen", "seinen-webtoon"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen-webtoon"),
        Genre("Slice of Life", "sliceoflife-webtoon"),
        Genre("Supernatural", "supernatural-webtoon"),
        Genre("Thriller", "thriller-webtoon"),
        Genre("Tragedy", "tragedy"),
        Genre("Vanilla", "vanilla-webtoon"),
        Genre("Yaoi", "yaoi-webtoon"),
        Genre("Yuri", "yuri-webtoon")
    )
}

class MangaKomi : Madara("MangaKomi", "https://mangakomi.com", "en")

class KingzManga : Madara("KingzManga", "https://kingzmanga.com", "ar")

@Nsfw
class YaoiToshokan : Madara("Yaoi Toshokan", "https://yaoitoshokan.com.br", "pt-BR", SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))) {
    // Page has custom link to scan website.
    override val popularMangaUrlSelector = "div.post-title a:not([target])"

    // Chapters are listed old to new.
    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).reversed()
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(pageListParseSelector)
            .mapIndexed { index, element ->
                // Had to add trim because of white space in source.
                val imageUrl = element.select("img").attr("data-src").trim()
                Page(index, document.location(), imageUrl)
            }
    }
}

class GoldenManga : Madara("موقع لترجمة المانجا", "https://golden-manga.com", "ar", SimpleDateFormat("yyyy-MM-dd", Locale.US)) {
    override fun searchMangaSelector() = "div.c-image-hover a"
    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            title = element.attr("title")
            thumbnail_url = element.select("img").firstOrNull()?.let { imageFromElement(it) }
        }
    }

    override fun chapterListSelector() = "div.main a"
    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select("h6:first-of-type").text()
            date_upload = parseChapterDate(element.select("h6:last-of-type").firstOrNull()?.ownText())
        }
    }
}

class Mangalek : Madara("مانجا ليك", "https://mangalek.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))


class KlikManga : Madara("KlikManga", "https://klikmanga.com", "id", SimpleDateFormat("MMMM dd, yyyy", Locale("id")))

class MiracleScans : Madara("Miracle Scans", "https://miraclescans.com", "en")

class Manhuasnet : Madara("Manhuas.net", "https://manhuas.net", "en")

class MangaTX : Madara("MangaTX", "https://mangatx.com", "en")


class OnManga : Madara("OnManga", "https://onmanga.com", "en")

class MangaAction : Madara("Manga Action", "https://manga-action.com", "ar", SimpleDateFormat("yyyy-MM-dd", Locale("ar")))

class NijiTranslations : Madara("Niji Translations", "https://niji-translations.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))

class IchirinNoHanaYuri : Madara("Ichirin No Hana Yuri", "https://ichirinnohanayuri.com.br", "pt-BR", SimpleDateFormat("dd/MM/yyyy", Locale("pt")))

class LilyManga : Madara("Lily Manga", "https://lilymanga.com", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class MangaBob : Madara("MangaBob", "https://mangabob.com", "en")

class ThreeSixtyFiveManga : Madara("365Manga", "https://365manga.com", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
}


class MangaDods : Madara("MangaDods", "https://www.mangadods.com", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class NeoxScanlator : Madara(
    "Neox Scanlator",
    "https://neoxscans.net",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
) {
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    // Only status and order by filter work.
    override fun getFilterList(): FilterList = FilterList(super.getFilterList().slice(3..4))

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"
    }
}

class MangaLord : Madara("Manga Lord", "https://mangalord.com", "en")

@Nsfw
class PornComix : Madara("PornComix", "https://www.porncomixonline.net", "en")

class PMScans : Madara("PMScans", "https://www.pmscans.com", "en")

class MangaRead : Madara("Manga Read", "https://mangaread.co", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class Manga68 : Madara("Manga68", "https://manga68.com", "en") {
    override val pageListParseSelector = "div.page-break, div.text-left p"
}

class ManhuaBox : Madara("ManhuaBox", "https://manhuabox.net", "en") {
    override val pageListParseSelector = "div.page-break, div.text-left p img"
}

class RaiderScans : Madara("Raider Scans", "https://raiderscans.com", "en")

class PojokManga : Madara("Pojok Manga", "https://pojokmanga.com", "id", SimpleDateFormat("MMM dd, yyyy", Locale.US))

class TopManhua : Madara("Top Manhua", "https://topmanhua.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}

class ManyToonClub : Madara("ManyToonClub", "https://manytoon.club", "ko")

class ManhuaUS : Madara("ManhuaUS", "https://manhuaus.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}

class MangaWT : Madara("MangaWT", "https://mangawt.com", "tr")


class MangaRockTeam : Madara("Manga Rock Team", "https://mangarockteam.com", "en")

class MixedManga : Madara("Mixed Manga", "https://mixedmanga.com", "en", SimpleDateFormat("d MMM yyyy", Locale.US)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
}

class ManhuasWorld : Madara("Manhuas World", "https://manhuasworld.com", "en")


class ManhwaRaw : Madara("Manhwa Raw", "https://manhwaraw.com", "ko")


class WeScans : Madara("WeScans", "https://wescans.xyz", "en") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=views", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=latest", headers)
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/manhua/?s=$query&post_type=wp-manga")
    override fun searchMangaNextPageSelector(): String? = null
    override fun getFilterList(): FilterList = FilterList()
}


@Nsfw
class MangaHentai : Madara("Manga Hentai", "https://mangahentai.me", "en")

class MangaPhoenix : Madara("Manga Diyari", "https://mangadiyari.com", "tr") {
    // Formerly "Manga Phoenix"
    override val id = 4308007020001642101
}



class MartialScans : Madara("Martial Scans", "https://martialscans.com", "en") {
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).last()?.let {
                manga.setUrlWithoutDomain(it.attr("href"))
                manga.title = it.ownText()
            }

            select("img").last()?.let {
                manga.thumbnail_url = imageFromElement(it)
            }
        }

        return manga
    }

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
}

class MangaYosh : Madara("MangaYosh", "https://mangayosh.xyz", "id", SimpleDateFormat("dd MMM yyyy", Locale.US))

class WebtoonXYZ : Madara("WebtoonXYZ", "https://www.webtoon.xyz", "en") {
    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=views", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=latest", headers)
}

class MangaReadOrg : Madara("MangaRead.org", "https://www.mangaread.org", "en", SimpleDateFormat("dd.MM.yyy", Locale.US))

class TurkceManga : Madara("Türkçe Manga", "https://turkcemanga.com", "tr") {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=views", headers)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=latest", headers)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)
}


class Manga347 : Madara("Manga347", "https://manga347.com", "en", SimpleDateFormat("d MMM, yyyy", Locale.US)) {
    override val pageListParseSelector = "li.blocks-gallery-item"
}

class RenaScans : Madara("Renascence Scans (Renascans)", "https://new.renascans.com", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US))

class QueensManga : Madara("QueensManga ملكات المانجا", "https://queensmanga.com", "ar") {
    override fun chapterListSelector(): String = "div.listing-chapters_wrap a"
}


class TheTopComic : Madara("TheTopComic", "https://thetopcomic.com", "en")

class WebNovel : Madara("WebNovel", "https://webnovel.live", "en")

class TruyenTranhAudioCom : Madara("TruyenTranhAudio.com", "https://truyentranhaudio.com", "vi", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)
    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reading-content img").map { it.attr("abs:src") }
            .filterNot { it.isNullOrEmpty() }
            .distinct()
            .mapIndexed { i, url -> Page(i, "", url) }
    }
}

class TruyenTranhAudioOnline : Madara("TruyenTranhAudio.online", "https://truyentranhaudio.online", "vi", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
    override val formHeaders: Headers = headersBuilder()
        .add("Content-Type", "application/x-www-form-urlencoded")
        .build()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.reading-content img").map { it.attr("abs:src") }
            .filterNot { it.isNullOrEmpty() }
            .distinct()
            .mapIndexed { i, url -> Page(i, "", url) }
    }
}

class MangaTurf : Madara("Manga Turf", "https://mangaturf.com", "en")


class Mangareceh : Madara("Mangareceh", "https://mangareceh.id", "id")


class KlanKomik : Madara("KlanKomik", "https://klankomik.com", "id", SimpleDateFormat("d MMM yyyy", Locale.US))

class ToonPoint : Madara("ToonPoint", "https://toonpoint.com", "en") {
    override val userAgentRandomizer = ""
}

class MangaScantrad : Madara("Manga-Scantrad", "https://manga-scantrad.net", "fr", SimpleDateFormat("d MMM yyyy", Locale.FRANCE))

class ManhuaPlus : Madara("Manhua Plus", "https://manhuaplus.com", "en") {
    override val pageListParseSelector = "li.blocks-gallery-item"
}



@Nsfw
class ToonilyNet : Madara("Toonily.net", "https://toonily.net", "en")


class TwilightScans : Madara("Twilight Scans", "https://twilightscans.com", "en")


// mostly novels
class WoopRead : Madara("WoopRead", "https://woopread.com", "en")

class Ninjavi : Madara("Ninjavi", "https://ninjavi.com", "ar")

class ManhwaTop : Madara("Manhwatop", "https://manhwatop.com", "en")

class ImmortalUpdates : Madara("Immortal Updates", "https://immortalupdates.com", "en")


class Wakascan : Madara("Wakascan", "https://wakascan.com", "fr")

class ShoujoHearts : Madara("ShoujoHearts", "http://shoujohearts.com", "en") {
    override fun popularMangaRequest(page: Int): Request =
        POST("$baseUrl/reader/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, true).build(), CacheControl.FORCE_NETWORK)

    override fun latestUpdatesRequest(page: Int): Request =
        POST("$baseUrl/reader/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, false).build(), CacheControl.FORCE_NETWORK)

    override fun searchPage(page: Int): String = "reader/page/$page/"
}

class AlianzaMarcial : Madara("AlianzaMarcial", "https://alianzamarcial.xyz", "es")

class OlaoeManga : Madara("مانجا اولاو", "https://olaoe.giize.com", "ar")

class FdmScan : Madara("FDM Scan", "https://fdmscan.com", "pt-BR", SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR")))

class ArgosScan : Madara("Argos Scan", "https://argosscan.com", "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))

class OrigamiOrpheans : Madara("Origami Orpheans", "https://origami-orpheans.com.br", "pt-BR")


class MangaKiss : Madara("Manga Kiss", "https://mangakiss.org", "en")

class MangaRocky : Madara("Manga Rocky", "https://mangarocky.com", "en")

class JJutsuScans : Madara("JJutsuScans", "https://jjutsuscans.com", "en")

class S2Manga : Madara("S2Manga", "https://s2manga.com", "en")

class MangaLandArabic : Madara("Manga Land Arabic", "https://mangalandarabic.com", "ar")

class Kombatch : Madara("Kombatch", "https://kombatch.com", "id", SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("id")))

class ProjetoScanlator : Madara("Projeto Scanlator", "https://projetoscanlator.com", "pt-BR", SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")))

class HikariScan : Madara("Hikari Scan", "https://hikariscan.com.br", "pt-BR", SimpleDateFormat("dd 'de' MMMM 'de' yyyy", Locale("pt", "BR")))

class NazarickScans : Madara("Nazarick Scans", "https://nazarickscans.com", "en") {
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page/?m_orderby=trending", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/page/$page/?m_orderby=trending", headers)
    override fun popularMangaNextPageSelector(): String? = null
    override fun latestUpdatesNextPageSelector(): String? = null
}

class MangaSpark : Madara("MangaSpark", "https://mangaspark.com", "ar") {
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)?.replace("mangaspark", "mangalek")
            }
        }

        return manga
    }
}

class MangaStarz : Madara("Manga Starz", "https://mangastarz.com", "ar") {
    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        with(element) {
            select(popularMangaUrlSelector).first()?.let {
                manga.setUrlWithoutDomain(it.attr("abs:href"))
                manga.title = it.ownText()
            }

            select("img").first()?.let {
                manga.thumbnail_url = imageFromElement(it)?.replace("mangastarz", "mangalek")
            }
        }

        return manga
    }
}

class MysticalMerries : Madara("Mystical Merries", "https://mysticalmerries.com", "en") {
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/manhwa/page/$page/?m_orderby=trending", headers)
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/manhwa/page/$page/?m_orderby=latest", headers)
}


class MangaNine : Madara("Manga Nine", "https://manganine.com", "en")

class MarkScans : Madara("Mark Scans", "https://markscans.online", "pt-BR")

class YuriVerso : Madara(
    "Yuri Verso",
    "https://yuri.live",
    "pt-BR",
    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
)

class MangaStein : Madara("MangaStein", "https://mangastein.com", "tr")

class EarlyManga : Madara("EarlyManga", "https://earlymanga.xyz", "en") {
    override fun headersBuilder(): Headers.Builder {
        return super.headersBuilder().add("Referer", "$baseUrl/manga/")
    }
}

class MangaGecesi : Madara("Manga Gecesi", "https://mangagecesi.com", "tr") {
    override val chapterUrlSelector = "li.wp-manga-chapter div.chapter-thumbnail + a"
}

class MangaWOW : Madara("MangaWOW", "https://mangawow.com", "tr")

class KnightNoScanlation : Madara("Knight no Scanlation", "https://knightnoscanlation.com", "es")

class MangaTeca : Madara(
    "MangaTeca",
    "https://www.mangateca.com",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt", "BR"))
) {
    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Referer", baseUrl)
        .add("Origin", baseUrl)

    override fun chapterFromElement(element: Element): SChapter {
        val parsedChapter = super.chapterFromElement(element)

        parsedChapter.date_upload = element.select("img").firstOrNull()?.attr("alt")
            ?.let { parseChapterDate(it) }
            ?: parseChapterDate(element.select("span.chapter-release-date i").firstOrNull()?.text())

        return parsedChapter
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.75 Safari/537.36"
    }
}
