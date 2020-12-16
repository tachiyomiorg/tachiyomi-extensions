package eu.kanade.tachiyomi.lib.themesources.genkan

import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.ThemeSourceData

class GenkanGenerator : ThemeSourceGenerator {

    override val themeName = "Genkan"

    override val sources = listOf(
        ThemeSourceData("Leviatan Scans", "https://leviatanscans.com", "en"),
        ThemeSourceData("Hunlight Scans", "https://hunlight-scans.info", "en"),
        ThemeSourceData("ZeroScans", "https://zeroscans.com", "en"),
        ThemeSourceData("The Nonames Scans", "https://the-nonames.com", "en"),
        ThemeSourceData("Edelgarde Scans", "https://edelgardescans.com", "en"),
        ThemeSourceData("Method Scans", "https://methodscans.com", "en"),
        ThemeSourceData("Sleeping Knight Scans", "https://skscans.com", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanGenerator().createOrUpdateAll()
        }
    }
}
