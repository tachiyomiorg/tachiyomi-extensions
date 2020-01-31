package eu.kanade.tachiyomi.extension.all.ehentai

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class EHFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        EHentai("ja", "japanese"),
        EHentai("en", "english"),
        EHentai("zh", "chinese"),
        EHentai("nl", "dutch"),
        EHentai("fr", "french"),
        EHentai("de", "german"),
        EHentai("hu", "hungarian"),
        EHentai("it", "italian"),
        EHentai("ko", "korean"),
        EHentai("pl", "polish"),
        EHentai("pt", "portuguese"),
        EHentai("ru", "russian"),
        EHentai("es", "spanish"),
        EHentai("th", "thai"),
        EHentai("vi", "vietnamese"),
        EHentai("none", "n/a"),
        EHentai("other", "other")
    )
}
