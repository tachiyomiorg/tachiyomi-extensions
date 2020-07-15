package eu.kanade.tachiyomi.extension.all.komga.dto

data class LibraryDtoOld(
    val id: Long,
    val name: String
) {
    fun toLibraryDto() = LibraryDto(id.toString(), name)
}

data class SeriesDtoOld(
    val id: Long,
    val libraryId: Long,
    val name: String,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val booksCount: Int,
    val metadata: SeriesMetadataDto
) {
    fun toSeriesDto() = SeriesDto(id.toString(), libraryId.toString(), name, created, lastModified, fileLastModified, booksCount, metadata)
}

data class BookDtoOld(
    val id: Long,
    val seriesId: Long,
    val name: String,
    val number: Float,
    val created: String?,
    val lastModified: String?,
    val fileLastModified: String,
    val sizeBytes: Long,
    val size: String,
    val media: MediaDto,
    val metadata: BookMetadataDto
) {
    fun toBookDto() = BookDto(id.toString(), seriesId.toString(), name, number, created, lastModified, fileLastModified, sizeBytes, size, media, metadata)
}

data class CollectionDtoOld(
    val id: Long,
    val name: String,
    val ordered: Boolean,
    val seriesIds: List<Long>,
    val createdDate: String,
    val lastModifiedDate: String,
    val filtered: Boolean
) {
    fun toCollectionDto() = CollectionDto(id.toString(), name, ordered, seriesIds, createdDate, lastModifiedDate, filtered)
}
