package eu.kanade.tachiyomi.extension.all.nhentai

import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.*

private val ONGOING_SUFFIX = arrayOf(
        "[ongoing]",
        "(ongoing)",
        "{ongoing}"
)

private val EX_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

fun NHentaiMetadata.copyTo(manga: SManga) {
    url?.let { manga.url = it }

    mediaId?.let { mid ->
        NHentaiMetadata.typeToExtension(thumbnailImageType)?.let {
            manga.thumbnail_url = "https://t.nhentai.net/galleries/$mid/thumb.$it"
        }
    }

    manga.title = englishTitle ?: japaneseTitle ?: shortTitle!!

    //Set artist (if we can find one)
    tags["artist"]?.let {
        if (it.isNotEmpty()) manga.artist = it.joinToString(transform = Tag::name)
    }

    tags["category"]?.let {
        if (it.isNotEmpty()) manga.genre = it.joinToString(transform = Tag::name)
    }

    //Try to automatically identify if it is ongoing, we try not to be too lenient here to avoid making mistakes
    //We default to completed
    manga.status = SManga.COMPLETED
    englishTitle?.let { t ->
        if (ONGOING_SUFFIX.any {
            t.endsWith(it, ignoreCase = true)
        }) manga.status = SManga.ONGOING
    }

    val titleDesc = StringBuilder()
    englishTitle?.let { titleDesc += "English Title: $it\n" }
    japaneseTitle?.let { titleDesc += "Japanese Title: $it\n" }
    shortTitle?.let { titleDesc += "Short Title: $it\n" }

    val detailsDesc = StringBuilder()
    uploadDate?.let { detailsDesc += "Upload Date: ${EX_DATE_FORMAT.format(Date(it))}\n" }
    pageImageTypes.size.let { detailsDesc += "Length: $it pages\n" }
    favoritesCount?.let { detailsDesc += "Favorited: $it times\n" }
    scanlator?.nullIfBlank()?.let { detailsDesc += "Scanlator: $it\n" }

    val tagsDesc = buildTagsDescription(this)

    manga.description = listOf(titleDesc.toString(), detailsDesc.toString(), tagsDesc.toString())
            .filter(String::isNotBlank)
            .joinToString(separator = "\n")
}

private fun buildTagsDescription(metadata: NHentaiMetadata)
        = StringBuilder("Tags:\n").apply {
    //BiConsumer only available in Java 8, we have to use destructuring here
    metadata.tags.forEach { (namespace, tags) ->
        if (tags.isNotEmpty()) {
            val joinedTags = tags.joinToString(separator = " ", transform = { "<${it.name}>" })
            this += "▪ $namespace: $joinedTags\n"
        }
    }
}

