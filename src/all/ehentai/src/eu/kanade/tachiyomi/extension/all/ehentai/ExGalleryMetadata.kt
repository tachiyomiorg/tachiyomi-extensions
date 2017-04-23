package eu.kanade.tachiyomi.extension.all.ehentai;

import java.util.*

/**
 * Gallery metadata storage model
 */

class ExGalleryMetadata {
    var url: String? = null

    var thumbnailUrl: String? = null

    var title: String? = null
    var altTitle: String? = null

    var genre: String? = null

    var datePosted: Long? = null
    var parent: String? = null
    var visible: String? = null //Not a boolean
    var language: String? = null
    var translated: Boolean? = null
    var size: Long? = null
    var length: Int? = null
    var favorites: Int? = null
    var ratingCount: Int? = null
    var averageRating: Double? = null

    var uploader: String? = null

    val tags: HashMap<String, ArrayList<Tag>> = HashMap()
}