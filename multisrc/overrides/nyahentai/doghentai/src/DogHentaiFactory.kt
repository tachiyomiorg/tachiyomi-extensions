package eu.kanade.tachiyomi.extension.all.doghentai

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class DogHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        DogHentaiEN(),
        DogHentaiJA(),
        DogHentaiZH(),
        DogHentaiALL(),
    )
}
class DogHentaiEN : NyaHentai("DogHentai", "https://zhb.doghentai.com", "en")
class DogHentaiJA : NyaHentai("DogHentai", "https://zhb.doghentai.com", "ja")
class DogHentaiZH : NyaHentai("DogHentai", "https://zhb.doghentai.com", "zh")
class DogHentaiALL : NyaHentai("DogHentai", "https://zhb.doghentai.com", "all")
