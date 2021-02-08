package eu.kanade.tachiyomi.multisrc.genkan

import eu.kanade.tachiyomi.multisrc.ThemeSourceData.SingleLang
import eu.kanade.tachiyomi.multisrc.ThemeSourceData.MultiLang
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator

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
        SingleLang("Sleeping Knight Scans", "https://skscans.com", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanGenerator().createAll()
        }
    }
}
