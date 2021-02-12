package eu.kanade.tachiyomi.multisrc.wpmangastream

import eu.kanade.tachiyomi.multisrc.ThemeSourceData.SingleLang
import eu.kanade.tachiyomi.multisrc.ThemeSourceData.MultiLang
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator

class WPMangaStreamGenerator : ThemeSourceGenerator {

    override val themePkg = "wpmangastream"

    override val themeClass = "WPMangaStream"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
            SingleLang("Reset Scans", "https://reset-scans.com", "en"),
            SingleLang("KlanKomik", "https://klankomik.com", "id"),
            SingleLang("ChiOtaku", "https://chiotaku.com", "id"),
            SingleLang("MangaShiro", "https://mangashiro.co", "id"),
            SingleLang("MasterKomik", "https://masterkomik.com", "id"),
            SingleLang("Kaisar Komik", "https://kaisarkomik.com", "id"),
            SingleLang("Rawkuma", "https://rawkuma.com/", "ja"),
            SingleLang("Flame Scans", "http://flamescans.org", "en"),
            SingleLang("KomikTap", "https://komiktap.in/", "id"),
            SingleLang("MangaP", "https://mangap.me", "ar"),
            SingleLang("Boosei", "https://boosei.com", "id"),
            SingleLang("Mangakyo", "https://www.mangakyo.me", "id"),
            SingleLang("Sekte Komik (WP Manga Stream)", "https://sektekomik.com", "id", className = "SekteKomikWPMS" ),
            SingleLang("Komik Station (WP Manga Stream)", "https://komikstation.com", "id", className = "KomikStationWPMS"),
            SingleLang("Komik Indo (WP Manga Stream)", "https://www.komikindo.web.id", "id", className = "KomikIndoWPMS"),
            SingleLang("Non-Stop Scans", "https://www.nonstopscans.com", "en", className = "NonStopScans"),
            SingleLang("Komikindo.co", "https://komikindo.co", "id", className = "KomikindoCo"),
            SingleLang("Readkomik", "https://readkomik.com", "en", className = "ReadKomik"),

            SingleLang("MangaIndonesia", "https://mangaindonesia.net", "id"),
            SingleLang("Liebe Schnee Hiver", "https://www.liebeschneehiver.com", "tr"),
            SingleLang("KomikRu", "https://komikru.com", "id"),
            SingleLang("GURU Komik", "https://gurukomik.com", "id"),
            SingleLang("Shea Manga", "https://sheamanga.my.id", "id"),
            SingleLang("Kiryuu (WP Manga Stream)", "https://kiryuu.co", "id", className = "KiryuuWPMS"),
            SingleLang("Komik AV (WP Manga Stream)", "https://komikav.com", "id", className = "KomikAVWPMS"),
            SingleLang("Komik Cast (WP Manga Stream)", "https://komikcast.com", "id", className = "KomikCastWPMS"),
            SingleLang("West Manga (WP Manga Stream)", "https://westmanga.info", "id", className = "WestMangaWPMS"),
            SingleLang("Komik GO (WP Manga Stream)", "https://komikgo.com", "id", className = "KomikGoWPMS"),
            SingleLang("MangaSwat", "https://mangaswat.com", "ar"),
            SingleLang("Manga Raw", "https://mangaraw.org", "ja"),
            SingleLang("Matakomik", "https://matakomik.com", "id"),
            SingleLang("Manga Pro Z", "https://mangaproz.com", "ar"),
            SingleLang("Silence Scan", "https://silencescan.net", "pt-BR"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPMangaStreamGenerator().createAll()
        }
    }
}
