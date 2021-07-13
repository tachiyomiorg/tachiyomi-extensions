package eu.kanade.tachiyomi.extension.en.mangacv

import eu.kanade.tachiyomi.annotations.Nsfw
import eu.kanade.tachiyomi.multisrc.madara.Madara

@Nsfw
class MangaCV : Madara("MangaCV", "https://mangacv.com", "en") {
        
    override fun getGenreList() = listOf(
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("Adventure", "adventure"),
        Genre("Comedy", "comedy"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Drama", "drama"),
        Genre("Ecchi", "ecchi"),
        Genre("Fanstasy", "fantasy"),
        Genre("Gender bender", "gender-bender"),
        Genre("Harem", "harem"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Josei", "josei"),
        Genre("Lolicon", "lolicon"),
        Genre("Martial arts", "martial-arts"),
        Genre("Mature", "mature"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Mystery", "mystery"),
        Genre("One shot", "one-shot"),
        Genre("Psychological", "psychological"),
        Genre("Romance", "romance"),
        Genre("School Life", "school-life"),
        Genre("Sci-fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shoujo ai", "shoujo-ai"),
        Genre("Shounen", "shounen"),
        Genre("Shounen ai", "shounen-ai"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Smut", "smut"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "supernatural"),
        Genre("Tragedy", "tragedy"),
        Genre("Webtoons", "webtoons"),
        Genre("Yaoi", "yaoi"),
        Genre("Yuri", "yuri"),
    )
}
