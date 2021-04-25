package eu.kanade.tachiyomi.extension.all.mangadex

import android.app.Application
import android.content.SharedPreferences
import android.support.v7.preference.PreferenceScreen
import com.github.salomonbrys.kotson.array
import com.github.salomonbrys.kotson.get
import com.github.salomonbrys.kotson.int
import com.github.salomonbrys.kotson.nullString
import com.github.salomonbrys.kotson.obj
import com.github.salomonbrys.kotson.string
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date

/*
 * Copyright (C) 2020 The Neko Manga Open Source Project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

abstract class MangaDex(override val lang: String) : ConfigurableSource, HttpSource() {
    override val name = "MangaDex"
    override val baseUrl = "https://www.mangadex.org"
    val apiUrl = "http://api.mangadex.org.dev.mdcloud.moe"

    //maybe after mvp comes out
    override val supportsLatest = false

    private val helper = MangaDexHelper()

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Tachiyomi " + System.getProperty("http.agent"))
    }

    override val client =
        network.client.newBuilder().addNetworkInterceptor(mdRateLimitInterceptor).addInterceptor(
            coverInterceptor
        ).addInterceptor(MdAtHomeReportInterceptor(network.client, headersBuilder().build()))
            .build()

    //POPULAR Manga Section

    override fun popularMangaRequest(page: Int): Request {
        return GET(
            "$apiUrl/manga/?limit=25&offset=${page * 25}",
            headers,
            CacheControl.FORCE_NETWORK
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        if (response.isSuccessful.not()) {
            throw Exception("Error getting popular manga http code: ${response.code()}")
        }

        val mangaListResponse = JsonParser().parse(response.body()!!.string()).obj
        val hasMoreResults =
            (mangaListResponse["limit"].int + mangaListResponse["offset"].int) < mangaListResponse["total"].int

        val mangaList = mangaListResponse["results"].array.map { createManga(it) }
        return MangasPage(mangaList, hasMoreResults)
    }

    // LATEST section  API can't sort by date yet so not implemented
    override fun latestUpdatesParse(response: Response): MangasPage = throw Exception("Not used")

    override fun latestUpdatesRequest(page: Int): Request = throw Exception("Not used")

    // SEARCH section

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        TODO("Not yet implemented")
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    //Manga Details section
    /**
     * get manga details url throws exception if the url is the old format so people migrate
     */
    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!helper.containsUuid(manga.url)) {
            throw Exception("Old manga id format, please migrate entry to MangaDex")
        }
        return GET("$apiUrl/${manga.url}", headers, CacheControl.FORCE_NETWORK)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val manga = JsonParser().parse(response.body()!!.string()).obj
        return createManga(manga)
    }

    //Chapter list section
    /**
     * get chapter list if manga url is old format throws exception
     */
    override fun chapterListRequest(manga: SManga): Request {
        if (!helper.containsUuid(manga.url)) {
            throw Exception("Old manga id format, please migrate entry to MangaDex")
        }
        return actualChapterListRequest(helper.getUUIDFromUrl(manga.url), 0)
    }

    /**
     * Required because api is paged
     */
    fun actualChapterListRequest(mangaId: String, offset: Int) =
        GET(
            url = apiUrl + helper.getChapterEndpoint(mangaId, offset, lang),
            headers = headers,
            cache = CacheControl.FORCE_NETWORK
        )

    override fun chapterListParse(response: Response): List<SChapter> {
        if (response.isSuccessful.not()) {
            throw Exception("Error getting chapter list http code: ${response.code()}")
        }
        val chapterListResponse = JsonParser().parse(response.body()!!.string()).obj

        val chapterListResults = chapterListResponse["results"].asJsonArray

        val mangaId =
            response.request().url().toString().substringBefore("/feed").substringAfter("/manga/")

        val limit = chapterListResponse["limit"].int

        var offset = chapterListResponse["offset"].int

        var hasMoreResults = (limit + offset) < chapterListResponse["total"].int


        while (hasMoreResults) {
            offset += limit
            val newResponse = client.newCall(actualChapterListRequest(mangaId, offset)).execute()
            val newChapterListJson = JsonParser().parse(newResponse.body()!!.string()).obj
            chapterListResults.addAll(newChapterListJson["results"].asJsonArray)
            hasMoreResults = (limit + offset) < newChapterListJson["total"].int
        }

        val groupIds =
            chapterListResults.map { it["data"].array["groups"].array }.flatten()
                .map { it.string }.distinct()

        // ignore errors if request fails, there is no batch group search yet..
        val groupMap = runCatching {
            groupIds.mapNotNull { groupId ->
                val response = client.newCall(GET("$apiUrl/group/$groupId")).execute()
                val name = when (response.isSuccessful) {
                    true -> {
                        JsonParser().parse(response.body()!!.string())
                            .obj["data"]["attributes"]["name"].nullString
                    }
                    false -> null
                }

                when (name == null) {
                    true -> null
                    false -> Pair(groupId, helper.cleanString(name))
                }

            }.toMap()
        }?.getOrNull() ?: emptyMap()

        val now = Date().time
        return chapterListResults.map { createChapter(it, groupMap) }
            .filter { it.date_upload <= now && "Manga Plus" != it.scanlator }
    }

    override fun pageListParse(response: Response): List<Page> {
        TODO("Not yet implemented")
    }

    override fun imageUrlParse(response: Response): String {
        TODO("Not yet implemented")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        TODO("Not yet implemented")
    }

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        TODO("Not yet implemented")
    }

    /**
     * Create an SManga from json element
     */
    fun createManga(mangaJson: JsonElement): SManga {
        val data = mangaJson["data"].obj
        val dexId = data["id"].string
        val attr = data["attributes"].obj

        val nonGenres = listOf(
            attr["contentRating"].nullString,
            attr["originalLanguage"]?.nullString,
            attr["publicationDemographic"]?.nullString
        )

        val authorIds = data["relationships"].array.filter { relationship ->
            relationship["type"].string.equals("author", true)
        }.map { relationship -> relationship["id"].string }
            .distinct()

        //get authors ignore if they error, artists are labelled as authors currently
        val authors = runCatching {
            authorIds.mapNotNull { id ->
                val response = client.newCall(GET("$apiUrl/author/$id")).execute()
                when (response.isSuccessful) {
                    true -> {
                        JsonParser().parse(response.body()!!.string())
                            .obj["data"]["attributes"]["name"].nullString
                    }
                    false -> null
                }
            }.map { helper.cleanString(it) }

        }.getOrNull() ?: emptyList()

        val genreList =
            (nonGenres + attr["tags"].array.map { tag -> tag["id"].string }.map { dexTag ->
                helper.tags.firstOrNull { tag -> tag.name.equals(dexTag, true) }
            }.map { it?.name }).filterNotNull()


        return SManga.create().apply {
            url = "/manga/$dexId"
            title = helper.cleanString(attr["title"]["en"].string)
            description = helper.cleanString(attr["description"]["??"].string)
            author = authors.joinToString(", ")
            status = helper.getPublicationStatus(attr["publicationDemographic"].nullString)
            thumbnail_url = ""
            genre = genreList.joinToString(", ")
        }
    }

    fun createChapter(chapterJsonResponse: JsonElement, groupMap: Map<String, String>): SChapter {

        val data = chapterJsonResponse["data"].obj
        val attr = data["attributes"]

        val chapterName = mutableListOf<String>()
        // Build chapter name

        attr["volume"].nullString?.let {
            chapterName.add("Vol.$it")
        }

        attr["chapter"].nullString?.let {
            chapterName.add("Ch.$it")
        }

        attr["title"].nullString?.let {
            if (chapterName.isNotEmpty()) {
                chapterName.add("-")
            }
            chapterName.add(it)
        }
        // if volume, chapter and title is empty its a oneshot
        if (chapterName.isEmpty()) {
            chapterName.add("Oneshot")
        }
        //In future calculate [END] if non mvp api doesnt provide it

        return SChapter.create().apply {
            name = helper.cleanString(chapterName.joinToString(" "))
            date_upload = 0L //helper.parseDate(attr["publishAt"].string)
            scanlator =
                attr["groups"].array.mapNotNull { groupMap[it.string] }.joinToString { " & " }
        }
    }
}
