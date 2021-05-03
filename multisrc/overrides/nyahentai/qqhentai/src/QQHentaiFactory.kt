package eu.kanade.tachiyomi.extension.all.qqhentai

import eu.kanade.tachiyomi.multisrc.nyahentai.NyaHentai
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class QQHentaiFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        QQHentaiEN(),
        QQHentaiJA(),
        QQHentaiZH(),
        QQHentaiALL(),
    )
}
class QQHentaiEN : NyaHentai("QQHentai", "https://zhb.qqhentai.com", "en")
class QQHentaiJA : NyaHentai("QQHentai", "https://zhb.qqhentai.com", "ja")
class QQHentaiZH : NyaHentai("QQHentai", "https://zhb.qqhentai.com", "zh")
class QQHentaiALL : NyaHentai("QQHentai", "https://zhb.qqhentai.com", "all")
