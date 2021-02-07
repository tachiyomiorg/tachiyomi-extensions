package eu.kanade.tachiyomi.multisrc.mangabox

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.SingleLangThemeSourceData
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.MultiLangThemeSourceData
import java.text.SimpleDateFormat
import java.util.*

class MangaBoxGenerator : ThemeSourceGenerator {

    override val themePkg = "mangabox"

    override val themeClass = "MangaBox"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLangThemeSourceData("Mangakakalot", "https://mangakakalot.com", "en"),
        SingleLangThemeSourceData("Manganelo", "https://manganelo.com", "en"),
        SingleLangThemeSourceData("Mangabat", "https://mangabat.com", "en"),
        SingleLangThemeSourceData("Mangakakalots (unoriginal)", "https://mangakakalots.com", "en", className = "Mangakakalots", pkgName = "mangakakalots"),
        SingleLangThemeSourceData("Mangairo", "https://m.mangairo.com", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaBoxGenerator().createAll()
        }
    }
}
