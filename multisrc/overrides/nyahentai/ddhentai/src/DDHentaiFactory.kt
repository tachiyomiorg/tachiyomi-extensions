package eu.kanade.tachiyomi.extension.all.ddhentai

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DDHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        DDHentaiEN(),
        DDHentaiJA(),
        DDHentaiZH(),
        DDHentaiALL(),
    )
}
class DDHentaiEN : NyaHentai("DDHentai", "https://zh.ddhentai.com", "en")
class DDHentaiJA : NyaHentai("DDHentai", "https://zh.ddhentai.com", "ja")
class DDHentaiZH : NyaHentai("DDHentai", "https://zh.ddhentai.com", "zh")
class DDHentaiALL : NyaHentai("DDHentai", "https://zh.ddhentai.com", "all")
