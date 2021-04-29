package eu.kanade.tachiyomi.extension.all.nyahentaime

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiMeFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiMeEN(),
        NyaHentaiMeJA(),
        NyaHentaiMeZH(),
        NyaHentaiMeALL(),
    )
}
class NyaHentaiMeEN : NyaHentai("NyaHentai.me", "https://ja.nyahentai.me", "en")
class NyaHentaiMeJA : NyaHentai("NyaHentai.me", "https://ja.nyahentai.me", "ja")
class NyaHentaiMeZH : NyaHentai("NyaHentai.me", "https://ja.nyahentai.me", "zh")
class NyaHentaiMeALL : NyaHentai("NyaHentai.me", "https://ja.nyahentai.me", "all")
