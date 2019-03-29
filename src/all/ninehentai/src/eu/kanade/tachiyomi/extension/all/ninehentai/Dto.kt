package eu.kanade.tachiyomi.extension.all.ninehentai

import eu.kanade.tachiyomi.source.model.Filter

data class Manga(
        val id : Int,
         var title: String,
        val image_server: String,
        val tags: List<Tag>,
        val total_page: Int
)

class Tag(
        val id: Int,
        name: String,
        val description: String = "null",
        val type: Int = 1
): Filter.TriState(name)


data class SearchRequest(
        val text: String,
        val page: Int,
        val sort: Int,
        val pages: Map<String, IntArray> = mapOf("range" to intArrayOf(0, 2000)),
        val tag: Map<String, Items>
)

data class Items(
        val included: MutableList<Tag>,
        val excluded: MutableList<Tag>
)