package eu.kanade.tachiyomi.multisrc.genkan

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class GenkanGenerator : ThemeSourceGenerator {

    override val themePkg = "genkan"

    override val themeClass = "Genkan"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        MultiLang("Leviatan Scans", "https://leviatanscans.com", listOf("en", "es"),
            className = "LeviatanScansFactory", pkgName = "leviatanscans", overrideVersionCode = 1),
        SingleLang("Hunlight Scans", "https://hunlight-scans.info", "en"),
        SingleLang("ZeroScans", "https://zeroscans.com", "en"),
        SingleLang("The Nonames Scans", "https://the-nonames.com", "en"),
        SingleLang("Edelgarde Scans", "https://edelgardescans.com", "en"),
        SingleLang("Method Scans", "https://methodscans.com", "en"),
        SingleLang("Sleeping Knight Scans", "https://skscans.com", "en"),
        SingleLang("LynxScans", "https://lynxscans.com", "en", overrideVersionCode = 1),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanGenerator().createAll()
        }
    }
}
