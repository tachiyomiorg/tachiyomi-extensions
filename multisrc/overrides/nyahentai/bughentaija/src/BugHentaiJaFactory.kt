package eu.kanade.tachiyomi.extension.all.bughentaija

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class BugHentaiJaFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        BugHentaiJaEN(),
        BugHentaiJaJA(),
        BugHentaiJaZH(),
        BugHentaiJaALL(),
    )
}
class BugHentaiJaEN : NyaHentai("BugHentai (ja)", "https://ja.bughentai.com", "en")
class BugHentaiJaJA : NyaHentai("BugHentai (ja)", "https://ja.bughentai.com", "ja")
class BugHentaiJaZH : NyaHentai("BugHentai (ja)", "https://ja.bughentai.com", "zh")
class BugHentaiJaALL : NyaHentai("BugHentai (ja)", "https://ja.bughentai.com", "all")
