package eu.kanade.tachiyomi.multisrc.genkan

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.ThemeSourceData

class GenkanGenerator : ThemeSourceGenerator {

    override val themePkg = "genkan"

    override val themeClass = "Genkan"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        ThemeSourceData.MultiLang("Leviatan Scans", "https://leviatanscans.com", listOf("en", "es"),
            className = "LeviatanScansFactory", pkgName = "leviatanscans", overrideVersionCode = 1),
        ThemeSourceGenerator.Companion.ThemeSourceData.SingleLang("Hunlight Scans", "https://hunlight-scans.info", "en"),
        ThemeSourceData.SingleLang("ZeroScans", "https://zeroscans.com", "en"),
        ThemeSourceData.SingleLang("The Nonames Scans", "https://the-nonames.com", "en"),
        ThemeSourceData.SingleLang("Edelgarde Scans", "https://edelgardescans.com", "en"),
        ThemeSourceData.SingleLang("Method Scans", "https://methodscans.com", "en"),
        ThemeSourceData.SingleLang("Sleeping Knight Scans", "https://skscans.com", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanGenerator().createAll()
        }
    }
}
