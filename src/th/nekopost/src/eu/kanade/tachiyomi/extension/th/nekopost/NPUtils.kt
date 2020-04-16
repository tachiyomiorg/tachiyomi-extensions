package eu.kanade.tachiyomi.extension.th.nekopost

import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.*

class NPArrayList<E>(c: Collection<E>, val mangaList: List<Element>) : ArrayList<E>(c) {
    override fun isEmpty(): Boolean = mangaList.isEmpty()

    fun isNotEmpty(): Boolean = mangaList.isNotEmpty()

    fun isListEmpty(): Boolean = super.isEmpty()

    fun isListNotEmpty(): Boolean = !isListEmpty()

}

object NPUtils {
    private val urlWithoutDomainFromFullUrlRegex: Regex = Regex("^https://www\\.nekopost\\.net/manga/(.*)$")


    fun getMangaOrChapterAlias(url: String): String {
        val (urlWithoutDomain) = urlWithoutDomainFromFullUrlRegex.find(url)!!.destructured
        return urlWithoutDomain
    }

    fun convertDateStringToEpoch(dateStr: String, format: String = "yyyy-MM-dd"): Long = SimpleDateFormat(format, Locale("th")).parse(dateStr).time

    fun getSearchQuery(keyword: String = "", genreList: Array<String>, statusList: Array<String>): String {
        val keywordQuery = "ip_keyword=$keyword"

        val genreQuery = genreList.joinToString("&") { genre -> "ip_genre[]=${getValueOf(Genre, genre)}" }

        val statusQuery = statusList.let {
            if (it.isNotEmpty()) it.map { status -> getValueOf(Status, status) }
            else Status.map { status -> status.second }
        }.joinToString("&") { status -> "ip_status[]=$status" }

        val typeQuery = "ip_type[]=m"

        return "$keywordQuery&$genreQuery&$statusQuery&$typeQuery"
    }

    val Genre = arrayOf(
        Pair("Fantasy", 1),
        Pair("Action", 2),
        Pair("Drama", 3),
        Pair("Sport", 5),
        Pair("Sci-fi", 7),
        Pair("Comedy", 8),
        Pair("Slice of Life", 9),
        Pair("Romance", 10),
        Pair("Adventure", 13),
        Pair("Yaoi", 23),
        Pair("Yuri", 24),
        Pair("Trap", 25),
        Pair("Gender Bender", 26),
        Pair("Mystery", 32),
        Pair("Doujinshi", 37),
        Pair("Grume", 41),
        Pair("Shoujo", 42),
        Pair("School Life", 43),
        Pair("Isekai", 44),
        Pair("Shounen", 46),
        Pair("Second Life", 45),
        Pair("Horror", 47),
        Pair("One short", 48),
        Pair("Seinen", 49)
    ).sortedWith(compareBy { it.first }).toTypedArray()

    val Status = arrayOf(
        Pair("Ongoing", 1),
        Pair("Completed", 2),
        Pair("Licensed", 3)
    )

    fun <T, F, S> getValueOf(array: Array<T>, name: F): S? where T : Pair<F, S> = array.find { genre -> genre.first == name }?.second

    val monthList: Array<String> = arrayOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
}
