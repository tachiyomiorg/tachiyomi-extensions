package eu.kanade.tachiyomi.extension.all.mangadex

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object MDConstants {

    val uuidRegex =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")

    const val mangaLimit = 20
    const val cdnUrl = "https://uploads.mangadex.org"

    const val apiUrl = "https://api.mangadex.org"
    const val apiMangaUrl = "$apiUrl/manga"
    const val apiCoverUrl = "$apiUrl/cover"
    const val atHomePostUrl = "https://api.mangadex.network/report"
    val whitespaceRegex = "\\s".toRegex()

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

    private const val contentRatingSafePref = "contentRatingSafe"

    fun getContentRatingSafePrefKey(dexLang: String): String {
        return "${contentRatingSafePref}_$dexLang"
    }

    private const val contentRatingSuggestivePref = "contentRatingSuggestive"

    fun getContentRatingSuggestivePrefKey(dexLang: String): String {
        return "${contentRatingSuggestivePref}_$dexLang"
    }

    private const val contentRatingEroticaPref = "contentRatingErotica"

    fun getContentRatingEroticaPrefKey(dexLang: String): String {
        return "${contentRatingEroticaPref}_$dexLang"
    }

    private const val contentRatingPornographicPref = "contentRatingPornographic"

    fun getContentRatingPornographicPrefKey(dexLang: String): String {
        return "${contentRatingPornographicPref}_$dexLang"
    }
}
