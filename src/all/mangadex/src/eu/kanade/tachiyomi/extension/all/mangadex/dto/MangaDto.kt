package eu.kanade.tachiyomi.extension.all.mangadex.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangaListDto(
    val limit: Int,
    val offset: Int,
    val total: Int,
    val results: List<MangaDto>,
)

@Serializable
data class MangaDto(
    val result: String,
    val data: MangaDataDto,
    val relationships: List<RelationshipDto>,
)

@Serializable
data class RelationshipDto(
    val id: String,
    val type: String,
    val attributes: IncludesAttributesDto? = null,
)

@Serializable
data class IncludesAttributesDto(
    val name: String? = null,
    val fileName: String? = null,
)

@Serializable
data class MangaDataDto(
    val id: String,
    val type: String,
    val attributes: MangaAttributesDto,
)

@Serializable
data class MangaAttributesDto(
    val title: Map<String, String>,
    val altTitles: List<Map<String, String>>,
    val description: Map<String, String>,
    val links: Map<String, String>?,
    val originalLanguage: String,
    val lastVolume: String?,
    val lastChapter: String?,
    val contentRating: String?,
    val publicationDemographic: String?,
    val status: String?,
    val year: Int?,
    val tags: List<TagDto>,
)

@Serializable
data class TagDto(
    val id: String,
)
