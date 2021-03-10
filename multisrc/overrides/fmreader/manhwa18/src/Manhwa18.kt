package eu.kanade.tachiyomi.extension.en.manhwa18

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class Manhwa18 : FMReader("Manhwa18", "https://manhwa18.com", "en") {
    override fun imageRequest(page: Page): Request {
        return if (page.imageUrl!!.contains("manhwa18")) {
            super.imageRequest(page)
        } else {
            GET(page.imageUrl!!, headers.newBuilder().removeAll("Referer").build())
        }
    }
    override fun getGenreList() = getAdultGenreList()
}