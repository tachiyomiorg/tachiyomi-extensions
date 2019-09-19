package eu.kanade.tachiyomi.extension.en.tsumino

import org.jsoup.nodes.Document
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

class TsuminoUtils {
    companion object {
        fun getArtists(document: Document): String {
            val stringBuilder = StringBuilder()
            val artists = document.select("#Artist a")

            artists.forEach {
                stringBuilder.append(it.text())

                if (it != artists.last())
                    stringBuilder.append(", ")
            }

            return stringBuilder.toString()
        }

        fun getGroups(document: Document): String? {
            val stringBuilder = StringBuilder()
            val groups = document.select("#Group a")

            groups.forEach {
                stringBuilder.append(it.text())

                if (it != groups.last())
                    stringBuilder.append(", ")
            }

            return if (stringBuilder.toString().isEmpty()) null else stringBuilder.toString()
        }

        fun getDesc(document: Document): String {
            val stringBuilder = StringBuilder()
            val pages = document.select("#Pages").text()
            val parodies = document.select("#Parody a")
            val characters = document.select("#Character a")

            stringBuilder.append("Pages: $pages")

            if (parodies.size > 0) {
                stringBuilder.append("\n\n")
                stringBuilder.append("Parodies: ")

                parodies.forEach {
                    stringBuilder.append(it.text())

                    if (it != parodies.last())
                        stringBuilder.append(", ")
                }
            }

            if (characters.size > 0) {
                stringBuilder.append("\n\n")
                stringBuilder.append("Characters: ")

                characters.forEach {
                    stringBuilder.append(it.text())

                    if (it != characters.last())
                        stringBuilder.append(", ")
                }
            }
            return stringBuilder.toString()
        }

        fun getDate(document: Document): Long {
            val timeString = document.select("#Uploaded").text()
            return try {
                SimpleDateFormat("yyyy MMMMM dd", Locale.ENGLISH).parse(timeString).time
            }catch (e: Exception) {
                0
            }
        }

    }
}
