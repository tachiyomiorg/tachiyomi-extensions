package eu.kanade.tachiyomi.extension.all.nyahentai

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiEN(),
        NyaHentaiJA(),
        NyaHentaiZH(),
        NyaHentaiALL(),
    )
}
class NyaHentaiEN : NyaHentai("NyaHentai", "https://nyahentai.com", "en"){
    override val id = 9170089554867447899
}
class NyaHentaiJA : NyaHentai("NyaHentai", "https://nyahentai.com", "ja")
class NyaHentaiZH : NyaHentai("NyaHentai", "https://nyahentai.com", "zh")
class NyaHentaiALL : NyaHentai("NyaHentai", "https://nyahentai.com", "all")
