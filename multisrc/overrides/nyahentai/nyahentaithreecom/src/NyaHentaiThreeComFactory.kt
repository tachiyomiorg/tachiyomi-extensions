package eu.kanade.tachiyomi.extension.all.nyahentaithreecom

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class NyaHentaiThreeComFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        NyaHentaiThreeComEN(),
        NyaHentaiThreeComJA(),
        NyaHentaiThreeComZH(),
        NyaHentaiThreeComALL(),
    )
}
class NyaHentaiThreeComEN : NyaHentai("NyaHentai3.com", "https://nyahentai3.com", "en")
class NyaHentaiThreeComJA : NyaHentai("NyaHentai3.com", "https://nyahentai3.com", "ja")
class NyaHentaiThreeComZH : NyaHentai("NyaHentai3.com", "https://nyahentai3.com", "zh")
class NyaHentaiThreeComALL : NyaHentai("NyaHentai3.com", "https://nyahentai3.com", "all")
