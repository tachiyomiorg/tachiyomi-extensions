package eu.kanade.tachiyomi.extension.en.keenspot

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

/**
 *  @author Aria Moradi <aria.moradi007@gmail.com>
 */

class KeenspotFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(TwoKinds())
}
