package eu.kanade.tachiyomi.multisrc.wpmangareader

import eu.kanade.tachiyomi.multisrc.ThemeSourceData.SingleLang
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator

class WPMangaReaderGenerator : ThemeSourceGenerator {

    override val themePkg = "wpmangareader"

    override val themeClass = "WPMangaReader"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
            SingleLang("KomikMama", "https://komikmama.net", "id"),
            SingleLang("MangaKita", "https://mangakita.net", "id"),
            SingleLang("Ngomik", "https://ngomik.net", "id"),
            SingleLang("TurkToon", "https://turktoon.com", "tr"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WPMangaReaderGenerator().createAll()
        }
    }
}
