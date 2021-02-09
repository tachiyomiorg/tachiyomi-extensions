package eu.kanade.tachiyomi.extension.all.madara

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class MadaraFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
//        AdonisFansub(),
//        AkuManga(),
//        AlianzaMarcial(),
//        AllPornComic(),
//        Aloalivn(),
//        AniMangaEs(),
//        AoCTranslations(),
//        ApollComics(),
//        ArangScans(),
//        ArazNovel(),
//        ArgosScan(),
//        AsgardTeam(),
//        AstralLibrary(),
//        Atikrost(),
//        ATMSubs(),
//        Azora(),
//        Bakaman(),
//        BestManga(),
//        BestManhua(),
//        BoysLove(),
//        CatOnHeadTranslations(),
//        CatTranslator(),
//        ChibiManga(),
//        CloverManga(),
//        ComicKiba(),
//        ComicsValley(),
//        CopyPasteScan(),
//        CutiePie(),
//        DarkyuRealm(),
//        DecadenceScans(),
//        DetectiveConanAr(),
//        DiamondFansub(),
//        DisasterScans(),
//        DoujinHentai(),
//        DoujinYosh(),
//        DreamManga(),
//        DropeScan(),
//        EinherjarScan(),
//        FdmScan(),
//        FirstKissManga(),
//        FirstKissManhua(),
//        FreeWebtoonCoins(),
//        FurioScans(),
//        GeceninLordu(),
//        GoldenManga(),
//        GrazeScans(),
//        GourmetScans(),
//        GuncelManga(),
//        HeroManhua(),
//        HerozScanlation(),
//        HikariScan(),
//        HimeraFansub(),
//        Hiperdex(),
//        Hscans(),
//        HunterFansub(),
//        IchirinNoHanaYuri(),
//        ImmortalUpdates(),
//        IsekaiScanCom(),
//        ItsYourRightManhua(),
//        JJutsuScans(),
//        JustForFun(),
//        KingzManga(),
//        KisekiManga(),
//        KlikManga(),
//        Kombatch(),
        KomikGo(),
        LilyManga(),
        LovableSubs(),
        Manga18Fun(),
        Manga347(),
        Manga3asq(),
        Manga3S(),
        Manga68(),
        MangaAction(),
        MangaArabOnline(),
        MangaArabTeam(),
        MangaBaz(),
        MangaBin(),
        MangaBob(),
        MangaChill(),
        MangaClash(),
        MangaCrimson(),
        MangaCultivator(),
        MangaDods(),
        MangaHentai(),
        MangaKiss(),
        MangaKomi(),
        MangaLandArabic(),
        Mangalek(),
        MangaLord(),
        ManganeloLink(),
        MangaNine(),
        MangaOnlineCo(),
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
        MangaWeebs(),
        MangaWT(),
        MangaYaku(),
        MangaYosh(),
        MangazukiClubJP(),
        MangazukiClubKO(),
        MangazukiMe(),
//        MangazukiOnline(),
//        ManhuaBox(),
//        ManhuaFast(),
//        Manhuaga(),
//        ManhuaPlus(),
//        Manhuasnet(),
//        ManhuasWorld(),
//        ManhuaSY(),
//        ManhuaUS(),
//        ManhwaRaw(),
//        ManhwaTop(),
//        ManwahentaiMe(),
//        ManwhaClub(),
//        ManyToon(),
//        ManyToonClub(),
//        ManyToonMe(),
//        MarkScans(),
//        MartialScans(),
//        MGKomik(),
//        Milftoon(),
//        MiracleScans(),
//        MixedManga(),
//        MMScans(),
//        MundoWuxia(),
//        MysticalMerries(),
//        NazarickScans(),
//        NeatManga(),
//        NekoBreaker(),
//        NekoScan(),
//        NeoxScanlator(),
//        NightComic(),
//        NijiTranslations(),
//        Ninjavi(),
//        NTSVoidScans(),
//        OffScan(),
//        OlaoeManga(),
//        OnManga(),
//        OrigamiOrpheans(),
//        PMScans(),
//        PojokManga(),
//        PornComix(),
//        PrimeManga(),
//        ProjetoScanlator(),
//        QueensManga(),
//        RaiderScans(),
//        RandomTranslations(),
//        RawMangas(),
//        ReadManhua(),
//        RenaScans(),
//        RuyaManga(),
//        S2Manga(),
//        SekteDoujin(),
//        ShoujoHearts(),
//        SiXiangScans(),
//        Siyahmelek(),
//        Skymanga(),
//        SoloScanlation(),
//        SpookyScanlations(),
//        StageComics(),
//        TheTopComic(),
//        ThreeSixtyFiveManga(),
//        ToonGod(),
//        Toonily(),
//        ToonilyNet(),
//        ToonPoint(),
//        TopManhua(),
//        TritiniaScans(),
//        TruyenTranhAudioCom(),
//        TruyenTranhAudioOnline(),
//        TsubakiNoScan(),
//        TurkceManga(),
//        TwilightScans(),
//        UyuyanBalik(),
//        VanguardBun(),
//        Voidscans(),
//        Wakascan(),
//        WarQueenScan(),
//        WebNovel(),
//        WebToonily(),
//        WebtoonXYZ(),
//        WeScans(),
//        WoopRead(),
//        WorldRomanceTranslation(),
//        WuxiaWorld(),
//        YaoiToshokan(),
//        YokaiJump(),
//        YuriVerso(),
//        ZinManga(),
//        ZManga(),

        // removed because scanlator site and they requested
        // AhStudios(),
        // KnightNoScanlation(),
    )
}

//@Nsfw
//class RawMangas : Madara("Raw Mangas", "https://rawmangas.net", "ja", SimpleDateFormat("MMMM dd, yyyy", Locale.US))

//class SiXiangScans : Madara("SiXiang Scans", "http://www.sixiangscans.com", "en", SimpleDateFormat("MMMM dd, yyyy", Locale.US))

//class SoloScanlation : Madara("SoloScanlation", "https://soloscanlation.site", "en", SimpleDateFormat("MMMM dd, yyyy", Locale.US))

class LovableSubs : Madara("LovableSubs", "https://lovablesubs.com", "tr", SimpleDateFormat("dd MMM yyyy", Locale("tr")))

class MangaOnlineCo : Madara("Manga-Online.co", "https://www.manga-online.co", "th", SimpleDateFormat("MMM dd, yyyy", Locale("th"))) {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}

//class NeatManga : Madara("NeatManga", "https://neatmanga.com", "en", SimpleDateFormat("dd MMM yyyy", Locale.US))

//class WarQueenScan : Madara("War Queen Scan", "https://wqscan.com", "pt-BR", SimpleDateFormat("dd/MM/yyyy", Locale.US))

//class StageComics : Madara("StageComics", "https://stagecomics.com", "pt-BR", SimpleDateFormat("MMMM dd, yyyy", Locale("pt"))) {
//    override fun chapterFromElement(element: Element): SChapter {
//        val parsedChapter = super.chapterFromElement(element)
//
//        parsedChapter.date_upload = element.select("img").firstOrNull()?.attr("alt")
//            ?.let { parseChapterDate(it) }
//            ?: parseChapterDate(element.select("span.chapter-release-date i").firstOrNull()?.text())
//
//        return parsedChapter
//    }
//}

//class SpookyScanlations : Madara("Spooky Scanlations", "https://spookyscanlations.xyz", "es", SimpleDateFormat("MMMM dd, yyyy", Locale("es")))

//class RandomTranslations : Madara("Random Translations", "https://randomtranslations.com", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US))

//class ManhuaFast : Madara("ManhuaFast", "https://manhuafast.com", "en") {
//    override val pageListParseSelector = "li.blocks-gallery-item"
//}

//class ManhuaSY : Madara("Manhua SY", "https://www.manhuasy.com", "en")

class MangaRave : Madara("MangaRave", "http://www.mangarave.com", "en")

//class NekoScan : Madara("NekoScan", "https://nekoscan.com", "en")

class Manga3S : Madara("Manga3S", "https://manga3s.com", "en")

//class MGKomik : Madara("MG Komik", "https://mgkomik.my.id", "id")

class MangaSco : Madara("MangaSco", "https://mangasco.com", "en")

class MangaCrimson : Madara("Manga Crimson", "https://mangacrimson.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale("tr")))

//class PrimeManga : Madara("Prime Manga", "https://primemanga.com", "en")

//class NekoBreaker : Madara("NekoBreaker", "https://nekobreaker.com", "pt-BR", SimpleDateFormat("MMMM dd, yyyy", Locale("pt")))

class ManganeloLink : Madara("Manganelo.link", "https://manganelo.link", "en")

class MangaRoma : Madara("MangaRoma", "https://mangaroma.com", "en")

//class NTSVoidScans : Madara("NTS Void Scans", "https://ntsvoidscans.com", "en")

//class ToonGod : Madara("ToonGod", "https://www.toongod.com", "en", SimpleDateFormat("dd MMM yyyy", Locale.US))

//class WebToonily : Madara("WebToonily", "https://webtoonily.com", "en")

class Manga18Fun : Madara("Manga18 Fun", "https://manga18.fun", "en")

class MangaYaku : Madara("MangaYaku", "https://mangayaku.my.id", "id")

//class RuyaManga : Madara("Rüya Manga", "https://www.ruyamanga.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")))

class MangaBaz : Madara("MangaBaz", "https://mangabaz.com", "tr", SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("tr")))

//class Manhuaga : Madara("Manhuaga", "https://manhuaga.com", "en")

class MangaCultivator : Madara("MangaCultivator", "https://mangacultivator.com", "en")

//class OffScan : Madara(
//    "Off Scan",
//    "https://offscan.top",
//    "pt-BR",
//    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
//)

class MangaClash : Madara(
    "Manga Clash",
    "https://mangaclash.com",
    "en",
    dateFormat = SimpleDateFormat("MM/dd/yy", Locale.US)
)

//class TritiniaScans : Madara("TritiniaScans", "https://tritinia.com", "en")

//class CopyPasteScan : Madara("CopyPasteScan", "https://copypastescan.xyz", "es")

class Mangasushi : Madara("Mangasushi", "https://mangasushi.net", "en")

class MangaRawr : Madara("MangaRawr", "https://mangarawr.com", "en")

//class ReadManhua : Madara(
//    "ReadManhua",
//    "https://readmanhua.net",
//    "en",
//    dateFormat = SimpleDateFormat("dd MMM yy", Locale.US)
//)




class KomikGo : Madara("KomikGo", "https://komikgo.com", "id")

//class TsubakiNoScan : Madara(
//    "Tsubaki No Scan",
//    "https://tsubakinoscan.com",
//    "fr",
//    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
//)

//class YokaiJump : Madara(
//    "Yokai Jump",
//    "https://yokaijump.fr",
//    "fr",
//    dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)
//)

//class ZManga : Madara("ZManga", "https://zmanga.org", "es")

class MangazukiMe : Madara("Mangazuki.me", "https://mangazuki.me", "en")

class MangazukiClubJP : Madara("Mangazuki.club", "https://mangazuki.club", "ja")

class MangazukiClubKO : Madara("Mangazuki.club", "https://mangazuki.club", "ko")

class MangaSY : Madara("Manga SY", "https://www.mangasy.com", "en") {
    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .cacheControl(CacheControl.FORCE_NETWORK)
        .build()
}

//class ManwhaClub : Madara("Manwha Club", "https://manhwa.club", "en")

//class WuxiaWorld : Madara("WuxiaWorld", "https://wuxiaworld.site", "en") {
//    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=views", headers)
//    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/tag/webcomic/page/$page/?m_orderby=latest", headers)
//    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = super.searchMangaRequest(page, "$query comics", filters)
//    override fun popularMangaNextPageSelector() = "div.nav-previous.float-left"
//}

//class ManyToon : Madara("ManyToon", "https://manytoon.com", "en")

//class ManyToonMe : Madara("ManyToon.me", "https://manytoon.me", "en")

//class BoysLove : Madara("BoysLove", "https://boyslove.me", "en")

//class ZinManga : Madara("Zin Translator", "https://zinmanga.com", "en") {
//    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
//        .add("Referer", "https://zinmanga.com/")
//}

//class ManwahentaiMe : Madara("Manwahentai.me", "https://manhwahentai.me", "en")

class Manga3asq : Madara("مانجا العاشق", "https://3asq.org", "ar")

//class Milftoon : Madara("Milftoon", "https://milftoon.xxx", "en") {
//    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=views", headers)
//    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?m_orderby=latest", headers)
//}

class MangaArabOnline : Madara("Manga Arab Online مانجا عرب اون لاين", "https://mangaarabonline.com", "ar", SimpleDateFormat("MMM d, yyyy", Locale.forLanguageTag("ar")))

class MangaArabTeam : Madara("مانجا عرب تيم Manga Arab Team", "https://mangaarabteam.com", "ar", SimpleDateFormat("dd MMM، yyyy", Locale.forLanguageTag("ar"))) {
    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!.replace("http:", "https:"))
    }
}

//class NightComic : Madara("Night Comic", "https://www.nightcomic.com", "en") {
//    override val formHeaders: Headers = headersBuilder()
//        .add("Content-Type", "application/x-www-form-urlencoded")
//        .add("X-MOD-SBB-CTYPE", "xhr")
//        .build()
//}

//class Skymanga : Madara("Skymanga", "https://skymanga.co", "en")

//@Nsfw
//class Toonily : Madara("Toonily", "https://toonily.com", "en") {
//    override fun getGenreList(): List<Genre> = listOf(
//        Genre("Action", "action-webtoon"),
//        Genre("Adult", "adult-webtoon"),
//        Genre("Adventure", "adventure-webtoon"),
//        Genre("Comedy", "comedy-webtoon"),
//        Genre("Drama", "drama-webtoon"),
//        Genre("Fantasy", "fantasy-webtoon"),
//        Genre("Gender Bender", "gender-bender"),
//        Genre("Gossip", "gossip"),
//        Genre("Harem", "harem-webtoon"),
//        Genre("Historical", "webtoon-historical"),
//        Genre("Horror", "horror-webtoon"),
//        Genre("Josei", "josei-manga"),
//        Genre("Mature", "mature-webtoon"),
//        Genre("Mystery", "mystery-webtoon"),
//        Genre("NTR", "ntr-webtoon"),
//        Genre("Psychological", "psychological-webtoon"),
//        Genre("Romance", "romance-webtoon"),
//        Genre("School life", "school-life-webtoon"),
//        Genre("Sci-Fi", "scifi-webtoon"),
//        Genre("Seinen", "seinen-webtoon"),
//        Genre("Shoujo", "shoujo"),
//        Genre("Shounen", "shounen-webtoon"),
//        Genre("Slice of Life", "sliceoflife-webtoon"),
//        Genre("Supernatural", "supernatural-webtoon"),
//        Genre("Thriller", "thriller-webtoon"),
//        Genre("Tragedy", "tragedy"),
//        Genre("Vanilla", "vanilla-webtoon"),
//        Genre("Yaoi", "yaoi-webtoon"),
//        Genre("Yuri", "yuri-webtoon")
//    )
//}

class MangaKomi : Madara("MangaKomi", "https://mangakomi.com", "en")

//@Nsfw
//class YaoiToshokan : Madara("Yaoi Toshokan", "https://yaoitoshokan.com.br", "pt-BR", SimpleDateFormat("dd MMM yyyy", Locale("pt", "BR"))) {
//    // Page has custom link to scan website.
//    override val popularMangaUrlSelector = "div.post-title a:not([target])"
//
//    // Chapters are listed old to new.
//    override fun chapterListParse(response: Response): List<SChapter> {
//        return super.chapterListParse(response).reversed()
//    }
//
//    override fun pageListParse(document: Document): List<Page> {
//        return document.select(pageListParseSelector)
//            .mapIndexed { index, element ->
//                // Had to add trim because of white space in source.
//                val imageUrl = element.select("img").attr("data-src").trim()
//                Page(index, document.location(), imageUrl)
//            }
//    }
//}

class Mangalek : Madara("مانجا ليك", "https://mangalek.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))

//class AstralLibrary : Madara("Astral Library", "https://www.astrallibrary.net", "en", SimpleDateFormat("d MMM", Locale.US)) {
//    override fun popularMangaRequest(page: Int): Request {
//        return GET("$baseUrl/manga-tag/manga/?m_orderby=views&page=$page", headers)
//    }
//
//    override fun latestUpdatesRequest(page: Int): Request {
//        return GET("$baseUrl/manga-tag/manga/?m_orderby=latest&page=$page", headers)
//    }
//}


//class MiracleScans : Madara("Miracle Scans", "https://miraclescans.com", "en")

//class Manhuasnet : Madara("Manhuas.net", "https://manhuas.net", "en")

class MangaTX : Madara("MangaTX", "https://mangatx.com", "en")

//class OnManga : Madara("OnManga", "https://onmanga.com", "en")

class MangaAction : Madara("Manga Action", "https://manga-action.com", "ar", SimpleDateFormat("yyyy-MM-dd", Locale("ar")))

//class NijiTranslations : Madara("Niji Translations", "https://niji-translations.com", "ar", SimpleDateFormat("MMMM dd, yyyy", Locale("ar")))

class LilyManga : Madara("Lily Manga", "https://lilymanga.com", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class MangaBob : Madara("MangaBob", "https://mangabob.com", "en")

//class ThreeSixtyFiveManga : Madara("365Manga", "https://365manga.com", "en") {
//    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=views", headers)
//    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manga/page/$page/?m_orderby=latest", headers)
//}

class MangaDods : Madara("MangaDods", "https://www.mangadods.com", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

//class NeoxScanlator : Madara(
//    "Neox Scanlator",
//    "https://neoxscans.com",
//    "pt-BR",
//    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
//) {
//    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
//        .connectTimeout(1, TimeUnit.MINUTES)
//        .readTimeout(1, TimeUnit.MINUTES)
//        .build()
//
//    override fun headersBuilder(): Headers.Builder = Headers.Builder()
//        .add("User-Agent", USER_AGENT)
//        .add("Referer", baseUrl)
//        .add("Origin", baseUrl)
//
//    // Only status and order by filter work.
//    override fun getFilterList(): FilterList = FilterList(super.getFilterList().slice(3..4))
//
//    companion object {
//        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
//            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36"
//    }
//}

class MangaLord : Madara("Manga Lord", "https://mangalord.com", "en")

//@Nsfw
//class PornComix : Madara("PornComix", "https://www.porncomixonline.net", "en")

//class PMScans : Madara("PMScans", "https://www.pmscans.com", "en", SimpleDateFormat("MMM-dd-yy", Locale.US))

class MangaRead : Madara("Manga Read", "https://mangaread.co", "en", SimpleDateFormat("yyyy-MM-dd", Locale.US))

class Manga68 : Madara("Manga68", "https://manga68.com", "en") {
    override val pageListParseSelector = "div.page-break, div.text-left p"
}

//class ManhuaBox : Madara("ManhuaBox", "https://manhuabox.net", "en") {
//    override val pageListParseSelector = "div.page-break, div.text-left p img"
//}

//class RaiderScans : Madara("Raider Scans", "https://raiderscans.com", "en")

//class PojokManga : Madara("Pojok Manga", "https://pojokmanga.com", "id", SimpleDateFormat("MMM dd, yyyy", Locale.US))

//class TopManhua : Madara("Top Manhua", "https://topmanhua.com", "en", SimpleDateFormat("MM/dd/yy", Locale.US)) {
//    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
//}

//class ManyToonClub : Madara("ManyToonClub", "https://manytoon.club", "ko")

//class ManhuaUS : Madara("ManhuaUS", "https://manhuaus.com", "en") {
//    override val pageListParseSelector = "li.blocks-gallery-item"
//}

class MangaWT : Madara("MangaWT", "https://mangawt.com", "tr")

class MangaRockTeam : Madara("Manga Rock Team", "https://mangarockteam.com", "en")

//class MixedManga : Madara("Mixed Manga", "https://mixedmanga.com", "en", SimpleDateFormat("d MMM yyyy", Locale.US)) {
//    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", baseUrl)
//}

//class ManhuasWorld : Madara("Manhuas World", "https://manhuasworld.com", "en")

//class ManhwaRaw : Madara("Manhwa Raw", "https://manhwaraw.com", "ko")

//class WeScans : Madara("WeScans", "https://wescans.xyz", "en") {
//    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=views", headers)
//    override fun popularMangaNextPageSelector(): String? = null
//    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/manhua/manga/?m_orderby=latest", headers)
//    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/manhua/?s=$query&post_type=wp-manga")
//    override fun searchMangaNextPageSelector(): String? = null
//    override fun getFilterList(): FilterList = FilterList()
//}

@Nsfw
class MangaHentai : Madara("Manga Hentai", "https://mangahentai.me", "en")

class MangaPhoenix : Madara("Manga Diyari", "https://mangadiyari.com", "tr") {
    // Formerly "Manga Phoenix"
    override val id = 4308007020001642101
}

//class MartialScans : Madara("Martial Scans", "https://martialscans.com", "en") {
//    override fun popularMangaFromElement(element: Element): SManga {
//        val manga = SManga.create()
//
//        with(element) {
//            select(popularMangaUrlSelector).last()?.let {
//                manga.setUrlWithoutDomain(it.attr("href"))
//                manga.title = it.ownText()
//            }
//
//            select("img").last()?.let {
//                manga.thumbnail_url = imageFromElement(it)
//            }
//        }
//
//        return manga
//    }
//
//    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
//}

class MangaYosh : Madara("MangaYosh", "https://mangayosh.xyz", "id", SimpleDateFormat("dd MMM yyyy", Locale.US))

//class WebtoonXYZ : Madara("WebtoonXYZ", "https://www.webtoon.xyz", "en") {
//    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""
//    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=views", headers)
//    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/webtoons/${pagePath(page)}?m_orderby=latest", headers)
//}

class MangaReadOrg : Madara("MangaRead.org", "https://www.mangaread.org", "en", SimpleDateFormat("dd.MM.yyy", Locale.US))

//class TurkceManga : Madara("Türkçe Manga", "https://turkcemanga.com", "tr") {
//    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=views", headers)
//    override fun popularMangaSelector() = searchMangaSelector()
//    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
//    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page/?s&post_type=wp-manga&m_orderby=latest", headers)
//    override fun latestUpdatesSelector() = searchMangaSelector()
//    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)
//}

class Manga347 : Madara("Manga347", "https://manga347.com", "en", SimpleDateFormat("d MMM, yyyy", Locale.US)) {
    override val pageListParseSelector = "li.blocks-gallery-item"
}

//class RenaScans : Madara("Renascence Scans (Renascans)", "https://new.renascans.com", "en", SimpleDateFormat("dd/MM/yyyy", Locale.US))

//class QueensManga : Madara("QueensManga ملكات المانجا", "https://queensmanga.com", "ar") {
//    override fun chapterListSelector(): String = "div.listing-chapters_wrap a"
//}

//class TheTopComic : Madara("TheTopComic", "https://thetopcomic.com", "en")

//class WebNovel : Madara("WebNovel", "https://webnovel.live", "en")

//class TruyenTranhAudioCom : Madara("TruyenTranhAudio.com", "https://truyentranhaudio.com", "vi", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
//    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=views", headers)
//    override fun popularMangaSelector() = searchMangaSelector()
//    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)
//    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page?s&post_type=wp-manga&m_orderby=latest", headers)
//    override fun latestUpdatesSelector() = searchMangaSelector()
//    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)
//    override fun pageListParse(document: Document): List<Page> {
//        return document.select("div.reading-content img").map { it.attr("abs:src") }
//            .filterNot { it.isNullOrEmpty() }
//            .distinct()
//            .mapIndexed { i, url -> Page(i, "", url) }
//    }
//}

//class TruyenTranhAudioOnline : Madara("TruyenTranhAudio.online", "https://truyentranhaudio.online", "vi", SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())) {
//    override val formHeaders: Headers = headersBuilder()
//        .add("Content-Type", "application/x-www-form-urlencoded")
//        .build()
//
//    override fun pageListParse(document: Document): List<Page> {
//        return document.select("div.reading-content img").map { it.attr("abs:src") }
//            .filterNot { it.isNullOrEmpty() }
//            .distinct()
//            .mapIndexed { i, url -> Page(i, "", url) }
//    }
//}

class MangaTurf : Madara("Manga Turf", "https://mangaturf.com", "en")

class Mangareceh : Madara("Mangareceh", "https://mangareceh.id", "id")

//class ToonPoint : Madara("ToonPoint", "https://toonpoint.com", "en") {
//    override val userAgentRandomizer = ""
//}

class MangaScantrad : Madara("Manga-Scantrad", "https://manga-scantrad.net", "fr", SimpleDateFormat("d MMM yyyy", Locale.FRANCE))

//class ManhuaPlus : Madara("Manhua Plus", "https://manhuaplus.com", "en") {
//    override val pageListParseSelector = "li.blocks-gallery-item"
//}

//@Nsfw
//class ToonilyNet : Madara("Toonily.net", "https://toonily.net", "en")

//class TwilightScans : Madara("Twilight Scans", "https://twilightscans.com", "en")

// mostly novels
//class WoopRead : Madara("WoopRead", "https://woopread.com", "en")

//class Ninjavi : Madara("Ninjavi", "https://ninjavi.com", "ar")

//class ManhwaTop : Madara("Manhwatop", "https://manhwatop.com", "en")


//class Wakascan : Madara("Wakascan", "https://wakascan.com", "fr")

//class ShoujoHearts : Madara("ShoujoHearts", "http://shoujohearts.com", "en") {
//    override fun popularMangaRequest(page: Int): Request =
//        POST("$baseUrl/reader/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, true).build(), CacheControl.FORCE_NETWORK)
//
//    override fun latestUpdatesRequest(page: Int): Request =
//        POST("$baseUrl/reader/wp-admin/admin-ajax.php", formHeaders, formBuilder(page, false).build(), CacheControl.FORCE_NETWORK)
//
//    override fun searchPage(page: Int): String = "reader/page/$page/"
//}

//class OlaoeManga : Madara("مانجا اولاو", "https://olaoe.giize.com", "ar")

//class OrigamiOrpheans : Madara("Origami Orpheans", "https://origami-orpheans.com.br", "pt-BR")

class MangaKiss : Madara("Manga Kiss", "https://mangakiss.org", "en")

class MangaRocky : Madara("Manga Rocky", "https://mangarocky.com", "en")

//class S2Manga : Madara("S2Manga", "https://s2manga.com", "en")

class MangaLandArabic : Madara("Manga Land Arabic", "https://mangalandarabic.com", "ar")


//class ProjetoScanlator : Madara("Projeto Scanlator", "https://projetoscanlator.com", "pt-BR", SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")))

//class NazarickScans : Madara("Nazarick Scans", "https://nazarickscans.com", "en") {
//    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga/page/$page/?m_orderby=trending", headers)
//    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga/page/$page/?m_orderby=trending", headers)
//    override fun popularMangaNextPageSelector(): String? = null
//    override fun latestUpdatesNextPageSelector(): String? = null
//}

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

//class MysticalMerries : Madara("Mystical Merries", "https://mysticalmerries.com", "en") {
//    override fun popularMangaRequest(page: Int) = GET("$baseUrl/genre/manhwa/page/$page/?m_orderby=trending", headers)
//    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/genre/manhwa/page/$page/?m_orderby=latest", headers)
//}

class MangaNine : Madara("Manga Nine", "https://manganine.com", "en")

//class MarkScans : Madara("Mark Scans", "https://markscans.online", "pt-BR")

//class YuriVerso : Madara(
//    "Yuri Verso",
//    "https://yuri.live",
//    "pt-BR",
//    SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
//)

class MangaStein : Madara("MangaStein", "https://mangastein.com", "tr")

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

//class Voidscans : Madara("Void Scans", "https://voidscans.com", "en") {
//    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/?s=$query&post_type=wp-manga")
//}


//class UyuyanBalik : Madara("Uyuyan Balik", "https://uyuyanbalik.com/", "tr", SimpleDateFormat("dd MMMM yyyy", Locale.US))

class MangaWeebs : Madara("Manga Weebs", "https://mangaweebs.in", "en")

//class MMScans : Madara("MMScans", "https://mm-scans.com/", "en")

//@Nsfw
//class Siyahmelek : Madara("Siyahmelek", "https://siyahmelek.com", "tr", SimpleDateFormat("dd MMM yyyy", Locale("tr")))

//@Nsfw
//class SekteDoujin : Madara("Sekte Doujin", "https://sektedoujin.xyz", "id")

//class MundoWuxia : Madara("Mundo Wuxia", "https://mundowuxia.com", "es", SimpleDateFormat("MMMM dd, yyyy", Locale("es")))

//class WorldRomanceTranslation : Madara("World Romance Translation", "https://wrt.my.id/", "id", SimpleDateFormat("dd MMMM yyyy", Locale("id")))

//class VanguardBun : Madara("Vanguard Bun", "https://vanguardbun.com/", "en")

class MangaBin : Madara("Manga Bin", "https://mangabin.com/", "en")

class MangaChill : Madara("Manga Chill", "https://mangachill.com/", "en")


