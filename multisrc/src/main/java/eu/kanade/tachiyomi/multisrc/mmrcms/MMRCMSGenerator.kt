package eu.kanade.tachiyomi.multisrc.mmrcms

import generator.ThemeSourceData
import generator.ThemeSourceData.SingleLang
import generator.ThemeSourceGenerator

class MMRCMSGenerator : ThemeSourceGenerator {

    override val themePkg = "mmrcms"

    override val themeClass = "MMRCMS"

    override val baseVersionCode: Int = MMRCMSSources.version

    override val sources = MMRCMSSources.sourceList.map {
        SingleLang(it.name, it.baseUrl, it.lang, it.isNsfw, it.className, it.pkgName, it.overrideVersionCode)
    }

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            MMRCMSGenerator().createAll()
        }
    }
}
