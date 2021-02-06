package eu.kanade.tachiyomi.extension.all.hentaihand

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@Nsfw
class HentaiHandFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        HentaiHand("en", 1),
        HentaiHand("zh", 2),
        HentaiHand("ja", 3)
    )
}
