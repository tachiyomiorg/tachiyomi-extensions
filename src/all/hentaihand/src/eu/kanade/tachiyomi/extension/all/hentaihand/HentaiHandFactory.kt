package eu.kanade.tachiyomi.extension.all.hentaihand

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

@Nsfw
class HentaiHandFactory : SourceFactory {

    override fun createSources(): List<Source> = listOf(
        // https://hentaihand.com/api/languages?per_page=50
        HentaiHandOther(),
        HentaiHandEn(),
        HentaiHandZh(),
        HentaiHandJa(),
        HentaiHandNoText(),
        HentaiHandEo(),
        HentaiHandCeb(),
        HentaiHandCs(),
        HentaiHandAr(),
        HentaiHandSk(),
        HentaiHandMn(),
        HentaiHandUk(),
        HentaiHandLa(),
        HentaiHandTl(),
        HentaiHandEs(),
        HentaiHandIt(),
        HentaiHandKo(),
        HentaiHandTh(),
        HentaiHandPl(),
        HentaiHandFr(),
        HentaiHandPtBr(),
        HentaiHandDe(),
        HentaiHandFi(),
        HentaiHandRu(),
        HentaiHandSv(),
        HentaiHandHu(),
        HentaiHandId(),
        HentaiHandVi(),
        HentaiHandDa(),
        HentaiHandRo(),
        HentaiHandEt(),
        HentaiHandNl(),
        HentaiHandCa(),
        HentaiHandTr(),
        HentaiHandEl(),
        HentaiHandNo(),
        HentaiHandSq(),
        HentaiHandBg(),
    )
}

class HentaiHandOther : HentaiHand("other", extraName = " (Unfiltered)")
class HentaiHandEn : HentaiHand("en", 1)
class HentaiHandZh : HentaiHand("zh", 2)
class HentaiHandJa : HentaiHand("ja", 3)
class HentaiHandNoText : HentaiHand("other", 4, extraName = " (Text Cleaned)")
class HentaiHandEo : HentaiHand("eo", 5)
class HentaiHandCeb : HentaiHand("ceb", 6)
class HentaiHandCs : HentaiHand("cs", 7)
class HentaiHandAr : HentaiHand("ar", 8)
class HentaiHandSk : HentaiHand("sk", 9)
class HentaiHandMn : HentaiHand("mn", 10)
class HentaiHandUk : HentaiHand("uk", 11)
class HentaiHandLa : HentaiHand("la", 12)
class HentaiHandTl : HentaiHand("tl", 13)
class HentaiHandEs : HentaiHand("es", 14)
class HentaiHandIt : HentaiHand("it", 15)
class HentaiHandKo : HentaiHand("ko", 16)
class HentaiHandTh : HentaiHand("th", 17)
class HentaiHandPl : HentaiHand("pl", 18)
class HentaiHandFr : HentaiHand("fr", 19)
class HentaiHandPtBr : HentaiHand("pt-BR", 20) {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 2516244587139644000
}
class HentaiHandDe : HentaiHand("de", 21)
class HentaiHandFi : HentaiHand("fi", 22)
class HentaiHandRu : HentaiHand("ru", 23)
class HentaiHandSv : HentaiHand("sv", 24)
class HentaiHandHu : HentaiHand("hu", 25)
class HentaiHandId : HentaiHand("id", 26)
class HentaiHandVi : HentaiHand("vi", 27)
class HentaiHandDa : HentaiHand("da", 28)
class HentaiHandRo : HentaiHand("ro", 29)
class HentaiHandEt : HentaiHand("et", 30)
class HentaiHandNl : HentaiHand("nl", 31)
class HentaiHandCa : HentaiHand("ca", 32)
class HentaiHandTr : HentaiHand("tr", 33)
class HentaiHandEl : HentaiHand("el", 34)
class HentaiHandNo : HentaiHand("no", 35)
class HentaiHandSq : HentaiHand("sq", 1501)
class HentaiHandBg : HentaiHand("bg", 1502)
