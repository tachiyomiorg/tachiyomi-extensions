package eu.kanade.tachiyomi.extension.en.mangakik

import eu.kanade.tachiyomi.multisrc.madara.Madara

class Mangakik : Madara("Mangakik", "https://mangakik.com", "en") {
    override fun getGenreList() = listOf(
        Genre("Action", "read-action-manga-or-free"),
        Genre("Adult", "adullt"),
        Genre("Adventure", "read-adventure-manga"),
        Genre("Comedy", "comedy"),
        Genre("Comics", "comics"),
        Genre("Completed", "completed"),
        Genre("Cooking", "cooking"),
        Genre("Crime", "crime"),
        Genre("Drama", "read-drama-manga"),
        Genre("Ecchi", "ecchi"),
        Genre("Fantasy", "read-fantasy-manga-for-free"),
        Genre("Harem", "read-harem-manga"),
        Genre("Historical", "historical"),
        Genre("Horror", "horror"),
        Genre("Isekai", "read-isekai-manga-online-for-free"),
        Genre("Magic", "magic"),
        Genre("Manga", "manga"),
        Genre("Manhua", "manhua"),
        Genre("Manhwa", "manhwa"),
        Genre("Martial Arts", "read-martial-arts-manga-for-free"),
        Genre("Mecha", "mecha"),
        Genre("Medical", "medical"),
        Genre("Mystery", "mystery"),
        Genre("Post Apocalyptic", "post-apocalyptic"),
        Genre("Psychological", "psychological"),
        Genre("Reincarnation", "read-reincarnation-manga-for-free"),
        Genre("Romance", "read-romance-manga"),
        Genre("School Life", "school-life"),
        Genre("Sci Fi", "sci-fi"),
        Genre("Seinen", "seinen"),
        Genre("Shoujo", "shoujo"),
        Genre("Shounen", "shounen"),
        Genre("Slice of Life", "slice-of-life"),
        Genre("Sports", "sports"),
        Genre("Supernatural", "read-supernatural-manga-for-free"),
        Genre("Thriller", "thriller"),
        Genre("Tragedy", "tragedy"),
        Genre("Webtoon", "read-webtoon-manga"),
        Genre("Zombies", "zombies")
    )
}
