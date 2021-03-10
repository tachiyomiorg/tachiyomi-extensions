package eu.kanade.tachiyomi.multisrc.mangabox

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaBoxGenerator : ThemeSourceGenerator {

    override val themePkg = "mangabox"

    override val themeClass = "MangaBox"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Mangakakalot", "https://mangakakalot.com", "en"),
        SingleLang("Manganelo", "https://manganelo.com", "en"),
        SingleLang("Mangabat", "https://m.mangabat.com/h", "en", overrideVersionCode = 1),
        SingleLang("Mangakakalots (unoriginal)", "https://mangakakalots.com", "en", className = "Mangakakalots", pkgName = "mangakakalots"),
        SingleLang("Mangairo", "https://h.mangairo.com/", "en", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaBoxGenerator().createAll()
        }
    }
}
