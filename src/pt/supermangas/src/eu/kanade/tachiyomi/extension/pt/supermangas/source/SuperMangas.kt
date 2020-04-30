package eu.kanade.tachiyomi.extension.pt.supermangas.source

import eu.kanade.tachiyomi.extension.pt.supermangas.SuperMangasGeneric
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import okhttp3.FormBody

class SuperMangas : SuperMangasGeneric(
    "Super Mangás",
    "https://supermangas.site"
) {
    override val contentList = listOf(
        Triple("100", "", "Todos"),
        Triple("6", "", "Mangá"),
        Triple("8", "", "Light Novel"),
        Triple("9", "", "Manhwa"),
        Triple("10", "", "Manhua")
    )

    override fun chapterListPaginatedBody(idCategory: Int, page: Int, totalPage: Int): FormBody.Builder =
        super.chapterListPaginatedBody(idCategory, page, totalPage)
            .add("order_audio", "pt-br")

    override fun getFilterList() = FilterList(
        Filter.Header("Filtros abaixo são ignorados na busca!"),
        ContentFilter(contentList),
        LetterFilter(),
        StatusFilter(),
        SortFilter(),
        GenreFilter(getGenreList()),
        ExclusiveModeFilter()
    )

    override fun getGenreList(): List<Tag> = listOf(
        Tag("1", "Ação"),
        Tag("81", "Action"),
        Tag("20", "Artes marciais"),
        Tag("4", "Aventura"),
        Tag("17", "Bishoujo"),
        Tag("16", "Bishounen"),
        Tag("78", "Cars"),
        Tag("21", "Comédia"),
        Tag("27", "Comédia romântica"),
        Tag("73", "Crianças"),
        Tag("79", "Dementia"),
        Tag("49", "Demônio"),
        Tag("76", "Doujinshi"),
        Tag("22", "Drama"),
        Tag("12", "Ecchi"),
        Tag("47", "Espaço"),
        Tag("23", "Esporte"),
        Tag("24", "Fantasia"),
        Tag("18", "Faroeste"),
        Tag("30", "Ficção científica"),
        Tag("68", "Food"),
        Tag("72", "Gender Bender"),
        Tag("25", "Harém‎"),
        Tag("48", "Histórico"),
        Tag("50", "Horror"),
        Tag("75", "Isekai"),
        Tag("34", "Jogos"),
        Tag("9", "Josei"),
        Tag("80", "Juventude"),
        Tag("82", "Kaiju"),
        Tag("83", "KBS2"),
        Tag("10", "Kodomo"),
        Tag("43", "Live action"),
        Tag("40", "Magia"),
        Tag("74", "Mature"),
        Tag("11", "Mecha"),
        Tag("46", "Militar"),
        Tag("32", "Mistério"),
        Tag("37", "Musical"),
        Tag("45", "Paródia"),
        Tag("38", "Policial"),
        Tag("44", "Psicológico"),
        Tag("3", "Romance"),
        Tag("39", "Samurai"),
        Tag("8", "Seinen"),
        Tag("5", "Shoujo"),
        Tag("7", "Shoujo-ai"),
        Tag("31", "Shounen"),
        Tag("6", "Shounen-ai"),
        Tag("41", "Slice of life"),
        Tag("28", "Sobrenatural"),
        Tag("77", "Super Herói"),
        Tag("35", "Superpoder"),
        Tag("26", "Suspense"),
        Tag("71", "Teatro"),
        Tag("2", "Terror"),
        Tag("33", "Thriller"),
        Tag("42", "Tokusatsu"),
        Tag("29", "Vampiros"),
        Tag("19", "Vida escolar"),
        Tag("36", "Visual Novels"),
        Tag("70", "War"),
        Tag("15", "Yuri")
    )
}
