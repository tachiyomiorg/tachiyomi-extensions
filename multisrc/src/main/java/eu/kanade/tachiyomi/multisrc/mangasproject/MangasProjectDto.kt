package eu.kanade.tachiyomi.multisrc.mangasproject

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class MangasProjectMostReadDto(
    @SerialName("most_read") val mostRead: List<MangasProjectSerieDto> = emptyList()
)

@Serializable
data class MangasProjectReleasesDto(
    val releases: List<MangasProjectSerieDto> = emptyList()
)

@Serializable
data class MangasProjectSearchDto(
    val series: JsonElement
)

@Serializable
data class MangasProjectSerieDto(
    val cover: String = "",
    val image: String = "",
    val link: String,
    val name: String = "",
    @SerialName("serie_name") val serieName: String = ""
)

@Serializable
data class MangasProjectChapterListDto(
    val chapters: JsonElement
)

@Serializable
data class MangasProjectChapterDto(
    @SerialName("date_created") val dateCreated: String,
    @SerialName("chapter_name") val name: String,
    val number: String,
    val releases: Map<String, MangasProjectChapterReleaseDto> = emptyMap()
)

@Serializable
data class MangasProjectChapterReleaseDto(
    val link: String,
    val scanlators: List<MangasProjectScanlatorDto> = emptyList()
)

@Serializable
data class MangasProjectScanlatorDto(
    val name: String
)

@Serializable
data class MangasProjectReaderDto(
    val images: List<String> = emptyList()
)
