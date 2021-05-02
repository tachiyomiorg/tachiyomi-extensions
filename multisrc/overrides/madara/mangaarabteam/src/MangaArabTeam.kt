package eu.kanade.tachiyomi.extension.ar.mangaarabteam

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Page
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class MangaArabTeam : Madara("مانجا عرب تيم Manga Arab Team", "https://mangaarabteam.com", "ar", SimpleDateFormat("dd MMM، yyyy", Locale.forLanguageTag("ar"))) {
    override fun imageRequest(page: Page): Request {
        return GET(page.imageUrl!!.replace("http:", "https:"))
    }
}
