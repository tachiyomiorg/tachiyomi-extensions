package eu.kanade.tachiyomi.extension.zh.dmzj.protobuf

/*
 *  Created by reference to https://github.com/xiaoyaocz/dmzj_flutter/blob/23b04c2af930cb7c18a74665e8ec0bf1ccc6f09b/lib/protobuf/comic/detail_response.proto
 *  All credit goes to their outstanding work.
 */

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ComicDetailResponse(
    @ProtoNumber(1) val Errno: Int = 0,
    @ProtoNumber(2) val Errmsg: String = "",
    @ProtoNumber(3) val Data: ComicDetailInfoResponse,
)

@Serializable
data class ComicDetailInfoResponse(
    @ProtoNumber(1) val Id: Int,
    @ProtoNumber(2) val Title: String,
    @ProtoNumber(3) val Direction: Int? = null,
    @ProtoNumber(4) val Islong: Int? = null,
    @ProtoNumber(5) val IsDmzj: Int? = null,
    @ProtoNumber(6) val Cover: String,
    @ProtoNumber(7) val Description: String,
    @ProtoNumber(8) val LastUpdatetime: Long,
    @ProtoNumber(9) val LastUpdateChapterName: String,
    @ProtoNumber(10) val Copyright: Int? = null,
    @ProtoNumber(11) val FirstLetter: String? = null,
    @ProtoNumber(12) val ComicPy: String? = null,
    @ProtoNumber(13) val Hidden: Int? = null,
    @ProtoNumber(14) val HotNum: Int? = null,
    @ProtoNumber(15) val HitNum: Int? = null,
    @ProtoNumber(16) val Uid: Int? = null,
    @ProtoNumber(17) val IsLock: Int? = null,
    @ProtoNumber(18) val LastUpdateChapterId: Int,
    @ProtoNumber(19) val TypesTypes: List<ComicDetailTypeItemResponse> = emptyList(),
    @ProtoNumber(20) val Status: List<ComicDetailTypeItemResponse> = emptyList(),
    @ProtoNumber(21) val Authors: List<ComicDetailTypeItemResponse> = emptyList(),
    @ProtoNumber(22) val SubscribeNum: Int? = null,
    @ProtoNumber(23) val Chapters: List<ComicDetailChapterResponse> = emptyList(),
    @ProtoNumber(24) val IsNeedLogin: Int? = null,
    @ProtoNumber(26) val IsHideChapter: Int? = null,
)

@Serializable
data class ComicDetailTypeItemResponse(
    @ProtoNumber(1) val TagId: Int,
    @ProtoNumber(2) val TagName: String,
)

@Serializable
data class ComicDetailChapterResponse(
    @ProtoNumber(1) val Title: String,
    @ProtoNumber(2) val Data: List<ComicDetailChapterInfoResponse> = emptyList(),
)

@Serializable
data class ComicDetailChapterInfoResponse(
    @ProtoNumber(1) val ChapterId: Int,
    @ProtoNumber(2) val ChapterTitle: String,
    @ProtoNumber(3) val Updatetime: Long,
    @ProtoNumber(4) val Filesize: Int,
    @ProtoNumber(5) val ChapterOrder: Int,
)
