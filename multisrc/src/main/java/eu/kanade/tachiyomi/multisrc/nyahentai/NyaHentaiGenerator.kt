package eu.kanade.tachiyomi.multisrc.nyahentai

import generator.ThemeSourceData.MultiLang
import generator.ThemeSourceGenerator

class NyaHentaiGenerator : ThemeSourceGenerator {

    override val themePkg = "nyahentai"

    override val themeClass = "NyaHentai"

    override val baseVersionCode: Int = 1

    override val sources = listOf(
        MultiLang("NyaHentai", "https://nyahentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiFactory", overrideVersionCode = 3),
        MultiLang("NyaHentai.site", "https://nyahentai.site", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiSiteFactory", pkgName = "nyahentaisite"),
        MultiLang("NyaHentai.me", "https://ja.nyahentai.me", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiMeFactory", pkgName = "nyahentaime"),
        MultiLang("QQHentai", "https://zhb.qqhentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "QQHentaiFactory"),
        MultiLang("FoxHentai", "https://ja.foxhentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "FoxHentaiFactory"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            NyaHentaiGenerator().createAll()
        }
    }
}
