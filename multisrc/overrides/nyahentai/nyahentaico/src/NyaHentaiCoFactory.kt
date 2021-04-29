package eu.kanade.tachiyomi.extension.all.nyahentaico

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiCoFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiCoEN(),
        NyaHentaiCoJA(),
        NyaHentaiCoZH(),
        NyaHentaiCoALL(),
    )
}
class NyaHentaiCoEN : NyaHentai("NyaHentai.co", "https://nyahentai.co", "en")
class NyaHentaiCoJA : NyaHentai("NyaHentai.co", "https://nyahentai.co", "ja")
class NyaHentaiCoZH : NyaHentai("NyaHentai.co", "https://nyahentai.co", "zh")
class NyaHentaiCoALL : NyaHentai("NyaHentai.co", "https://nyahentai.co", "all")
