package eu.kanade.tachiyomi.lib.themesources

import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.ThemeSourceData
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.SingleLangThemeSourceData
import eu.kanade.tachiyomi.lib.themesources.ThemeSourceGenerator.Companion.createOrUpdateSource

/**
 * Meant to be used when creating a single new source for an existing theme source.
 */
class CreateInheritor {
    companion object {
        /**
         * Name of the class/theme we're inheriting from.
         */
        private const val themeName: String = ""

        /**
         * Information about the source being created.
         * Note that ThemeSourceData has optional properties
         * className will be the inheritor's class/file name and is based off the name property, override if that's inappropriate
         * isNsfw is defaulted to false, can be set to true if the source should be annotated @NSFW
         */
        private val source: ThemeSourceData = SingleLangThemeSourceData("", "", "")

        @JvmStatic
        fun main(args: Array<String>) {
            createOrUpdateSource(source, themeName, System.getProperty("user.dir")!!)
        }
    }
}
