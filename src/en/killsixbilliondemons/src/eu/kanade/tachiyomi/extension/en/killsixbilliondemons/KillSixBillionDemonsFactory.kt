package eu.kanade.tachiyomi.extension.en.killsixbilliondemons

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

/**
 *  @author Aria Moradi <aria.moradi007@gmail.com>
 */

class KillSixBillionDemonsFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        KillSixBillionDemons(),
        KillSixBillionDemonsWithFlavourText()
    )
}
