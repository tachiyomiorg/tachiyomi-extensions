package eu.kanade.tachiyomi.multisrc.wpmangareader

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class WPMangaReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "wpmangareader"

    override val themeClass = "WPMangaReader"

    override val baseVersionCode: Int = 2

    override val sources = listOf(
            SingleLang("Hikari Scan", "https://hikariscan.com.br", "pt-BR", overrideVersionCode = 1),
            SingleLang("KomikMama", "https://komikmama.net", "id"),
            SingleLang("MangaKita", "https://mangakita.net", "id"),
            SingleLang("Ngomik", "https://ngomik.net", "id"),
            SingleLang("Sekaikomik", "https://www.sekaikomik.com", "id", true),
            SingleLang("TurkToon", "https://turktoon.com", "tr"),
            SingleLang("Gecenin Lordu", "https://geceninlordu.com/", "tr", overrideVersionCode = 1),
        )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPMangaReaderGenerator().createAll()
        }
    }
}
