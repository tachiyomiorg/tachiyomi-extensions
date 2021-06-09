package eu.kanade.tachiyomi.multisrc.wpmangastream

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class WPMangaStreamGenerator : ThemeSourceGenerator {

    override val themePkg = "wpmangastream"

    override val themeClass = "WPMangaStream"

    override val baseVersionCode: Int = 6

    override val sources = listOf(
            SingleLang("Asura Scans", "https://www.asurascans.com", "en", overrideVersionCode = 1),
            SingleLang("KlanKomik", "https://klankomik.com", "id", overrideVersionCode = 1),
            SingleLang("MasterKomik", "https://masterkomik.com", "id", overrideVersionCode = 1),
            SingleLang("Kaisar Komik", "https://kaisarkomik.com", "id", overrideVersionCode = 1),
            SingleLang("Rawkuma", "https://rawkuma.com/", "ja"),
            SingleLang("Boosei", "https://boosei.com", "id", overrideVersionCode = 1),
            SingleLang("Mangakyo", "https://www.mangakyo.me", "id"),
            SingleLang("Sekte Komik", "https://sektekomik.com", "id", overrideVersionCode = 3),
            SingleLang("Komik Station", "https://komikstation.com", "id", overrideVersionCode = 2),
            SingleLang("Non-Stop Scans", "https://www.nonstopscans.com", "en", className = "NonStopScans"),
            SingleLang("KomikIndo.co", "https://komikindo.co", "id", className = "KomikindoCo", overrideVersionCode = 1),
            SingleLang("Readkomik", "https://readkomik.com", "en", className = "ReadKomik", overrideVersionCode = 1),
            SingleLang("MangaIndonesia", "https://mangaindonesia.net", "id"),
            SingleLang("Liebe Schnee Hiver", "https://www.liebeschneehiver.com", "tr"),
            SingleLang("GURU Komik", "https://gurukomik.com", "id"),
            SingleLang("Shea Manga", "https://sheamanga.my.id", "id", overrideVersionCode = 1),
            SingleLang("Komik AV", "https://komikav.com", "id", overrideVersionCode = 1),
            SingleLang("Komik Cast", "https://komikcast.com", "id", overrideVersionCode = 7),
            SingleLang("West Manga", "https://westmanga.info", "id", overrideVersionCode = 1),
            SingleLang("MangaSwat", "https://mangaswat.com", "ar"),
            SingleLang("Manga Raw.org", "https://mangaraw.org", "ja", className = "MangaRawOrg", overrideVersionCode = 1),
            SingleLang("Manga Pro Z", "https://mangaproz.com", "ar"),
            SingleLang("Silence Scan", "https://silencescan.net", "pt-BR", overrideVersionCode = 2),
            SingleLang("Kuma Scans (Kuma Translation)", "https://kumascans.com", "en", className = "KumaScans", overrideVersionCode = 1),
            SingleLang("Tempest Manga", "https://manga.tempestfansub.com", "tr"),
            SingleLang("xCaliBR Scans", "https://xcalibrscans.com", "en", overrideVersionCode = 2),
            SingleLang("NoxSubs", "https://noxsubs.com", "tr"),
            SingleLang("World Romance Translation", "https://wrt.my.id/", "id", overrideVersionCode = 2),
            SingleLang("The Apollo Team", "https://theapollo.team", "en"),
            SingleLang("Sekte Doujin", "https://sektedoujin.xyz", "id", isNsfw = true),
            SingleLang("Lemon Juice Scan", "https://lemonjuicescan.com", "pt-BR", isNsfw = true),
            SingleLang("Phoenix Fansub", "https://phoenixfansub.com", "es")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPMangaStreamGenerator().createAll()
        }
    }
}
