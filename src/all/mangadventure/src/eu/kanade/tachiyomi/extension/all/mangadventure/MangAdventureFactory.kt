package eu.kanade.tachiyomi.extension.all.mangadventure

import eu.kanade.tachiyomi.source.SourceFactory

/** [MangAdventure] source factory. */
class MangAdventureFactory : SourceFactory {
    override fun createSources() = listOf(
        ArcRelight()
    )

    /** Arc-Relight source. */
    class ArcRelight : MangAdventure(
        "Arc-Relight", "https://arc-relight.com", arrayOf(
            "4-Koma",
            "Chaos;Head",
            "Collection",
            "Comedy",
            "Drama",
            "Jubilee",
            "Mystery",
            "Psychological",
            "Robotics;Notes",
            "Romance",
            "Sci-Fi",
            "Seinen",
            "Shounen",
            "Steins;Gate",
            "Supernatural",
            "Tragedy"
        )
    )
}
