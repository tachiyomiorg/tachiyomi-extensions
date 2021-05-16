package eu.kanade.tachiyomi.extension.en.manhwahot

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import eu.kanade.tachiyomi.source.model.SChapter

@Nsfw
class ManhwaHot : FMReader("ManhwaHot", "https://manhwahot.com", "en") {
    override fun fetchPageList(chapter: SChapter) = fetchPageListEncrypted(chapter)
}
