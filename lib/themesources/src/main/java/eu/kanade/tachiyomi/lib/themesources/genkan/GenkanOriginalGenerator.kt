package eu.kanade.tachiyomi.lib.themesources.genkan

import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.SingleLangThemeSourceData

class GenkanOriginalGenerator : ThemeSourceGenerator {

    override val themeName = "GenkanOriginal"

    override val sources = listOf(
        SingleLangThemeSourceData("Reaper Scans", "https://reaperscans.com", "en"),
        SingleLangThemeSourceData("Hatigarm Scans", "https://hatigarmscanz.net", "en"),
        SingleLangThemeSourceData("SecretScans", "https://secretscans.co", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanOriginalGenerator().createOrUpdateAll()
        }
    }
}
