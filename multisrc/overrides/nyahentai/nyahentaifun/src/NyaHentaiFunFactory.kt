package eu.kanade.tachiyomi.extension.all.nyahentaifun

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiFunFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiFunEN(),
        NyaHentaiFunJA(),
        NyaHentaiFunZH(),
        NyaHentaiFunALL(),
    )
}
class NyaHentaiFunEN : NyaHentai("NyaHentai.fun", "https://nyahentai.fun", "en")
class NyaHentaiFunJA : NyaHentai("NyaHentai.fun", "https://nyahentai.fun", "ja")
class NyaHentaiFunZH : NyaHentai("NyaHentai.fun", "https://nyahentai.fun", "zh")
class NyaHentaiFunALL : NyaHentai("NyaHentai.fun", "https://nyahentai.fun", "all")
