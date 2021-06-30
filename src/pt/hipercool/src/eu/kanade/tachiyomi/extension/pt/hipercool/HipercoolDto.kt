package eu.kanade.tachiyomi.extension.pt.hipercool

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class HipercoolBookDto(
    val chapters: JsonElement,
    val revision: Int = 1,
    val slug: String,
    val synopsis: String? = null,
    val tags: List<HipercoolTagDto> = emptyList(),
    val title: String
)

@Serializable
data class HipercoolTagDto(
    val label: String,
    val values: List<HipercoolTagDto> = emptyList()
)

@Serializable
data class HipercoolChapterDto(
    @SerialName("_book") val book: HipercoolBookDto? = null,
    val images: Int = 0,
    @SerialName("publishied_at") val publishedAt: String,
    val slug: String,
    val title: String
)
