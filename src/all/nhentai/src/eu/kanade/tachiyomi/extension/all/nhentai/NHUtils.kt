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
                stringBuilder.append(cleanTag(it.text()))

                if (it != artists.last())
                    stringBuilder.append(", ")
            }

            return stringBuilder.toString()
        }

        fun getGroups(document: Document): String? {
            val stringBuilder = StringBuilder()
            val groups = document.select("#tags > div:nth-child(5) > span > a")

            groups.forEach {
                stringBuilder.append(cleanTag(it.text()))

                if (it != groups.last())
                    stringBuilder.append(", ")
            }

            return if (stringBuilder.toString().isEmpty()) null else stringBuilder.toString()
        }

        fun getTags(document: Document): String {
            val stringBuilder = StringBuilder()
            val parodies = document.select("#tags > div:nth-child(1) > span > a")
            val characters = document.select("#tags > div:nth-child(2) > span > a")
            val tags = document.select("#tags > div:nth-child(3) > span > a")

            if (parodies.size > 0) {
                stringBuilder.append("Parodies: ")

                parodies.forEach {
                    stringBuilder.append(cleanTag(it.text()))

                    if (it != parodies.last())
                        stringBuilder.append(", ")
                }

                stringBuilder.append("\n\n")
            }

            if (characters.size > 0) {
                stringBuilder.append("Characters: ")

                characters.forEach {
                    stringBuilder.append(cleanTag(it.text()))

                    if (it != characters.last())
                        stringBuilder.append(", ")
                }

                stringBuilder.append("\n\n")
            }

            if (tags.size > 0) {
                stringBuilder.append("Tags: ")

                tags.forEach {
                    stringBuilder.append(cleanTag(it.text()))

                    if (it != tags.last())
                        stringBuilder.append(", ")
                }
            }

            return stringBuilder.toString()
        }

        fun getTime(document: Document): Long {
            val timeString = document.toString().substringAfter("datetime=\"").substringBefore("\">").replace("T", " ")

            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSSSSZ").parse(timeString).time
        }

        private fun cleanTag(tag: String): String = tag.replace(Regex("\\(.*\\)"), "").trim()
    }
}