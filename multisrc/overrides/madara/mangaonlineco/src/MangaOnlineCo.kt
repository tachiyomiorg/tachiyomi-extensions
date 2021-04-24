package eu.kanade.tachiyomi.extension.th.mangaonlineco

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaOnlineCo : Madara("Manga-Online.co", "https://www.manga-online.co", "th", SimpleDateFormat("MMM dd, yyyy", Locale("th"))) {
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()
}
