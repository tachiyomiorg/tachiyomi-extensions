package eu.kanade.tachiyomi.extension.all.webtoons

import eu.kanade.tachiyomi.extension.en.webtoons.WebtoonsEnglish
import eu.kanade.tachiyomi.extension.fr.webtoons.WebtoonsFrench
import eu.kanade.tachiyomi.extension.id.webtoons.WebtoonsIndonesian
import eu.kanade.tachiyomi.extension.th.webtoons.WebtoonsThai
import eu.kanade.tachiyomi.extension.zh.webtoons.WebtoonsChineseTraditional
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class WebtoonsFactory : SourceFactory {
    override fun createSources(): List<Source> = getAllWebtoons()
}

fun getAllWebtoons(): List<Source> {
    return listOf(
            WebtoonsEnglish(),
            WebtoonsChineseTraditional(),
            WebtoonsFrench(),
            WebtoonsIndonesian(),
            WebtoonsThai()
    )
}
