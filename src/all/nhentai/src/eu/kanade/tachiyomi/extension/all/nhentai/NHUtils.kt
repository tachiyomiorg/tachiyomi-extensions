package eu.kanade.tachiyomi.extension.all.nhentai

import java.text.SimpleDateFormat
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object NHUtils {
    fun getArtists(document: Document): String {
        val artists = document.select("#tags > div:nth-child(4) > span > a")
        return artists.joinToString(", ") { it.cleanTag() }
    }

    fun getGroups(document: Document): String? {
        val groups = document.select("#tags > div:nth-child(5) > span > a")
        return if (groups.isNotEmpty()) {
            groups.joinToString(", ") { it.cleanTag() }
        } else {
            null
        }
    }

    fun getTagDescription(document: Document): String {
        val stringBuilder = StringBuilder()

        val parodies = document.select("#tags > div:nth-child(1) > span > a")
        if (parodies.isNotEmpty()) {
            stringBuilder.append("Parodies: ")
            stringBuilder.append(parodies.joinToString(", ") { it.cleanTag() })
            stringBuilder.append("\n\n")
        }

        val characters = document.select("#tags > div:nth-child(2) > span > a")
        if (characters.isNotEmpty()) {
            stringBuilder.append("Characters: ")
            stringBuilder.append(characters.joinToString(", ") { it.cleanTag() })
        }

        return stringBuilder.toString()
    }

    fun getTags(document: Document): String {
        val tags = document.select("#tags > div:nth-child(3) > span > a")
        return tags.map { it.cleanTag() }.sorted().joinToString(", ")
    }

    fun getTime(document: Document): Long {
        val timeString = document.toString().substringAfter("datetime=\"").substringBefore("\">").replace("T", " ")

        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSZ").parse(timeString).time
    }

    private fun Element.cleanTag(): String = text().replace(Regex("\\(.*\\)"), "").trim()
}
