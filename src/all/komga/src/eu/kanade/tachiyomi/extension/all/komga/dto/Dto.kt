package eu.kanade.tachiyomi.extension.all.komga.dto

data class SerieDto(
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
    val fileName: String
)
