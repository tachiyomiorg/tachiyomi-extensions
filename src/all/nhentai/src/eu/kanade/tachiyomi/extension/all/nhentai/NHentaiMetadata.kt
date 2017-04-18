package eu.kanade.tachiyomi.extension.all.nhentai

/**
 * NHentai metadata
 */

class NHentaiMetadata {

    var id: Long? = null

    var url: String? get() = id?.let { "$BASE_URL/g/$it" }
    set(a) {
        a?.let {
            id = a.split("/").last().toLong()
        }
    }

    var uploadDate: Long? = null

    var favoritesCount: Long? = null

    var mediaId: String? = null

    var japaneseTitle: String? = null
    var englishTitle: String? = null
    var shortTitle: String? = null

    var coverImageType: String? = null
    var pageImageTypes: MutableList<String> = mutableListOf()
    var thumbnailImageType: String? = null

    var scanlator: String? = null

    val tags: MutableMap<String, MutableList<Tag>> = mutableMapOf()

    companion object {
        val BASE_URL = "https://nhentai.net"

        fun typeToExtension(t: String?) =
            when(t) {
                "p" -> "png"
                "j" -> "jpg"
                else -> null
            }
    }
}

