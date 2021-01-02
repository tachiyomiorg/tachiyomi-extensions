package eu.kanade.tachiyomi.multisrc.genkan

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.ThemeSourceData

class GenkanOriginalGenerator : ThemeSourceGenerator {

    override val themePkg = "genkan"

    override val themeClass = "GenkanOriginal"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        ThemeSourceData.SingleLang("Reaper Scans", "https://reaperscans.com", "en"),
        ThemeSourceData.SingleLang("Hatigarm Scans", "https://hatigarmscanz.net", "en"),
        ThemeSourceData.SingleLang("SecretScans", "https://secretscans.co", "en"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            GenkanOriginalGenerator().createAll()
        }
    }
}
