package eu.kanade.tachiyomi.extension.th.nekopost

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

object NPUtils {
    private val urlWithoutDomainFromFullUrlRegex: Regex = Regex("^https://www\\.nekopost\\.net/manga/(.*)$")


    fun getUrlWithoutDomainFromFullUrl(url: String): String {
        val (urlWithoutDomain) = urlWithoutDomainFromFullUrlRegex.find(url)!!.destructured
        return urlWithoutDomain
    }

    fun convertDateStringToEpoch(dateStr: String, format: String = "yyyy-MM-dd"): Long = SimpleDateFormat(format, Locale("th")).parse(dateStr).time

    fun getSearchQuery(keyword: String = "", genreList: Array<Genre>, statusList: Array<Status>): String {
        val keywordQuery = "ip_keyword=$keyword"

        val genreQuery = genreList.joinToString("&") { genre -> "ip_genre[]=${genre.genre}" }

        val statusQuery = statusList.let {
            if (it.isNotEmpty()) it
            else Status.values()
        }.joinToString("&") { status -> "ip_status[]=${status.status}" }

        val typeQuery = "ip_type[]=m"

        return "$keywordQuery&$genreQuery&$statusQuery&$typeQuery"
    }

    enum class Genre(val title: String, val genre: Int) {
        ACTION("Action", 2),
        ADVENTURE("Adventure", 13),
        COMEDY("Comedy", 8),
        DOUJINSHI("Doujinshi", 37),
        DRAMA("Drama", 3),
        FANTASY("Fantasy", 1),
        GENDER_BENDER("Gender Bender", 26),
        GRUME("Grume", 41),
        HORROR("Horror", 47),
        ISEKAI("Isekai", 44),
        MYSTERY("Mystery", 32),
        ONE_SHORT("One short", 48),
        ROMANCE("Romance", 10),
        SCHOOL_LIFE("School Life", 43),
        SCI_FI("Sci-fi", 7),
        SECOND_LIFE("Second Life", 45),
        SEINEN("Seinen", 49),
        SHOUJO("Shoujo", 42),
        SHOUNEN("Shounen", 46),
        SLICE_OF_LIFE("Slice of Life", 9),
        SPORT("Sport", 5),
        TRAP("Trap", 25),
        YAOI("Yaoi", 23),
        YURI("Yuri", 24);

        companion object {
            fun getGenre(title: String): Genre? = values().find { genre -> genre.title == title }
        }
    }

    enum class Status(val title: String, val status: Int) {
        ONGOING("Ongoing", 1),
        COMPLETED("Completed", 2),
        LICENSED("Licensed", 3);

        companion object {
            fun getStatus(title: String): Status? = values().find { Status -> Status.title == title }
        }
    }

    val monthList: Array<String> = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
}
