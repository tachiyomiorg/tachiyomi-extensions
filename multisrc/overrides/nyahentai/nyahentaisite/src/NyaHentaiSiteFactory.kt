package eu.kanade.tachiyomi.extension.all.nyahentaisite

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiSiteFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiSiteEN(),
        NyaHentaiSiteJA(),
        NyaHentaiSiteZH(),
        NyaHentaiSiteALL(),
    )
}
class NyaHentaiSiteEN : NyaHentai("NyaHentai.site", "https://nyahentai.site", "en")
class NyaHentaiSiteJA : NyaHentai("NyaHentai.site", "https://nyahentai.site", "ja")
class NyaHentaiSiteZH : NyaHentai("NyaHentai.site", "https://nyahentai.site", "zh")
class NyaHentaiSiteALL : NyaHentai("NyaHentai.site", "https://nyahentai.site", "all")
