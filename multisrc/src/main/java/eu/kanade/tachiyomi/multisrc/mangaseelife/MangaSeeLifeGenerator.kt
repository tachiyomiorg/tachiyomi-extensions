package eu.kanade.tachiyomi.multisrc.mangaseelife

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MangaSeeLifeGenerator : ThemeSourceGenerator {

    override val themePkg = "mangaseelife"

    override val themeClass = "MangaSeeLife"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("MangaSee", "https://mangasee123.com", "en", overrideVersionCode = 20),
        SingleLang("MangaLife", "https://manga4life.com", "en", overrideVersionCode = 16),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            MangaSeeLifeGenerator().createAll()
        }
    }
}
