package eu.kanade.tachiyomi.extension.all.mangadex.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    val title: JsonElement,
    val description: JsonElement,
    val originalLanguage: String,
    val lastVolume: String?,
    val lastChapter: String?,
    val contentRating: String?,
    val publicationDemographic: String?,
    val status: String?,
    val tags: List<TagDto>,
)

@Serializable
data class TagDto(
    val id: String,
)

fun JsonElement.asMdMap(): Map<String, String> {
    return runCatching {
        (this as JsonObject).map { it.key to it.value.toString() }.toMap()
    }.getOrElse { emptyMap() }
}
