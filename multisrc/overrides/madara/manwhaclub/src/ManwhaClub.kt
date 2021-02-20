package eu.kanade.tachiyomi.extension.en.manwhaclub

import eu.kanade.tachiyomi.multisrc.madara.Madara

class ManwhaClub : Madara("Manhwa.club", "https://manhwa.club", "en") {
    override val id = 6951399865568003192
    override fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Crime", "crime"),
        Genre("Drama", "drama"),
        Genre("Fantasy", "fantasy"),
        Genre("Gender bender", "gender-bender"),
        Genre("Gossip", "gossip"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Incest", "incest"),
        Genre("Isekai", "isekai"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Monster/Tentacle", "monster-tentacle"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Psychological", "psychological"),
        Genre("Revenge", "revenge"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
}
