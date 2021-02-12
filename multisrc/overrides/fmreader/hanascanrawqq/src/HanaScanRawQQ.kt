package eu.kanade.tachiyomi.extension.ja.hanascanrawqq

import eu.kanade.tachiyomi.multisrc.fmreader.FMReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request

class HanaScanRawQQ : FMReader("HanaScan (RawQQ)", "https://hanascan.com", "ja") {
    override fun popularMangaNextPageSelector() = "div.col-md-8 button"
    // Referer needs to be chapter URL
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().set("Referer", page.url).build())
}