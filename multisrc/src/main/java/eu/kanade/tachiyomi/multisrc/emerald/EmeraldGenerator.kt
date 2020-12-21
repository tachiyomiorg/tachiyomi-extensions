package eu.kanade.tachiyomi.multisrc.emerald

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.MultiLangThemeSourceData

class EmeraldGenerator : ThemeSourceGenerator {

    override val themePkg = "emerald"

    override val themeClass = "Emerald"

    override val baseVersionCode: Int = 1

    private val languages = Emerald.languages.map { it.first }

    override val sources = listOf(
        MultiLangThemeSourceData("Mangawindow", "https://mangawindow.net", languages),
        MultiLangThemeSourceData("Bato.to", "https://bato.to", languages, className ="BatoToFactory", pkgName = "batoto" ),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            EmeraldGenerator().createAll()
        }
    }
}

