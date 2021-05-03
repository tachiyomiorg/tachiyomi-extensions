package eu.kanade.tachiyomi.extension.all.bughentaien

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class BugHentaiEnFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        BugHentaiEnEN(),
        BugHentaiEnJA(),
        BugHentaiEnZH(),
        BugHentaiEnALL(),
    )
}
class BugHentaiEnEN : NyaHentai("BugHentai (en)", "https://en.bughentai.com", "en")
class BugHentaiEnJA : NyaHentai("BugHentai (en)", "https://en.bughentai.com", "ja")
class BugHentaiEnZH : NyaHentai("BugHentai (en)", "https://en.bughentai.com", "zh")
class BugHentaiEnALL : NyaHentai("BugHentai (en)", "https://en.bughentai.com", "all")
