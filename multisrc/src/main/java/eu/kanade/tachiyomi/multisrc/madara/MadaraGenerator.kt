package eu.kanade.tachiyomi.multisrc.madara

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.SingleLangThemeSourceData
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.MultiLangThemeSourceData

class MadaraGenerator : ThemeSourceGenerator {

    override val themePkg = "madara"

    override val themeClass = "Madara"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLangThemeSourceData("Adonis Fansub", "https://manga.adonisfansub.com", "tr"),
        SingleLangThemeSourceData("AkuManga", "https://akumanga.com", "ar"),
        SingleLangThemeSourceData("AllPornComic", "https://allporncomic.com", "en"),
        SingleLangThemeSourceData("Aloalivn", "https://aloalivn.com", "en"),
        SingleLangThemeSourceData("AniMangaEs", "http://animangaes.com", "en"),
        SingleLangThemeSourceData("Agent of Change Translations", "https://aoc.moe", "en"),
        SingleLangThemeSourceData("ApollComics", "https://apollcomics.xyz", "es"),
        SingleLangThemeSourceData("Arang Scans", "https://www.arangscans.com", "en"),
        SingleLangThemeSourceData("ArazNovel", "https://www.araznovel.com", "tr"),
        SingleLangThemeSourceData("Asgard Team", "https://www.asgard1team.com", "ar"),
        SingleLangThemeSourceData("Astral Library", "https://www.astrallibrary.net", "en"),
        SingleLangThemeSourceData("Bakaman", "https://bakaman.net", "th"),
        SingleLangThemeSourceData("BestManga", "https://bestmanga.club", "ru"),
        SingleLangThemeSourceData("BestManhua", "https://bestmanhua.com", "en"),
        SingleLangThemeSourceData("BoysLove", "https://boyslove.me", "en"),
        SingleLangThemeSourceData("CatOnHeadTranslations", "https://catonhead.com", "en"),
        SingleLangThemeSourceData("CAT-translator", "https://cat-translator.com", "th", className = "CatTranslator", pkgName = "cattranslator"),
        SingleLangThemeSourceData("Chibi Manga", "https://www.cmreader.info", "en"),
        SingleLangThemeSourceData("ComicKiba", "https://comickiba.com", "en"),
        SingleLangThemeSourceData("Comics Valley", "https://comicsvalley.com", "hi", isNsfw = true),
        SingleLangThemeSourceData("CopyPasteScan", "https://copypastescan.xyz", "es"),
        SingleLangThemeSourceData("Cutie Pie", "https://cutiepie.ga", "tr"),
        SingleLangThemeSourceData("Darkyu Realm", "https://darkyuerealm.site", "pt-BR"),
        SingleLangThemeSourceData("Decadence Scans", "https://reader.decadencescans.com", "en"),
        SingleLangThemeSourceData("شبكة كونان العربية", "https://www.manga.detectiveconanar.com", "ar", className="DetectiveConanAr"),
        SingleLangThemeSourceData("DiamondFansub", "https://diamondfansub.com", "tr"),
        SingleLangThemeSourceData("Disaster Scans", "https://disasterscans.com", "en"),
        SingleLangThemeSourceData("DoujinHentai", "https://doujinhentai.net", "es", isNsfw = true),
        SingleLangThemeSourceData("DoujinYosh", "https://doujinyosh.work", "id"),
        SingleLangThemeSourceData("Drope Scan", "https://dropescan.com", "pt-BR"),
        SingleLangThemeSourceData("Einherjar Scan", "https://einherjarscans.space", "en"),
        SingleLangThemeSourceData("1st Kiss", "https://1stkissmanga.com", "en", className = "FirstKissManga"),
        SingleLangThemeSourceData("1st Kiss Manhua", "https://1stkissmanhua.com","en", className="FirstKissManhua"),
        SingleLangThemeSourceData("FreeWebtoonCoins", "https://freewebtooncoins.com", "en"),
        SingleLangThemeSourceData("Furio Scans", "https://furioscans.com", "pt-BR"),
        SingleLangThemeSourceData("موقع لترجمة المانجا", "https://golden-manga.com", "ar", className = "GoldenManga"),
        SingleLangThemeSourceData("GuncelManga", "https://guncelmanga.com", "tr"),
        SingleLangThemeSourceData("Hero Manhua", "https://heromanhua.com", "en"),
        SingleLangThemeSourceData("Heroz Scanlation", "https://herozscans.com", "en"),
        SingleLangThemeSourceData("Himera Fansub", "https://himera-fansub.com", "tr"),
        SingleLangThemeSourceData("Hiperdex", "https://hiperdex.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MadaraGenerator().createAll()
        }
    }
}
