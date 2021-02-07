package eu.kanade.tachiyomi.multisrc.mangabox

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.ThemeSourceData.SingleLang


class MangaBoxGenerator : ThemeSourceGenerator {

    override val themePkg = "mangabox"

    override val themeClass = "MangaBox"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Mangakakalot", "https://mangakakalot.com", "en"),
        SingleLang("Manganelo", "https://manganelo.com", "en"),
        SingleLang("Mangabat", "https://mangabat.com", "en"),
        SingleLang("Mangakakalots (unoriginal)", "https://mangakakalots.com", "en", className = "Mangakakalots", pkgName = "mangakakalots"),
        SingleLang("Mangairo", "https://m.mangairo.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaBoxGenerator().createAll()
        }
    }
}
