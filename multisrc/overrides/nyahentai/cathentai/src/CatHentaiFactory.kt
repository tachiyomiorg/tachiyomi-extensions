package eu.kanade.tachiyomi.extension.all.cathentai

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class CatHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        CatHentaiEN(),
        CatHentaiJA(),
        CatHentaiZH(),
        CatHentaiALL(),
    )
}
class CatHentaiEN : NyaHentai("CatHentai", "https://cathentai.com", "en")
class CatHentaiJA : NyaHentai("CatHentai", "https://cathentai.com", "ja")
class CatHentaiZH : NyaHentai("CatHentai", "https://cathentai.com", "zh")
class CatHentaiALL : NyaHentai("CatHentai", "https://cathentai.com", "all")
