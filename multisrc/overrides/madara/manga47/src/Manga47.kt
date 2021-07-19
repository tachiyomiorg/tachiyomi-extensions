package eu.kanade.tachiyomi.extension.en.manga47

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter

class Manga47 : Madara("Manga47", "https://manga47.net", "en") {

  override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()

}
