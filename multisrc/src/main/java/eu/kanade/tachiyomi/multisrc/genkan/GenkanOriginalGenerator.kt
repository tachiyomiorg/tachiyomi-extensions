package eu.kanade.tachiyomi.multisrc.genkan

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class GenkanOriginalGenerator : ThemeSourceGenerator {

    override val themePkg = "genkan"

    override val themeClass = "GenkanOriginal"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Reaper Scans", "https://reaperscans.com", "en"),
        SingleLang("Hatigarm Scans", "https://hatigarmscanz.net", "en", overrideVersionCode = 1),
        SingleLang("Method Scans", "https://methodscans.com", "en", overrideVersionCode = 1)
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanOriginalGenerator().createAll()
        }
    }
}
