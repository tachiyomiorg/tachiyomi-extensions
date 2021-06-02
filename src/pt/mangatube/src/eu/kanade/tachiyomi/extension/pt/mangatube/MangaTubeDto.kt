package eu.kanade.tachiyomi.extension.pt.mangatube

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class MangaTubeLatestDto(
    val page: String,
    val releases: List<MangaTubeReleaseDto> = emptyList(),
    @SerialName("total_page") val totalPage: Int
)

@Serializable
data class MangaTubeReleaseDto(
    val image: String,
    val link: String,
    val name: String
)

@Serializable
data class MangaTubeTitleDto(
    @SerialName("img") val image: String,
    val title: String,
    val url: String
)

@Serializable
data class MangaTubePaginatedChaptersDto(
    val chapters: List<MangaTubeChapterDto>? = emptyList(),
    @SerialName("pagina") val page: Int,
    @SerialName("total_pags") val totalPages: Int
)

@Serializable
data class MangaTubeChapterDto(
    @SerialName("date_created") val dateCreated: String,
    val link: String,
    @SerialName("chapter_name") val name: JsonPrimitive,
    val number: JsonPrimitive
)

@Serializable
data class MangaTubeReaderDto(
    val images: List<MangaTubePageDto> = emptyList()
)

@Serializable
data class MangaTubePageDto(
    val url: String
)
