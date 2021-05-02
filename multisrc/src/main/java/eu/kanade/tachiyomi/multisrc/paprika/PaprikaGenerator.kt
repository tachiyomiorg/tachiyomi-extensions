package eu.kanade.tachiyomi.multisrc.paprika

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class PaprikaGenerator : ThemeSourceGenerator {

    override val themePkg = "paprika"

    override val themeClass = "Paprika"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
            SingleLang("MangaStream.xyz", "http://mangastream.xyz", "en", className = "MangaStreamXYZ"),
            SingleLang("ReadMangaFox", "http://readmangafox.xyz", "en"),
//            SingleLang("MangaZuki.xyz", "http://mangazuki.xyz", "en", className = "MangaZuki"),
//            SingleLang("MangaTensei", "http://www.mangatensei.com", "en"),
            SingleLang("MangaNelos.com", "http://manganelos.com", "en", className = "MangaNelosCom"),
            SingleLang("MangaDogs.fun", "http://mangadogs.fun", "en", className = "MangaDogsFun"),
            SingleLang("MangaHere.today", "http://mangahere.today", "en", className = "MangaHereToday"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            PaprikaGenerator().createAll()
        }
    }
}
