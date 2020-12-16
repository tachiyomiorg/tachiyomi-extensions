package eu.kanade.tachiyomi.lib.themesources.genkan

import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.ThemeSourceData

class GenkanOriginalGenerator : ThemeSourceGenerator {

    override val themeName = "Genkan"

    override val sources = listOf(
        ThemeSourceData("Leviatan Scans", "https://es.leviatanscans.com", "es", "LeviatanScansES"),
        ThemeSourceData("Reaper Scans", "https://reaperscans.com", "en"),
        ThemeSourceData("Hatigarm Scans", "https://hatigarmscanz.net", "en"),
        ThemeSourceData("SecretScans", "https://secretscans.co", "en"),

    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanOriginalGenerator().createOrUpdateAll()
        }
    }
}
