package eu.kanade.tachiyomi.extension.all.foxhentai

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class FoxHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        FoxHentaiEN(),
        FoxHentaiJA(),
        FoxHentaiZH(),
        FoxHentaiALL(),
    )
}
class FoxHentaiEN : NyaHentai("FoxHentai", "https://ja.foxhentai.com", "en")
class FoxHentaiJA : NyaHentai("FoxHentai", "https://ja.foxhentai.com", "ja")
class FoxHentaiZH : NyaHentai("FoxHentai", "https://ja.foxhentai.com", "zh")
class FoxHentaiALL : NyaHentai("FoxHentai", "https://ja.foxhentai.com", "all")
