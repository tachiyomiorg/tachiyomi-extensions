package eu.kanade.tachiyomi.multisrc.wpmangareader

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class WPMangaReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "wpmangareader"

    override val themeClass = "WPMangaReader"

    override val baseVersionCode: Int = 4

    override val sources = listOf(
            SingleLang("Hikari Scan", "https://hikariscan.com.br", "pt-BR", overrideVersionCode = 1),
            SingleLang("KomikMama", "https://komikmama.net", "id"),
            SingleLang("MangaKita", "https://mangakita.net", "id"),
            SingleLang("Ngomik", "https://ngomik.net", "id"),
            SingleLang("Sekaikomik", "https://www.sekaikomik.net", "id", isNsfw = true, overrideVersionCode = 2),
            SingleLang("TurkToon", "https://turktoon.com", "tr"),
            SingleLang("Gecenin Lordu", "https://geceninlordu.com/", "tr", overrideVersionCode = 1),
            SingleLang("Flame Scans", "http://flamescans.org", "en", overrideVersionCode = 1),
            SingleLang("PMScans", "https://reader.pmscans.com", "en"),
        )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPMangaReaderGenerator().createAll()
        }
    }
}
