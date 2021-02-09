package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.multisrc.ThemeSourceData.SingleLang
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator

class MadaraGenerator : ThemeSourceGenerator {

    override val themePkg = "madara"

    override val themeClass = "Madara"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
            SingleLang("Adonis Fansub", "https://manga.adonisfansub.com", "tr"),
            SingleLang("AkuManga", "https://akumanga.com", "ar"),
            SingleLang("AlianzaMarcial", "https://alianzamarcial.xyz", "es"),
            SingleLang("AllPornComic", "https://allporncomic.com", "en"),
            SingleLang("Aloalivn", "https://aloalivn.com", "en"),
            SingleLang("AniMangaEs", "http://animangaes.com", "en"),
            SingleLang("Agent of Change Translations", "https://aoc.moe", "en"),
            SingleLang("ApollComics", "https://apollcomics.xyz", "es"),
            SingleLang("Arang Scans", "https://www.arangscans.com", "en"),
            SingleLang("ArazNovel", "https://www.araznovel.com", "tr"),
            SingleLang("Argos Scan", "https://argosscan.com", "pt-BR"),
            SingleLang("Asgard Team", "https://www.asgard1team.com", "ar"),
            SingleLang("Astral Library", "https://www.astrallibrary.net", "en"),
            SingleLang("Atikrost", "https://atikrost.com", "tr"),
            SingleLang("ATM-Subs", "https://atm-subs.fr", "fr", className = "ATMSubs", pkgName = "atmsubs"),
            SingleLang("Azora", "https://www.azoramanga.com", "ar"),
            SingleLang("Bakaman", "https://bakaman.net", "th"),
            SingleLang("BestManga", "https://bestmanga.club", "ru"),
            SingleLang("BestManhua", "https://bestmanhua.com", "en", overrideVersionCode = 1),
            SingleLang("BoysLove", "https://boyslove.me", "en"),
            SingleLang("CatOnHeadTranslations", "https://catonhead.com", "en"),
            SingleLang("CAT-translator", "https://cat-translator.com", "th", className = "CatTranslator", pkgName = "cattranslator"),
            SingleLang("Chibi Manga", "https://www.cmreader.info", "en"),
            SingleLang("Clover Manga", "Clover Manga", "tr"),
            SingleLang("ComicKiba", "https://comickiba.com", "en"),
            SingleLang("Comics Valley", "https://comicsvalley.com", "hi", isNsfw = true),
            SingleLang("CopyPasteScan", "https://copypastescan.xyz", "es"),
            SingleLang("Cutie Pie", "https://cutiepie.ga", "tr"),
            SingleLang("Darkyu Realm", "https://darkyuerealm.site", "pt-BR"),
            SingleLang("Decadence Scans", "https://reader.decadencescans.com", "en"),
            SingleLang("شبكة كونان العربية", "https://www.manga.detectiveconanar.com", "ar", className = "DetectiveConanAr"),
            SingleLang("DiamondFansub", "https://diamondfansub.com", "tr"),
            SingleLang("Disaster Scans", "https://disasterscans.com", "en"),
            SingleLang("DoujinHentai", "https://doujinhentai.net", "es", isNsfw = true),
            SingleLang("DoujinYosh", "https://doujinyosh.work", "id"),
            SingleLang("Dream Manga", "https://dreammanga.com/", "en"),
            SingleLang("Drope Scan", "https://dropescan.com", "pt-BR"),
            SingleLang("Einherjar Scan", "https://einherjarscans.space", "en"),
            SingleLang("FDM Scan", "https://fdmscan.com", "pt-BR"),
            SingleLang("1st Kiss", "https://1stkissmanga.com", "en", className = "FirstKissManga", overrideVersionCode = 1),
            SingleLang("1st Kiss Manhua", "https://1stkissmanhua.com", "en", className = "FirstKissManhua"),
            SingleLang("FreeWebtoonCoins", "https://freewebtooncoins.com", "en"),
            SingleLang("Furio Scans", "https://furioscans.com", "pt-BR"),
            SingleLang("Gecenin Lordu", "https://geceninlordu.com/", "tr"),
            SingleLang("موقع لترجمة المانجا", "https://golden-manga.com", "ar", className = "GoldenManga"),
            SingleLang("Graze Scans", "https://grazescans.com/", "en"),
            SingleLang("Gourmet Scans", "https://gourmetscans.net/", "en"),
            SingleLang("GuncelManga", "https://guncelmanga.com", "tr"),
            SingleLang("Hero Manhua", "https://heromanhua.com", "en"),
            SingleLang("Heroz Scanlation", "https://herozscans.com", "en"),
            SingleLang("Hikari Scan", "https://hikariscan.com.br", "pt-BR"),
            SingleLang("Himera Fansub", "https://himera-fansub.com", "tr"),
            SingleLang("Hiperdex", "https://hiperdex.com", "en"),
            SingleLang("Hscans", "https://hscans.com", "en"),
            SingleLang("Hunter Fansub", "https://hunterfansub.com", "es"),
            SingleLang("Ichirin No Hana Yuri", "https://ichirinnohanayuri.com.br", "pt-BR"),
            SingleLang("Immortal Updates", "https://immortalupdates.com", "en"),
            SingleLang("IsekaiScan.com", "https://isekaiscan.com", "en", className = "IsekaiScanCom"),
            SingleLang("Its Your Right Manhua", "https://itsyourightmanhua.com/", "en"),
            SingleLang("JJutsuScans", "https://jjutsuscans.com", "en"),
            SingleLang("Just For Fun", "https://just-for-fun.ru", "ru"),
            SingleLang("KingzManga", "https://kingzmanga.com", "ar"),
            SingleLang("KisekiManga", "https://kisekimanga.com", "en"),
            SingleLang("KlikManga", "https://klikmanga.com", "id"),
            SingleLang("Kissmanga.in", "https://kissmanga.in", "en", className= "KissmangaIn"),
            SingleLang("Kombatch", "https://kombatch.com", "id"),

            SingleLang("Mangazuki.online", "http://mangazukinew.online", "en", className = "MangazukiOnline"),
            SingleLang("ManhuaBox", "https://manhuabox.net", "en"),
            SingleLang("ManhuaFast", "https://manhuafast.com", "en"),
            SingleLang("Manhuaga", "https://manhuaga.com", "en"),
            SingleLang("Manhua Plus", "https://manhuaplus.com", "en"),
            SingleLang("Manhuas.net", "https://manhuas.net", "en"),
            SingleLang("Manhuas World", "https://manhuasworld.com", "en"),
            SingleLang("Manhua SY", "https://www.manhuasy.com", "en"),
            SingleLang("ManhuaUS", "https://manhuaus.com", "en"),
            SingleLang("Manhwa Raw", "https://manhwaraw.com", "ko"),
            SingleLang("Manhwatop", "https://manhwatop.com", "en"),
            SingleLang("Manwahentai.me", "https://manhwahentai.me", "en"),
            SingleLang("Manwha Club", "https://manhwa.club", "en"),
            SingleLang("ManyToon", "https://manytoon.com", "en"),
            SingleLang("ManyToonClub", "https://manytoon.club", "ko"),
            SingleLang("ManyToon.me", "https://manytoon.me", "en"),
            SingleLang("Mark Scans", "https://markscans.online", "pt-BR"),
            SingleLang("Martial Scans", "https://martialscans.com", "en"),
            SingleLang("MG Komik", "https://mgkomik.my.id", "id"),
            SingleLang("Milftoon", "https://milftoon.xxx", "en"),
            SingleLang("Miracle Scans", "https://miraclescans.com", "en"),
            SingleLang("Mixed Manga", "https://mixedmanga.com", "en"),
            SingleLang("MMScans", "https://mm-scans.com/", "en"),
            SingleLang("Mundo Wuxia", "https://mundowuxia.com", "es"),
            SingleLang("Mystical Merries", "https://mysticalmerries.com", "en"),
            SingleLang("Nazarick Scans", "https://nazarickscans.com", "en"),
            SingleLang("NeatManga", "https://neatmanga.com", "en"),
            SingleLang("NekoBreaker", "https://nekobreaker.com", "pt-BR"),
            SingleLang("NekoScan", "https://nekoscan.com", "en"),
            SingleLang("Neox Scanlator", "https://neoxscans.com", "pt-BR"),
            SingleLang("Night Comic", "https://www.nightcomic.com", "en"),
            SingleLang("Niji Translations", "https://niji-translations.com", "ar"),
            SingleLang("Ninjavi", "https://ninjavi.com", "ar"),
            SingleLang("NTS Void Scans", "https://ntsvoidscans.com", "en"),
            SingleLang( "Off Scan", "https://offscan.top", "pt-BR"),
            SingleLang("مانجا اولاو", "https://olaoe.giize.com", "ar", className = "OlaoeManga"),
            SingleLang("OnManga", "https://onmanga.com", "en"),
            SingleLang("Origami Orpheans", "https://origami-orpheans.com.br", "pt-BR"),
            SingleLang("PMScans", "https://www.pmscans.com", "en"),
            SingleLang("Pojok Manga", "https://pojokmanga.com", "id"),
            SingleLang("PornComix", "https://www.porncomixonline.net", "en", isNsfw = true),
            SingleLang("Prime Manga", "https://primemanga.com", "en"),
            SingleLang("Projeto Scanlator", "https://projetoscanlator.com", "pt-BR"),
            SingleLang("QueensManga ملكات المانجا", "https://queensmanga.com", "ar", className = "QueensManga"),
            SingleLang("Raider Scans", "https://raiderscans.com", "en"),
            SingleLang("Random Translations", "https://randomtranslations.com", "en"),
            SingleLang("Raw Mangas", "https://rawmangas.net", "ja", isNsfw = true),
            SingleLang("ReadManhua", "https://readmanhua.net", "en"),
            SingleLang("Renascence Scans (Renascans)", "https://new.renascans.com", "en", className = "RenaScans"),
            SingleLang("Rüya Manga", "https://www.ruyamanga.com", "tr", className = "RuyaManga"),
            SingleLang("S2Manga", "https://s2manga.com", "en"),
            SingleLang("Sekte Doujin", "https://sektedoujin.xyz", "id", isNsfw = true),
            SingleLang("ShoujoHearts", "http://shoujohearts.com", "en"),
            SingleLang("SiXiang Scans", "http://www.sixiangscans.com", "en"),
            SingleLang("Siyahmelek", "https://siyahmelek.com", "tr", isNsfw = true),
            SingleLang("Skymanga", "https://skymanga.co", "en"),
            SingleLang("SoloScanlation", "https://soloscanlation.site", "en"),
            SingleLang("Spooky Scanlations", "https://spookyscanlations.xyz", "es"),
            SingleLang("StageComics", "https://stagecomics.com", "pt-BR"),
            SingleLang("TheTopComic", "https://thetopcomic.com", "en"),
            SingleLang("365Manga", "https://365manga.com", "en", className = "ThreeSixtyFiveManga"),
            SingleLang("ToonGod", "https://www.toongod.com", "en"),
            SingleLang("Toonily", "https://toonily.com", "en", isNsfw = true),
            SingleLang("Toonily.net", "https://toonily.net", "en", isNsfw = true),
            SingleLang("ToonPoint", "https://toonpoint.com", "en"),
            SingleLang("Top Manhua", "https://topmanhua.com", "en"),
            SingleLang("TritiniaScans", "https://tritinia.com", "en"),
            SingleLang("TruyenTranhAudio.com", "https://truyentranhaudio.com", "vi"),
            SingleLang("TruyenTranhAudio.online", "https://truyentranhaudio.online", "vi", className = "truyentranhaudioonline"),
            SingleLang("Tsubaki No Scan", "https://tsubakinoscan.com", "fr"),
            SingleLang("Türkçe Manga", "https://turkcemanga.com", "tr"),
            SingleLang("Twilight Scans", "https://twilightscans.com", "en"),
            SingleLang("Uyuyan Balik", "https://uyuyanbalik.com/", "tr"),
            SingleLang("Vanguard Bun", "https://vanguardbun.com/", "en"),
            SingleLang("Void Scans", "https://voidscans.com", "en"),
            SingleLang("Wakascan", "https://wakascan.com", "fr"),
            SingleLang("War Queen Scan", "https://wqscan.com", "pt-BR"),
            SingleLang("WebNovel", "https://webnovel.live", "en"),
            SingleLang("WebToonily", "https://webtoonily.com", "en"),
            SingleLang("WebtoonXYZ", "https://www.webtoon.xyz", "en"),
            SingleLang("WeScans", "https://wescans.xyz", "en"),
            SingleLang("WoopRead", "https://woopread.com", "en"),
            SingleLang("World Romance Translation", "https://wrt.my.id/", "id"),
            SingleLang("WuxiaWorld", "https://wuxiaworld.site", "en"),
            SingleLang("Yaoi Toshokan", "https://yaoitoshokan.com.br", "pt-BR", isNsfw = true),
            SingleLang("Yokai Jump", "https://yokaijump.fr", "fr"),
            SingleLang("Yuri Verso", "https://yuri.live", "pt-BR"),
            SingleLang("Zin Translator", "https://zinmanga.com", "en"),
            SingleLang("ZManga", "https://zmanga.org", "es"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MadaraGenerator().createAll()
        }
    }
}
