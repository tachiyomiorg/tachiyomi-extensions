package eu.kanade.tachiyomi.extension.all.nyahentaitwocom

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiTwoComFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiTwoComEN(),
        NyaHentaiTwoComJA(),
        NyaHentaiTwoComZH(),
        NyaHentaiTwoComALL(),
    )
}
class NyaHentaiTwoComEN : NyaHentai("NyaHentai2.com", "https://nyahentai2.com", "en")
class NyaHentaiTwoComJA : NyaHentai("NyaHentai2.com", "https://nyahentai2.com", "ja")
class NyaHentaiTwoComZH : NyaHentai("NyaHentai2.com", "https://nyahentai2.com", "zh")
class NyaHentaiTwoComALL : NyaHentai("NyaHentai2.com", "https://nyahentai2.com", "all")
