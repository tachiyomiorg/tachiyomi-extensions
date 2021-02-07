package eu.kanade.tachiyomi.extension.ar.goldenmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import java.text.SimpleDateFormat
import java.util.Locale


class GeceninLordu : Madara("Gecenin Lordu", "https://geceninlordu.com/", "tr", SimpleDateFormat("dd MMM yyyy", Locale("tr"))) {
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/?s=$query&post_type=wp-manga")
}
