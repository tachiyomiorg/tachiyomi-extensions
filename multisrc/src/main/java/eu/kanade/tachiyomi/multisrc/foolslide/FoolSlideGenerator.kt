package eu.kanade.tachiyomi.multisrc.foolslide

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class FoolSlideGenerator : ThemeSourceGenerator {

    override val themePkg = "foolslide"

    override val themeClass = "FoolSlide"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("The Cat Scans", "https://reader2.thecatscans.com/", "en"),
        SingleLang("Silent Sky", "https://reader.silentsky-scans.net", "en"),
        SingleLang("Death Toll Scans", "https://reader.deathtollscans.net", "en"),
        SingleLang("One Time Scans", "https://reader.otscans.com", "en"),
        SingleLang("MangaScouts", "http://onlinereader.mangascouts.org", "de"),
        SingleLang("Lilyreader", "https://manga.smuglo.li", "en"),
        SingleLang("Evil Flowers", "https://reader.evilflowers.com", "en"),
        SingleLang("Русификация", "https://rusmanga.ru", "ru", className = "Russification"),
        SingleLang("PowerManga", "https://reader.powermanga.org", "it", className = "PowerMangaIT"),
        MultiLang("FoolSlide Customizable", "",  listOf("other")),
        SingleLang("Menudo-Fansub", "https://www.menudo-fansub.com", "es", className = "MenudoFansub", overrideVersionCode = 1),
        SingleLang("Sense-Scans", "https://sensescans.com", "en", className = "SenseScans", overrideVersionCode = 1),
        SingleLang("Kirei Cake", "https://reader.kireicake.com", "en"),
        SingleLang("Mangatellers", "http://www.mangatellers.gr", "en"),
        SingleLang("Iskultrip Scans", "https://maryfaye.net", "en"),
        SingleLang("Anata no Motokare", "https://motokare.xyz", "en", className = "AnataNoMotokare"),
        SingleLang("Yuri-ism", "https://www.yuri-ism.net", "en", className = "YuriIsm"),
        SingleLang("Ajia no Scantrad", "https://www.ajianoscantrad.fr", "fr", className = "AjiaNoScantrad"),
        SingleLang("Storm in Heaven", "https://www.storm-in-heaven.net", "it", className = "StormInHeaven"),
        SingleLang("LupiTeam", "https://lupiteam.net", "it"),
        SingleLang("Zandy no Fansub", "https://zandynofansub.aishiteru.org", "en"),
        SingleLang("Helvetica Scans", "https://helveticascans.com", "en"),
        SingleLang("Kirishima Fansub", "https://www.kirishimafansub.net", "es"),
        SingleLang("Baixar Hentai", "https://leitura.baixarhentai.net", "pt-BR", isNsfw = true),
        SingleLang("HNI-Scantrad", "https://hni-scantrad.com", "fr", className = "HNIScantrad"),
        SingleLang("HNI-Scantrad", "https://hni-scantrad.com", "en", className = "HNIScantradEN"),
        SingleLang("The Phoenix Scans", "https://www.phoenixscans.com", "it", className = "PhoenixScans"),
        SingleLang("GTO The Great Site", "https://www.gtothegreatsite.net", "it", className = "GTO"),
        SingleLang("Fall World Reader", "https://faworeader.altervista.org", "it", className = "FallenWorldOrder"),
        SingleLang("NIFTeam", "http://read-nifteam.info", "it"),
        SingleLang("TuttoAnimeManga", "https://tuttoanimemanga.net", "it"),
        SingleLang("Tortuga Ceviri", "http://tortuga-ceviri.com", "tr"),
        SingleLang("Rama", "https://www.ramareader.it", "it"),
        SingleLang("Mabushimajo", "http://mabushimajo.com", "tr"),
        SingleLang("Hentai Cafe", "https://hentai.cafe", "en", isNsfw = true),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            FoolSlideGenerator().createAll()
        }
    }
}
