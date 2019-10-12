package eu.kanade.tachiyomi.extension.all.komga.dto

data class LibraryDto(
    val id: Long,
    val name: String
)

data class SeriesDto(
    val id: Long,
    val name: String,
    val lastModified: String?
)

data class BookDto(
    val id: Long,
    val name: String,
    val lastModified: String?,
    val metadata: BookMetadataDto
)

data class BookMetadataDto(
    val status: String,
    val mediaType: String
)

data class PageDto(
    val number: Int,
    val fileName: String,
    val mediaType: String
)
