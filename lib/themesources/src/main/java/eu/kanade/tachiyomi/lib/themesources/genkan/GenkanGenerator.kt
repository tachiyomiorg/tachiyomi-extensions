package eu.kanade.tachiyomi.lib.themesources.genkan

import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.SingleLangThemeSourceData
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.MultiLangThemeSourceData

class GenkanGenerator : ThemeSourceGenerator {

    override val themeName = "Genkan"

    override val sources = listOf(
//        MultiLangThemeSourceData("Leviatan Scans", "https://leviatanscans.com", listOf("en","es"), className="LeviatanScansFactory", pkgName="leviatanscans"),
//        SingleLangThemeSourceData("Hunlight Scans", "https://hunlight-scans.info", "en"),
        SingleLangThemeSourceData("ZeroScans", "https://zeroscans.com", "en"),
        SingleLangThemeSourceData("The Nonames Scans", "https://the-nonames.com", "en"),
//        SingleLangThemeSourceData("Edelgarde Scans", "https://edelgardescans.com", "en"),
//        SingleLangThemeSourceData("Method Scans", "https://methodscans.com", "en"),
//        SingleLangThemeSourceData("Sleeping Knight Scans", "https://skscans.com", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanGenerator().createOrUpdateAll()
        }
    }
}
