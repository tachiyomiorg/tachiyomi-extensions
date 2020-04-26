package eu.kanade.tachiyomi.extension.pt.supermangas.source

import eu.kanade.tachiyomi.extension.pt.supermangas.SuperMangasGeneric
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.Request

class SuperHentais : SuperMangasGeneric(
    "Super Hentais",
    "https://superhentais.com",
    "hentai-manga"
) {
    // Hardcode the id because the language wasn't specific.
    override val id: Long = 6434607482102119984

    override val defaultFilter: MutableMap<String, String> =
        super.defaultFilter.apply { this["filter_type_content"] = "5" }

    override val contentList = listOf(
        Triple("5", "hentai-manga", "Hentai Manga"),
        Triple("6", "hq-ero", "HQ Ero"),
        Triple("7", "parody-hentai-manga", "Parody Manga"),
        Triple("8", "parody-hq-ero", "Parody HQ"),
        Triple("9", "doujinshi", "Doujinshi"),
        Triple("10", "manhwa-ero", "Manhwa")
    )

    override fun searchMangaWithQueryRequest(query: String): Request {
        val searchUrl = HttpUrl.parse("$baseUrl/busca")!!.newBuilder()
            .addEncodedQueryParameter("parametro", query)
            .addQueryParameter("search_type", "serie")
            .toString()

        return GET(searchUrl, headers)
    }

    override fun searchMangaSelector(): String = "article.box_view.list div.grid_box:contains(Hentai Manga) div.grid_image.grid_image_vertical a"

    override fun chapterListPaginatedBody(idCategory: Int, page: Int, totalPage: Int): FormBody.Builder =
        super.chapterListPaginatedBody(idCategory, page, totalPage)
            .add("type", "book")

    override fun getFilterList() = FilterList(
        Filter.Header("Filtros abaixo são ignorados na busca!"),
        ContentFilter(contentList),
        LetterFilter(),
        StatusFilter(),
        CensureFilter(),
        SortFilter(),
        GenreFilter(getGenreList()),
        ExclusiveModeFilter()
    )

    override fun getGenreList(): List<Tag> = listOf(
        Tag("33", "Ação"),
        Tag("100", "Ahegao"),
        Tag("64", "Anal"),
        Tag("7", "Artes Marciais"),
        Tag("134", "Ashikoki"),
        Tag("233", "Aventura"),
        Tag("57", "Bara"),
        Tag("3", "BDSM"),
        Tag("267", "Big Ass"),
        Tag("266", "Big Cock"),
        Tag("268", "Blowjob"),
        Tag("88", "Boquete"),
        Tag("95", "Brinquedos"),
        Tag("156", "Bukkake"),
        Tag("120", "Chikan"),
        Tag("68", "Comédia"),
        Tag("140", "Cosplay"),
        Tag("265", "Creampie"),
        Tag("241", "Dark Skin"),
        Tag("212", "Demônio"),
        Tag("169", "Drama"),
        Tag("144", "Dupla Penetração"),
        Tag("184", "Enfermeira"),
        Tag("126", "Eroge"),
        Tag("160", "Esporte"),
        Tag("245", "Facial"),
        Tag("30", "Fantasia"),
        Tag("251", "Femdom"),
        Tag("225", "Ficção Científica"),
        Tag("273", "FootJob"),
        Tag("270", "Forçado"),
        Tag("51", "Futanari"),
        Tag("106", "Gang Bang"),
        Tag("240", "Gender Bender"),
        Tag("67", "Gerakuro"),
        Tag("254", "Gokkun"),
        Tag("236", "Golden Shower"),
        Tag("204", "Gore"),
        Tag("234", "Grávida"),
        Tag("148", "Grupo"),
        Tag("2", "Gyaru"),
        Tag("17", "Harém"),
        Tag("8", "Histórico"),
        Tag("250", "Horror"),
        Tag("27", "Incesto"),
        Tag("61", "Jogos Eróticos"),
        Tag("5", "Josei"),
        Tag("48", "Kemono"),
        Tag("98", "Kemonomimi"),
        Tag("252", "Lactação"),
        Tag("177", "Magia"),
        Tag("92", "Maid"),
        Tag("110", "Masturbação"),
        Tag("162", "Mecha"),
        Tag("243", "Menage"),
        Tag("154", "Milf"),
        Tag("115", "Mind Break"),
        Tag("248", "Mind Control"),
        Tag("238", "Mistério"),
        Tag("112", "Moe"),
        Tag("200", "Monstros"),
        Tag("79", "Nakadashi"),
        Tag("46", "Netorare"),
        Tag("272", "Óculos"),
        Tag("1", "Oral"),
        Tag("77", "Paizuri"),
        Tag("237", "Paródia"),
        Tag("59", "Peitões"),
        Tag("274", "Pelos Pubianos"),
        Tag("72", "Pettanko"),
        Tag("36", "Policial"),
        Tag("192", "Professora"),
        Tag("4", "Psicológico"),
        Tag("152", "Punição"),
        Tag("242", "Raio-X"),
        Tag("45", "Romance"),
        Tag("253", "Seinen"),
        Tag("271", "Sex Toys"),
        Tag("93", "Sexo Público"),
        Tag("55", "Shotacon"),
        Tag("9", "Shoujo Ai"),
        Tag("13", "Shounen ai"),
        Tag("239", "Slice of Life"),
        Tag("25", "Sobrenatural"),
        Tag("96", "Superpoder"),
        Tag("158", "Tentáculos"),
        Tag("31", "Terror"),
        Tag("249", "Thriller"),
        Tag("217", "Vampiros"),
        Tag("84", "Vanilla"),
        Tag("23", "Vida Escolar"),
        Tag("40", "Virgem"),
        Tag("247", "Voyeur"),
        Tag("6", "Yaoi"),
        Tag("10", "Yuri")
    )
}
