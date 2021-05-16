package eu.kanade.tachiyomi.extension.en.manhuascan

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.source.model.SChapter

import eu.kanade.tachiyomi.annotations.Nsfw


@Nsfw
class ManhuaScan : FMReader("ManhuaScan", "https://manhuascan.com", "en") {
    override fun fetchPageList(chapter: SChapter) = fetchPageListEncrypted(chapter)
}
