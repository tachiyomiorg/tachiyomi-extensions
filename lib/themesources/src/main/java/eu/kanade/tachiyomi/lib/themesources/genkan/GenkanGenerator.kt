package eu.kanade.tachiyomi.lib.themesources.genkan

import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.ThemeSourceData

class GenkanGenerator : ThemeSourceGenerator {

    override val themeName = "Genkan"

    override val sources = listOf(
        ThemeSourceData("Leviatan Scans", "https://leviatanscans.com", "en"),
        ThemeSourceData("Leviatan Scans", "https://es.leviatanscans.com", "es", "LeviatanScansES"),
        ThemeSourceData("Hunlight Scans", "https://hunlight-scans.info", "en"),
        ThemeSourceData("ZeroScans", "https://zeroscans.com", "en"),
        ThemeSourceData("Reaper Scans", "https://reaperscans.com", "en"),
        ThemeSourceData("The Nonames Scans", "https://the-nonames.com", "en"),
        ThemeSourceData("Hatigarm Scans", "https://hatigarmscanz.net", "en"),
        ThemeSourceData("Edelgarde Scans", "https://edelgardescans.com", "en"),
        ThemeSourceData("SecretScans", "https://secretscans.co", "en"),
        ThemeSourceData("Method Scans", "https://methodscans.com", "en"),
        ThemeSourceData("Sleeping Knight Scans", "https://skscans.com", "en"),
        ThemeSourceData("KKJ Scans", "https://kkjscans.co", "en"),
        ThemeSourceData("Kraken Scans", "https://krakenscans.com", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanGenerator().createOrUpdateAll()
        }
    }
}
