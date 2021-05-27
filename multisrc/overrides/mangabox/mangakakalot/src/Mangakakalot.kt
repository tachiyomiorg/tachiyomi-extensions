package eu.kanade.tachiyomi.extension.en.mangakakalot

import eu.kanade.tachiyomi.multisrc.mangabox.MangaBox
import okhttp3.Headers
import java.text.SimpleDateFormat
import java.util.Locale

class Mangakakalot : MangaBox("Mangakakalot", "https://mangakakalot.com", "en", SimpleDateFormat("MMM dd,yy", Locale.ENGLISH)) {
    override fun headersBuilder(): Headers.Builder = super.headersBuilder().set("Referer", "https://manganato.com") // for covers
    override val simpleQueryPath = "search/story/"
    override fun searchMangaSelector() = "${super.searchMangaSelector()}, div.list-truyen-item-wrap"
}
