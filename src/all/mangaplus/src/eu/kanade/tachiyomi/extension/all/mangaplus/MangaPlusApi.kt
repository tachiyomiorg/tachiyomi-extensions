package eu.kanade.tachiyomi.extension.all.mangaplus

import kotlinx.serialization.Optional
import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer

@Serializer(forClass=MangaPlusResponse::class)
object MangaPlusSerializer

@Serializable
data class MangaPlusResponse(
    @Optional @SerialId(1) val success: SuccessResult? = null,
    @Optional @SerialId(2) val error: ErrorResult? = null
)

@Serializable
data class ErrorResult(
    @SerialId(1) val action: Action,
    @SerialId(2) val englishPopup: Popup,
    @SerialId(3) val spanishPopup: Popup
)

enum class Action { DEFAULT, UNAUTHORIZED, MAINTAINENCE, GEOIP_BLOCKING }

@Serializable
data class Popup(
    @SerialId(1) val subject: String,
    @SerialId(2) val body: String
)

@Serializable
data class SuccessResult(
    @Optional @SerialId(1) val isFeaturedUpdated: Boolean? = false,
    @Optional @SerialId(5) val allTitlesView: AllTitlesView? = null,
    @Optional @SerialId(6) val titleRankingView: TitleRankingView? = null,
    @Optional @SerialId(8) val titleDetailView: TitleDetailView? = null,
    @Optional @SerialId(10) val mangaViewer: MangaViewer? = null,
    @Optional @SerialId(11) val webHomeView: WebHomeView? = null
)

@Serializable
data class TitleRankingView(@SerialId(1) val titles: List<Title> = emptyList())

@Serializable
data class AllTitlesView(@SerialId(1) val titles: List<Title> = emptyList())

@Serializable
data class WebHomeView(@SerialId(2) val groups: List<UpdatedTitleGroup> = emptyList())

@Serializable
data class TitleDetailView(
    @SerialId(1) val title: Title,
    @SerialId(2) val titleImageUrl: String,
    @SerialId(3) val overview: String,
    @SerialId(4) val backgroundImageUrl: String,
    @Optional @SerialId(5) val nextTimeStamp: Int = 0,
    @Optional @SerialId(6) val updateTiming: UpdateTiming? = UpdateTiming.DAY,
    @Optional @SerialId(7) val viewingPeriodDescription: String = "",
    @SerialId(9) val firstChapterList: List<Chapter> = emptyList(),
    @Optional @SerialId(10) val lastChapterList: List<Chapter> = emptyList(),
    @Optional @SerialId(14) val isSimulReleased: Boolean = true,
    @Optional @SerialId(17) val chaptersDescending: Boolean = true
)

enum class UpdateTiming { NOT_REGULARLY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY, DAY }

@Serializable
data class MangaViewer(@SerialId(1) val pages: List<MangaPlusPage> = emptyList())

@Serializable
data class Title(
    @SerialId(1) val titleId: Int,
    @SerialId(2) val name: String,
    @SerialId(3) val author: String,
    @SerialId(4) val portraitImageUrl: String,
    @SerialId(5) val landscapeImageUrl: String,
    @SerialId(6) val viewCount: Int,
    @Optional @SerialId(7) val language: Language? = Language.ENGLISH
)

enum class Language(val id: Int) {
    @SerialId(0) ENGLISH(0),
    @SerialId(1) SPANISH(1)
}

@Serializable
data class UpdatedTitleGroup(
    @SerialId(1) val groupName: String,
    @SerialId(2) val titles: List<UpdatedTitle> = emptyList()
)

@Serializable
data class UpdatedTitle(
    @Optional @SerialId(1) val title: Title? = null
)

@Serializable
data class Chapter(
    @SerialId(1) val titleId: Int,
    @SerialId(2) val chapterId: Int,
    @SerialId(3) val name: String,
    @Optional @SerialId(4) val subTitle: String? = null,
    @SerialId(6) val startTimeStamp: Int,
    @SerialId(7) val endTimeStamp: Int
)

@Serializable
data class MangaPlusPage(@Optional @SerialId(1) val page: MangaPage? = null)

@Serializable
data class MangaPage(
    @SerialId(1) val imageUrl: String,
    @SerialId(2) val width: Int,
    @SerialId(3) val height: Int,
    @Optional @SerialId(5) val encryptionKey: String? = null
)
