package eu.kanade.tachiyomi.extension.es.vcpvmp

import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory

class VCPVMPFactory : SourceFactory {
    override fun createSources(): List<Source> = listOf(
        VCP(),
        VMP()
    )
}

class VCP : VCPVMP("VCP", "https://vercomicsporno.com")

class VMP : VCPVMP("VMP", "https://vermangasporno.com")
