package eu.kanade.tachiyomi.multisrc.genkan

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.SingleLangThemeSourceData
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.MultiLangThemeSourceData

class GenkanGenerator : ThemeSourceGenerator {

    override val themePkg = "genkan"

    override val themeClass = "Genkan"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        MultiLangThemeSourceData("Leviatan Scans", "https://leviatanscans.com", listOf("en", "es"),
            className = "LeviatanScansFactory", pkgName = "leviatanscans", overrideVersionCode = 1),
        SingleLangThemeSourceData("Hunlight Scans", "https://hunlight-scans.info", "en"),
        SingleLangThemeSourceData("ZeroScans", "https://zeroscans.com", "en"),
        SingleLangThemeSourceData("The Nonames Scans", "https://the-nonames.com", "en"),
        SingleLangThemeSourceData("Edelgarde Scans", "https://edelgardescans.com", "en"),
        SingleLangThemeSourceData("Method Scans", "https://methodscans.com", "en"),
        SingleLangThemeSourceData("Sleeping Knight Scans", "https://skscans.com", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanGenerator().createAll()
        }
    }
}
