package eu.kanade.tachiyomi.multisrc.comicake

import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator
import eu.kanade.tachiyomi.multisrc.ThemeSourceGenerator.Companion.SingleLangThemeSourceData

class ComiCakeGenerator : ThemeSourceGenerator {

    override val themePkg = "comicake"

    override val themeClass = "ComiCake"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLangThemeSourceData("LetItGo Scans", "https://reader.letitgo.scans.today", "en"),
        SingleLangThemeSourceData("ProjectTime Scans", "https://read.ptscans.com", "en"),
        SingleLangThemeSourceData("WhimSubs", "https://whimsubs.xyz", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ComiCakeGenerator().createAll()
        }
    }
}
