package eu.kanade.tachiyomi.extension.en.animangaes

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response

class AniMangaEs : Madara("AniMangaEs", "https://animangaes.com", "en") {
    override val pageListParseSelector = "div.text-left noscript"
    override val chapterUrlSuffix = ""
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}
