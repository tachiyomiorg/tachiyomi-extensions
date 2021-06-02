package eu.kanade.tachiyomi.extension.en.mangamutiny

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
data class MangaMutinyMangasPageResponse(val items: List<MangaMutinyItem>, val total: Int)

@Serializable
data class MangaMutinyItem(
    val title: String,
    val slug: String,
    val thumbnail: String,
    val status: String? = null,
    val summary: String? = null,
    val artists: String? = null,
    val authors: String? = null,
    val genre: List<String>? = null,
    val chapters: List<MangaMutinyChapter>? = null
) {
    fun toSManga(): SManga = SManga.create().apply {
        this.title = this@MangaMutinyItem.title
        this.thumbnail_url = this@MangaMutinyItem.thumbnail
        this.url = this@MangaMutinyItem.slug

        this.status = when (this@MangaMutinyItem.status) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        this.description = this@MangaMutinyItem.summary
        this.artist = this@MangaMutinyItem.artists
        this.author = this@MangaMutinyItem.authors
        this.genre = this@MangaMutinyItem.genre?.joinToString()
    }
}

@Serializable
data class MangaMutinyPageResponse(
    val storage: String,
    val manga: String,
    val id: String,
    val images: List<String>
) {
    private val chapterUrl = "$storage/$manga/$id/"
    fun toPageList(): List<Page> =
        images.mapIndexed { index, pageSuffix -> Page(index, "", chapterUrl + pageSuffix) }
}

@Serializable
data class MangaMutinyChapter(
    val volume: Int?,
    val chapter: Float,
    val title: String?,
    val slug: String,
    val releasedAt: String
) {
    companion object {
        private val dateFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }

        /**
         * Converts this Float into a String, removing any trailing .0
         */
        fun Float.toStringWithoutDotZero(): String = when (this % 1) {
            0F -> this.toInt().toString()
            else -> this.toString()
        }
    }

    private fun chapterTitleBuilder(): String {
        val chapterTitle = StringBuilder()
        if (volume != null) {
            chapterTitle.append("Vol. $volume ")
        }
        chapterTitle.append("Chapter ${chapter.toStringWithoutDotZero()}")
        if (title != null && title != "") {
            chapterTitle.append(": $title")
        }
        return chapterTitle.toString()
    }

    fun toSChapter(): SChapter = SChapter.create().apply {
        this.name = this@MangaMutinyChapter.chapterTitleBuilder()
        this.url = this@MangaMutinyChapter.slug
        this.date_upload = dateFormatter.parse(releasedAt)?.time ?: 0
        this.chapter_number = this@MangaMutinyChapter.chapter
    }
}
