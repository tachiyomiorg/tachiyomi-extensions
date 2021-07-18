package eu.kanade.tachiyomi.extension.pt.tsukimangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TsukiAuthRequestDto(
    val username: String,
    val password: String
)

@Serializable
data class TsukiAuthResultDto(
    val token: String? = null
)

@Serializable
data class TsukiPaginatedDto(
    val data: List<TsukiMangaDto> = emptyList(),
    val lastPage: Int,
    val page: Int,
    val perPage: Int,
    val total: Int
)

@Serializable
data class TsukiMangaDto(
    val artist: String? = "",
    val author: String? = "",
    val format: Int? = 1,
    val genres: List<TsukiGenreDto> = emptyList(),
    val id: Int,
    val poster: String? = "",
    val status: String? = "",
    val synopsis: String? = "",
    val title: String,
    val url: String
)

@Serializable
data class TsukiGenreDto(
    val genre: String
)

@Serializable
data class TsukiChapterDto(
    val number: String,
    val title: String? = "",
    val versions: List<TsukiChapterVersionDto> = emptyList()
)

@Serializable
data class TsukiChapterVersionDto(
    @SerialName("created_at") val createdAt: String,
    val id: Int,
    val scans: List<TsukiScanlatorDto> = emptyList()
)

@Serializable
data class TsukiScanlatorDto(
    val scan: TsukiScanlatorDetailDto
)

@Serializable
data class TsukiScanlatorDetailDto(
    val name: String
)

@Serializable
data class TsukiReaderDto(
    val pages: List<TsukiPageDto> = emptyList()
)

@Serializable
data class TsukiPageDto(
    val server: Int,
    val url: String
)
