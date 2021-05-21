package eu.kanade.tachiyomi.multisrc.wpmangastream

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class WPMangaStreamGenerator : ThemeSourceGenerator {

    override val themePkg = "wpmangastream"

    override val themeClass = "WPMangaStream"

    override val baseVersionCode: Int = 4

    override val sources = listOf(
            SingleLang("Asura Scans", "https://asurascans.com", "en"),
            SingleLang("KlanKomik", "https://klankomik.com", "id"),
            SingleLang("ChiOtaku", "https://chiotaku.com", "id"),
            SingleLang("MangaShiro", "https://mangashiro.co", "id"),
            SingleLang("MasterKomik", "https://masterkomik.com", "id"),
            SingleLang("Kaisar Komik", "https://kaisarkomik.com", "id"),
            SingleLang("Rawkuma", "https://rawkuma.com/", "ja"),
            SingleLang("MangaP", "https://mangap.me", "ar"),
            SingleLang("Boosei", "https://boosei.com", "id"),
            SingleLang("Mangakyo", "https://www.mangakyo.me", "id"),
            SingleLang("Sekte Komik", "https://sektekomik.com", "id"),
            SingleLang("Komik Station", "https://komikstation.com", "id"),
            SingleLang("Komik Indo", "https://www.komikindo.web.id", "id", className = "KomikIndoWPMS"),
            SingleLang("Non-Stop Scans", "https://www.nonstopscans.com", "en", className = "NonStopScans"),
            SingleLang("KomikIndo.co", "https://komikindo.co", "id", className = "KomikindoCo"),
            SingleLang("Readkomik", "https://readkomik.com", "en", className = "ReadKomik"),
            SingleLang("MangaIndonesia", "https://mangaindonesia.net", "id"),
            SingleLang("Liebe Schnee Hiver", "https://www.liebeschneehiver.com", "tr"),
            SingleLang("KomikRu", "https://komikru.com", "id"),
            SingleLang("GURU Komik", "https://gurukomik.com", "id"),
            SingleLang("Shea Manga", "https://sheamanga.my.id", "id"),
            SingleLang("Kiryuu", "https://kiryuu.co", "id"),
            SingleLang("Komik AV", "https://komikav.com", "id"),
            SingleLang("Komik Cast", "https://komikcast.com", "id", overrideVersionCode = 3), // make it from v0 to v3 to force update user who still use old standalone ext, they will need to migrate
            SingleLang("West Manga", "https://westmanga.info", "id"),
            SingleLang("Komik GO", "https://komikgo.com", "id", overrideVersionCode = 1),
            SingleLang("MangaSwat", "https://mangaswat.com", "ar"),
            SingleLang("Manga Raw.org", "https://mangaraw.org", "ja", className = "MangaRawOrg", overrideVersionCode = 1),
            SingleLang("Matakomik", "https://matakomik.com", "id"),
            SingleLang("Manga Pro Z", "https://mangaproz.com", "ar"),
            SingleLang("Silence Scan", "https://silencescan.net", "pt-BR", overrideVersionCode = 1),
            SingleLang("Kuma Scans (Kuma Translation)", "https://kumascans.com", "en", className = "KumaScans"),
            SingleLang("Tempest Manga", "https://manga.tempestfansub.com", "tr"),
            SingleLang("xCaliBR Scans", "https://xcalibrscans.com", "en", overrideVersionCode = 2),
            SingleLang("NoxSubs", "https://noxsubs.com", "tr"),
            SingleLang("World Romance Translation", "https://wrt.my.id/", "id", overrideVersionCode = 1),
            SingleLang("The Apollo Team", "https://theapollo.team", "en"),
            SingleLang("Sekte Doujin", "https://sektedoujin.xyz", "id", isNsfw = true),
            SingleLang("Lemon Juice Scan", "https://lemonjuicescan.com", "pt-BR", isNsfw = true)
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPMangaStreamGenerator().createAll()
        }
    }
}
