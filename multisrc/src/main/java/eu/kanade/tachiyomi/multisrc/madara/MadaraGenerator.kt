package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.ThemeSourceData.SingleLang

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
        SingleLang("BestManhua", "https://bestmanhua.com", "en"),
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
        SingleLang("شبكة كونان العربية", "https://www.manga.detectiveconanar.com", "ar", className="DetectiveConanAr"),
        SingleLang("DiamondFansub", "https://diamondfansub.com", "tr"),
        SingleLang("Disaster Scans", "https://disasterscans.com", "en"),
        SingleLang("DoujinHentai", "https://doujinhentai.net", "es", isNsfw = true),
        SingleLang("DoujinYosh", "https://doujinyosh.work", "id"),
        SingleLang("Dream Manga", "https://dreammanga.com/", "en"),
        SingleLang("Drope Scan", "https://dropescan.com", "pt-BR"),
        SingleLang("Einherjar Scan", "https://einherjarscans.space", "en"),
        SingleLang("FDM Scan", "https://fdmscan.com", "pt-BR"),
        SingleLang("1st Kiss", "https://1stkissmanga.com", "en", className = "FirstKissManga"),
        SingleLang("1st Kiss Manhua", "https://1stkissmanhua.com","en", className="FirstKissManhua"),
        SingleLang("FreeWebtoonCoins", "https://freewebtooncoins.com", "en"),
        SingleLang("Furio Scans", "https://furioscans.com", "pt-BR"),
        SingleLang("Gecenin Lordu", "https://geceninlordu.com/", "tr"),
        SingleLang("موقع لترجمة المانجا", "https://golden-manga.com", "ar", className = "GoldenManga"),
        SingleLang("GuncelManga", "https://guncelmanga.com", "tr"),
        SingleLang("Hero Manhua", "https://heromanhua.com", "en"),
        SingleLang("Heroz Scanlation", "https://herozscans.com", "en"),
        SingleLang("Hikari Scan", "https://hikariscan.com.br", "pt-BR"),
        SingleLang("Himera Fansub", "https://himera-fansub.com", "tr"),
        SingleLang("Hiperdex", "https://hiperdex.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MadaraGenerator().createAll()
        }
    }
}
