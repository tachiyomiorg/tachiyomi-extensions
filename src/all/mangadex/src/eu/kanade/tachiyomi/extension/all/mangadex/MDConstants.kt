package eu.kanade.tachiyomi.extension.all.mangadex

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MDConstants {

    companion object {
        val uuidRegex =
            Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

        val mangaLimit = 25
        val apiUrl = "http://api.mangadex.org.dev.mdcloud.moe"
        val apiMangaUrl = "$apiUrl/manga"
        val atHomePostUrl = "https://api.mangadex.network/report"
        val whitespaceRegex = "\\s".toRegex()

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        const val prefixIdSearch = "id:"

        const val dataSaverPrefTitle = "Data saver"
        const val dataSaverPref = "dataSaver"

        const val mdAtHomeTokenLifespan = 10 * 60 * 1000
    }
}
