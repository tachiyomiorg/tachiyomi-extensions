package eu.kanade.tachiyomi.multisrc.zbulu

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class ZbuluGenerator : ThemeSourceGenerator {

    override val themePkg = "zbulu"

    override val themeClass = "Zbulu"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("HolyManga", "https://w15.holymanga.net", "en"),
        SingleLang("My Toon", "https://mytoon.net", "en"),
        SingleLang("Koo Manga", "https://ww9.koomanga.com", "en"),
        SingleLang("Bulu Manga", "https://ww8.bulumanga.net", "en")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            ZbuluGenerator().createAll()
        }
    }
}
