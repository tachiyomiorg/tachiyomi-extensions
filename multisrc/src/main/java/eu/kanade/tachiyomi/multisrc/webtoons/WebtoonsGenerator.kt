package eu.kanade.tachiyomi.multisrc.webtoons

import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceGenerator

class WebtoonsGenerator : ThemeSourceGenerator {

    override val themePkg = "webtoons"

    override val themeClass = "Webtoons"

    override val baseVersionCode: Int = 28

    override val sources = listOf(
        MultiLang("Webtoons", "https://www.webtoons.com", listOf("en", "fr", "es", "id", "th", "zh"), className = "WebtoonsFactory"),
        SingleLang("Dongman Manhua", "https://www.dongmanmanhua.cn", "zh")
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            WebtoonsGenerator().createAll()
        }
    }
}
