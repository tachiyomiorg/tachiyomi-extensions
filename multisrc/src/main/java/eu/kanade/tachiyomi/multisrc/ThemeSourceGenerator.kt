package eu.kanade.tachiyomi.multisrc

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * This is meant to be used in place of a factory extension, specifically for what would be a multi-source extension.
 * A multi-lang (but not multi-source) extension should still be made as a factory extensiion.
 * Use a generator for initial setup of a theme source or when all of the inheritors need a version bump.
 * Source list (val sources) should be kept up to date.
 */
interface ThemeSourceGenerator {
    /**
     * The class that the sources inherit from.
     */
    val themeClass: String

    /**
     * The package that contains themeClass.
     */
    val themePkg: String


    /**
     * Base theme version, starts with 1 and should be increased when based theme class changes
     */
    val baseVersionCode: Int

    /**
     * The list of sources to be created or updated.
     */
    val sources: List<ThemeSourceData>

    fun createAll() {
        val userDir = System.getProperty("user.dir")!!

        sources.forEach { source ->
            createGradleProject(source, themePkg, themeClass, baseVersionCode, userDir)
        }
    }

    companion object {
        private fun pkgNameSuffix(source: ThemeSourceData, separator: String): String {
            return if (source is ThemeSourceData.SingleLang)
                listOf(source.lang.substringBefore("-"), source.pkgName).joinToString(separator)
            else
                listOf("all", source.pkgName).joinToString(separator)
        }

        private fun themeSuffix(themePkg: String, separator: String): String {
            return listOf("eu", "kanade", "tachiyomi", "multisrc", themePkg).joinToString(separator)
        }

        private fun writeGradle(gradle: File, source: ThemeSourceData, baseVersionCode: Int) {
            gradle.writeText("""apply plugin: 'com.android.application'
apply plugin: 'kotlin-android'

ext {
    extName = '${source.name}'
    pkgNameSuffix = '${pkgNameSuffix(source, ".")}'
    extClass = '.${source.className}'
    extVersionCode = ${baseVersionCode + source.overrideVersionCode + multisrcLibraryVersion}
    libVersion = '1.2'
${if (source.isNsfw) "    containsNsfw = true\n" else ""}}

apply from: "${'$'}rootDir/common.gradle"
"""
            )
        }

        private fun writeAndroidManifest(androidManifestFile: File) {
            androidManifestFile.writeText(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                    "<manifest package=\"eu.kanade.tachiyomi.extension\" />\n"
            )
        }

        /**
         * Clears directory recursively
         */
        private fun purgeDirectory(dir: File) {
            for (file in dir.listFiles()!!) {
                if (file.isDirectory) purgeDirectory(file)
                file.delete()
            }
        }

        fun createGradleProject(source: ThemeSourceData, themePkg: String, themeClass: String, baseVersionCode: Int, userDir: String) {
            val projectRootPath = "$userDir/generated-src/${pkgNameSuffix(source, "/")}"
            val projectSrcPath = "$projectRootPath/src/eu/kanade/tachiyomi/extension/${pkgNameSuffix(source, "/")}"
            val overridesPath = "$userDir/multisrc/overrides" // userDir = tachiyomi-extensions project root path
            val resOverridesPath = "$overridesPath/res/$themePkg"
            val srcOverridesPath = "$overridesPath/src/$themePkg"
            val projectGradleFile = File("$projectRootPath/build.gradle")
            val projectAndroidManifestFile = File("$projectRootPath/AndroidManifest.xml")


            File(projectRootPath).let { projectRootFile ->
                println("Working on $source")

                projectRootFile.mkdirs()
                // remove everything from past runs
                purgeDirectory(projectRootFile)

                writeGradle(projectGradleFile, source, baseVersionCode)
                writeAndroidManifest(projectAndroidManifestFile)

                writeSourceFiles(projectSrcPath, srcOverridesPath, source, themePkg, themeClass)
                copyThemeClasses(userDir, themePkg, projectRootPath)

                copyResFiles(resOverridesPath, source, projectRootPath)
            }
        }

        private fun copyThemeClasses(userDir: String, themePkg: String, projectRootPath: String) {
            val themeSrcPath = "$userDir/multisrc/src/main/java/${themeSuffix(themePkg, "/")}"
            val themeSrcFile = File(themeSrcPath)
            val themeDestPath = "$projectRootPath/src/${themeSuffix(themePkg, "/")}"
            val themeDestFile = File(themeDestPath)

            themeDestFile.mkdirs()

            themeSrcFile.list()!!
                .filter { it.endsWith(".kt") && !it.endsWith("Generator.kt") }
                .forEach { Files.copy(File("$themeSrcPath/$it").toPath(), File("$themeDestPath/$it").toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }

        private fun copyResFiles(resOverridesPath: String, source: ThemeSourceData, projectRootPath: String): Any {
            // check if res override exists if not copy default res
            val resOverride = File("$resOverridesPath/${source.pkgName}")
            return if (resOverride.exists())
                resOverride.copyRecursively(File("$projectRootPath/res"))
            else
                File("$resOverridesPath/default").let { res ->
                    if (res.exists()) res.copyRecursively(File("$projectRootPath/res"))
                }
        }

        private fun writeSourceFiles(projectSrcPath: String, srcOverridePath: String, source: ThemeSourceData, themePkg: String, themeClass: String) {
            val projectSrcFile = File(projectSrcPath)
            projectSrcFile.mkdirs()
            val srcOverride = File("$srcOverridePath/${source.pkgName}")
            if (srcOverride.exists())
                srcOverride.copyRecursively(projectSrcFile)
            else
                writeSourceClass(projectSrcFile, source, themePkg, themeClass)
        }

        private fun writeSourceClass(classPath: File, source: ThemeSourceData, themePkg: String, themeClass: String) {
            fun factoryClassText(): String {
                val sourceListString =
                    (source as ThemeSourceData.MultiLang).lang.map {
                        "        $themeClass(\"${source.name}\", \"${source.baseUrl}\", \"$it\"),"
                    }.joinToString("\n")

                return """class ${source.className} : SourceFactory {
    override fun createSources(): List<Source> = listOf(
$sourceListString
    )
}"""
            }
            File("$classPath/${source.className}.kt").writeText(
                """package eu.kanade.tachiyomi.extension.${pkgNameSuffix(source, ".")}
${if (source.isNsfw) "\nimport eu.kanade.tachiyomi.annotations.Nsfw" else ""}
import eu.kanade.tachiyomi.multisrc.$themePkg.$themeClass
${if (source is ThemeSourceData.MultiLang)
                    """import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceFactory
                    """
                else ""}${if (source.isNsfw) "\n@Nsfw" else ""}
${if (source is ThemeSourceData.SingleLang) {
                    "class ${source.className} : $themeClass(\"${source.name}\", \"${source.baseUrl}\", \"${source.lang}\")\n"
                } else
                    factoryClassText()
                }
""")
        }

        sealed class ThemeSourceData {
            abstract val name: String
            abstract val baseUrl: String
            abstract val isNsfw: Boolean
            abstract val className: String
            abstract val pkgName: String

            /**
             * overrideVersionCode defaults to 0, if a source changes their source override code or
             * a previous existing source suddenly needs source code overrides, overrideVersionCode
             * should be increased.
             * When a new source is added with overrides, overrideVersionCode should still be set to 0
             *
             * Note: source code overrides are located in "multisrc/overrides/src/<themeName>/<sourceName>"
             */
            abstract val overrideVersionCode: Int

            data class SingleLang(
                override val name: String,
                override val baseUrl: String,
                val lang: String,
                override val isNsfw: Boolean = false,
                override val className: String = name.replace(" ", ""),
                override val pkgName: String = className.toLowerCase(Locale.ENGLISH),
                override val overrideVersionCode: Int = 0,
            ) : ThemeSourceData()

            data class MultiLang(
                override val name: String,
                override val baseUrl: String,
                val lang: List<String>,
                override val isNsfw: Boolean = false,
                override val className: String = name.replace(" ", "") + "Factory",
                override val pkgName: String = className.substringBefore("Factory").toLowerCase(Locale.ENGLISH),
                override val overrideVersionCode: Int = 0,
            ) : ThemeSourceData()
        }
    }
}


/**
 * This variable should be increased when the multisrc library changes in a way that prompts global extension upgrade
 */
const val multisrcLibraryVersion = 0
