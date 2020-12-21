package eu.kanade.tachiyomi.multisrc.genkan

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.SingleLangThemeSourceData

class GenkanOriginalGenerator : ThemeSourceGenerator {

    override val themePkg = "genkan"

    override val themeClass = "GenkanOriginal"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLangThemeSourceData("Reaper Scans", "https://reaperscans.com", "en"),
        SingleLangThemeSourceData("Hatigarm Scans", "https://hatigarmscanz.net", "en"),
        SingleLangThemeSourceData("SecretScans", "https://secretscans.co", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanOriginalGenerator().createAll()
        }
    }
}
