package eu.kanade.tachiyomi.extension.all.komga.dto

data class LibraryDto(
    val id: Long,
    val name: String
)

data class SeriesDto(
    val id: Long,
    val libraryId: Long,
    val name: String,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val booksCount: Int,
    val metadata: SeriesMetadataDto
)

data class SeriesMetadataDto(
    val status: String,
    val created: String?,
    val lastModified: String?
)

data class BookDto(
    val id: Long,
    val seriesId: Long,
    val name: String,
    val number: Float,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val sizeBytes: Long,
    val size: String,
    val media: MediaDto
)

data class MediaDto(
    val status: String,
    val mediaType: String,
    val pagesCount: Int
)

data class PageDto(
    val number: Int,
    val fileName: String,
    val mediaType: String
)
