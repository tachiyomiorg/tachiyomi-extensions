package eu.kanade.tachiyomi.extension.all.nhentai

import org.jsoup.nodes.Document
import java.lang.StringBuilder
import java.text.SimpleDateFormat

class NHUtils {
    companion object {
        fun getArtists(document: Document): String {
            val stringBuilder = StringBuilder()
            val artists = document.select("#tags > div:nth-child(4) > span > a")

            artists.forEach {
                stringBuilder.append(it.text().replace(Regex("\\(.*\\)"), ""))

                if (it != artists.last())
                    stringBuilder.append(", ")
            }

            return stringBuilder.toString()
        }

        fun getGroups(document: Document): String? {
            val stringBuilder = StringBuilder()
            val groups = document.select("#tags > div:nth-child(5) > span > a")

            groups.forEach {
                stringBuilder.append(it.text().replace(Regex("\\(.*\\)"), ""))

                if (it != groups.last())
                    stringBuilder.append(", ")
            }

            return if (stringBuilder.toString().isEmpty()) null else stringBuilder.toString()
        }

        fun getDesc(document: Document): String {
            val parodies = document.select("#tags > div:nth-child(1) > span > a")
                .map { it.text().replace(Regex("\\(.*\\)"), "") }
                .joinToString(", ") { it.trim() }
            val characters = document.select("#tags > div:nth-child(2) > span > a")
                .map { it.text().replace(Regex("\\(.*\\)"), "") }
                .joinToString(", ") { it.trim() }
            return parodies.plus("\n\n").plus(characters)
        }

        fun getTags(document: Document): String {
            return document.select("#tags > div:nth-child(3) > span > a")
                .map { it.text().replace(Regex("\\(.*\\)"), "") }
                .joinToString(", ") { it.trim() }            
        }
        
        fun getTime(document: Document): Long {
            val timeString = document.toString().substringAfter("datetime=\"").substringBefore("\">").replace("T", " ")

            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSZ").parse(timeString).time
        }
    }
}
