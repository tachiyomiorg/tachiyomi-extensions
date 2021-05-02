package eu.kanade.tachiyomi.extension.all.hentaihand

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@Nsfw
class HentaiHandFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        // https://hentaihand.com/api/languages?per_page=50
        HentaiHand("other", extraName = " (Unfiltered)"),
        HentaiHand("en", 1),
        HentaiHand("zh", 2),
        HentaiHand("ja", 3),
        HentaiHand("other", 4, extraName = " (Text Cleaned)"),
        HentaiHand("eo", 5),
        HentaiHand("ceb", 6),
        HentaiHand("cs", 7),
        HentaiHand("ar", 8),
        HentaiHand("sk", 9),
        HentaiHand("mn", 10),
        HentaiHand("uk", 11),
        HentaiHand("la", 12),
        HentaiHand("tl", 13),
        HentaiHand("es", 14),
        HentaiHand("it", 15),
        HentaiHand("ko", 16),
        HentaiHand("th", 17),
        HentaiHand("pl", 18),
        HentaiHand("fr", 19),
        HentaiHand("pt", 20),
        HentaiHand("de", 21),
        HentaiHand("fi", 22),
        HentaiHand("ru", 23),
        HentaiHand("sv", 24),
        HentaiHand("hu", 25),
        HentaiHand("id", 26),
        HentaiHand("vi", 27),
        HentaiHand("da", 28),
        HentaiHand("ro", 29),
        HentaiHand("et", 30),
        HentaiHand("nl", 31),
        HentaiHand("ca", 32),
        HentaiHand("tr", 33),
        HentaiHand("el", 34),
        HentaiHand("no", 35),
        HentaiHand("sq", 1501),
        HentaiHand("bg", 1502),
    )
}
