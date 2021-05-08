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
        MultiLang("NyaHentai.fun", "https://nyahentai.fun", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiFunFactory", pkgName = "nyahentaifun"),
        MultiLang("NyaHentai.club", "https://nyahentai.club", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiClubFactory", pkgName = "nyahentaiclub"),
        MultiLang("NyaHentai2.com", "https://nyahentai2.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiTwoComFactory", pkgName = "nyahentaitwocom"),
        MultiLang("NyaHentai.co", "https://nyahentai.co", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiCoFactory", pkgName = "nyahentaico"),
        MultiLang("NyaHentai3.com", "https://nyahentai3.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "NyaHentaiThreeComFactory", pkgName = "nyahentaithreecom"),
        MultiLang("CatHentai", "https://cathentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "CatHentaiFactory"),
        MultiLang("DogHentai", "https://zhb.doghentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "DogHentaiFactory"),
        MultiLang("BugHentai (ja)", "https://ja.bughentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "BugHentaiJaFactory", pkgName = "bughentaija"),
        MultiLang("BugHentai (en)", "https://en.bughentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "BugHentaiEnFactory", pkgName = "bughentaien"),
        MultiLang("QQHentai", "https://zhb.qqhentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "QQHentaiFactory"),
        MultiLang("FoxHentai", "https://ja.foxhentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "FoxHentaiFactory"),
        MultiLang("DDHentai", "https://zh.ddhentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "DDHentaiFactory"),
        MultiLang("DDHentai A", "https://zha.ddhentai.com", listOf("en","ja", "zh", "all"), isNsfw = true, className = "DDHentaiAFactory"),
    )

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            NyaHentaiGenerator().createAll()
        }
    }
}
