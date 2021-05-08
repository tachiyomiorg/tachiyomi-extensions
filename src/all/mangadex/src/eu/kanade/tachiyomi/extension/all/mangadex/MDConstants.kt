package eu.kanade.tachiyomi.extension.all.mangadex

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object MDConstants {

    val uuidRegex =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    val mangaLimit = 25
    val apiUrl = "https://api.mangadex.org"
    val apiMangaUrl = "$apiUrl/manga"
    val atHomePostUrl = "https://api.mangadex.network/report"
    val whitespaceRegex = "\\s".toRegex()

    val tempCover = "https://i.imgur.com/6TrIues.jpg"

    const val mdAtHomeTokenLifespan = 5 * 60 * 1000

    val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss+SSS", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    const val prefixIdSearch = "id:"

    const val dataSaverPref = "dataSaverV5"

    fun getDataSaverPreferenceKey(dexLang: String): String {
        return "${dataSaverPref}_$dexLang"
    }

    private const val standardHttpsPortPref = "usePort443"

    fun getStandardHttpsPreferenceKey(dexLang: String): String {
        return "${standardHttpsPortPref}_$dexLang"
    }

    private const val contentRatingPref = "contentRating"
    const val contentRatingPrefValSafe = "safe"
    const val contentRatingPrefValSuggestive = "suggestive"
    const val contentRatingPrefValErotica = "erotica"
    const val contentRatingPrefValPornographic = "pornographic"
    val contentRatingPrefDefaults = setOf(contentRatingPrefValSafe, contentRatingPrefValSuggestive)

    fun getContentRatingPrefKey(dexLang: String): String {
        return "${contentRatingPref}_$dexLang"
    }
}
