package eu.kanade.tachiyomi.extension.all.nyahentaiclub

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiClubFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiClubEN(),
        NyaHentaiClubJA(),
        NyaHentaiClubZH(),
        NyaHentaiClubALL(),
    )
}
class NyaHentaiClubEN : NyaHentai("NyaHentai.club", "https://nyahentai.club", "en")
class NyaHentaiClubJA : NyaHentai("NyaHentai.club", "https://nyahentai.club", "ja")
class NyaHentaiClubZH : NyaHentai("NyaHentai.club", "https://nyahentai.club", "zh")
class NyaHentaiClubALL : NyaHentai("NyaHentai.club", "https://nyahentai.club", "all")
