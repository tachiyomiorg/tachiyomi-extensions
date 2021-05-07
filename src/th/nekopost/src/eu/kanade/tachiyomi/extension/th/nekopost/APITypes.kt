package eu.kanade.tachiyomi.extension.th.nekopost

data class RawMangaData(
    val no_new_chapter: String,
    val nc_chapter_id: String,
    val np_project_id: String,
    val np_name: String,
    val np_name_link: String,
    val nc_chapter_no: String,
    val nc_chapter_name: String,
    val nc_chapter_cover: String,
    val nc_provider: String,
    val np_group_dir: String,
    val nc_created_date: String,
)

data class RawMangaDataList(
    val code: String,
    val listItem: Array<RawMangaData>?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawMangaDataList

        if (code != other.code) return false
        if (listItem != null) {
            if (other.listItem == null) return false
            if (!listItem.contentEquals(other.listItem)) return false
        } else if (other.listItem != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + (listItem?.contentHashCode() ?: 0)
        return result
    }
}

data class RawProjectData(
    val np_status: String,
    val np_project_id: String,
    val np_type: String,
    val np_name: String,
    val np_name_link: String,
    val np_flag_mature: String,
    val np_info: String,
    val np_view: String,
    val np_comment: String,
    val np_created_date: String,
    val np_updated_date: String,
    val author_name: String,
    val artist_name: String,
    val np_web: String,
    val np_licenced_by: String,
)

data class RawProjectGenre(
    val npc_name: String,
    val npc_name_link: String,
)

data class RawChapterData(
    val nc_chapter_id: String,
    val nc_chapter_no: String,
    val nc_chapter_name: String,
    val nc_provider: String,
    val cu_displayname: String,
    val nc_created_date: String,
    val nc_data_file: String,
    val nc_owner_id: String,
)

data class RawMangaDetailedData(
    val code: String,
    val projectInfo: RawProjectData,
    val projectCategoryUsed: Array<RawProjectGenre>?,
    val projectChapterList: Array<RawChapterData>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawMangaDetailedData

        if (code != other.code) return false
        if (projectInfo != other.projectInfo) return false
        if (projectCategoryUsed != null) {
            if (other.projectCategoryUsed == null) return false
            if (!projectCategoryUsed.contentEquals(other.projectCategoryUsed)) return false
        } else if (other.projectCategoryUsed != null) return false
        if (!projectChapterList.contentEquals(other.projectChapterList)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + projectInfo.hashCode()
        result = 31 * result + (projectCategoryUsed?.contentHashCode() ?: 0)
        result = 31 * result + projectChapterList.contentHashCode()
        return result
    }
}

data class RawPageData(
    val pageNo: Int,
    val fileName: String,
    val width: Int,
    val height: Int,
    val pageCount: Int
)

data class RawChapterDetailedData(
    val projectId: String,
    val chapterId: Int,
    val chapterNo: String,
    val pageItem: Array<RawPageData>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RawChapterDetailedData

        if (projectId != other.projectId) return false
        if (chapterId != other.chapterId) return false
        if (chapterNo != other.chapterNo) return false
        if (!pageItem.contentEquals(other.pageItem)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = projectId.hashCode()
        result = 31 * result + chapterId
        result = 31 * result + chapterNo.hashCode()
        result = 31 * result + pageItem.contentHashCode()
        return result
    }
}

data class MangaNameList(
    val np_project_id: String,
    val np_name: String,
    val np_name_link: String,
    val np_type: String,
    val np_status: String,
    val np_no_chapter: String,
)
