package eu.kanade.tachiyomi.extension.ko.mangashowme

import org.json.JSONArray

internal class MMCDNUrlHandler(scripts: String) {
    private val domains = JSONArray("[${scripts.substringBetween("var cdn_domains = [", "];")}]")
    private val chapter = scripts.substringBetween("var chapter = ", ";")
        .toIntOrNull() ?: 0

    fun replace(array: JSONArray): List<String> {
        return (0 until array.length())
            .map {
                val cdn: String = domains.get((chapter + 4 * it) % domains.length()) as String
                (array.get(it) as String)
                    .replace("cdntigermask.xyz", cdn)
                    .replace("cdnmadmax.xyz", cdn)
                    .replace("filecdn.xyz", cdn)
            }
    }
}
