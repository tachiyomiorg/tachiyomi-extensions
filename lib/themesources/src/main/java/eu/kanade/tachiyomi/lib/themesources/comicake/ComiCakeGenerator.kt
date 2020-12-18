package eu.kanade.tachiyomi.lib.themesources.comicake

import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.SingleLangThemeSourceData
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.MultiLangThemeSourceData

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
