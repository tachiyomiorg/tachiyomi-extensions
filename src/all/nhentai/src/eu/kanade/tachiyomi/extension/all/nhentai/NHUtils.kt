package eu.kanade.tachiyomi.extension.all.nhentai

import org.jsoup.nodes.Document
import java.text.SimpleDateFormat

class NHUtils {
    companion object {
        fun getArtists(document: Document): String {
            return document.select("#tags > div:nth-child(4) > span > a")
                .map { cleanTag(it.text()) }
                .joinToString(", ") { it.trim() } 
        }

        fun getGroups(document: Document): String? {
            val groups = document.select("#tags > div:nth-child(5) > span > a")
                .map { cleanTag(it.text()) }
                .joinToString(", ") { it.trim() }

            return if (groups.isEmpty()) null else groups
        }

        fun getDesc(document: Document): String {
            val parodies = document.select("#tags > div:nth-child(1) > span > a")
                .map { cleanTag(it.text()) }
                .joinToString(", ") { it.trim() }
            val characters = document.select("#tags > div:nth-child(2) > span > a")
                .map { cleanTag(it.text()) }
                .joinToString(", ") { it.trim() }
            return parodies.plus("\n\n").plus(characters)
        }

        fun getTags(document: Document): String {
            return document.select("#tags > div:nth-child(3) > span > a")
                .map { cleanTag(it.text()) }
                .joinToString(", ") { it.trim() }            
        }
        
        fun getTime(document: Document): Long {
            val timeString = document.toString().substringAfter("datetime=\"").substringBefore("\">").replace("T", " ")

            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSZ").parse(timeString).time
        }
        private fun cleanTag(tag: String): String = tag.replace(Regex("\\(.*\\)"), "").trim()
    }
}
