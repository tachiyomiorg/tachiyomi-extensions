package eu.kanade.tachiyomi.multisrc.bakkin

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class BakkinGenerator : ThemeSourceGenerator {
    override val themePkg = "bakkin"

    override val themeClass = "BakkinReaderX"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        SingleLang("Bakkin", "https://bakkin.moe/reader/", "en"),
        SingleLang("Bakkin Self-hosted", "", "en", className = "BakkinSelfHosted")
    )

    companion object {
        @JvmStatic fun main(args: Array<String>) = BakkinGenerator().createAll()
    }
}
