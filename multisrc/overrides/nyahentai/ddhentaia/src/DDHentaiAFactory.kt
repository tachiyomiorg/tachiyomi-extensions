package eu.kanade.tachiyomi.extension.all.ddhentaia

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DDHentaiAFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        DDHentaiAEN(),
        DDHentaiAJA(),
        DDHentaiAZH(),
        DDHentaiAALL(),
    )
}
class DDHentaiAEN : NyaHentai("DDHentai A", "https://zha.ddhentai.com", "en")
class DDHentaiAJA : NyaHentai("DDHentai A", "https://zha.ddhentai.com", "ja")
class DDHentaiAZH : NyaHentai("DDHentai A", "https://zha.ddhentai.com", "zh")
class DDHentaiAALL : NyaHentai("DDHentai A", "https://zha.ddhentai.com", "all")
