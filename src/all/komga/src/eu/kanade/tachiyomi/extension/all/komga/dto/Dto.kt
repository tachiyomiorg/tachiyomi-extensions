package eu.kanade.tachiyomi.extension.all.komga.dto

data class SerieDto(
    val id: Long,
    val name: String
)

data class BookDto(
    val id: Long,
    val name: String,
    val url: String,
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
