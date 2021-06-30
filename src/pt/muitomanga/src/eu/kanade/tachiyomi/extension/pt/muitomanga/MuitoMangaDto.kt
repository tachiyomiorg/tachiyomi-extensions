package eu.kanade.tachiyomi.extension.pt.muitomanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MuitoMangaDirectoryDto(
    @SerialName("encontrado") val results: List<MuitoMangaTitleDto> = emptyList()
)

@Serializable
data class MuitoMangaTitleDto(
    @SerialName("imagem") val image: String,
    @SerialName("titulo") val title: String,
    val url: String
)
