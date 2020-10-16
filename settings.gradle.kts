include(":annotations")

include(":lib-ratelimit")
project(":lib-ratelimit").projectDir = File("lib/ratelimit")

include(":duktape-stub")
project(":duktape-stub").projectDir = File("lib/duktape-stub")

include(":lib-dataimage")
project(":lib-dataimage").projectDir = File("lib/dataimage")

File(rootDir, "src").eachDir { dir ->
    dir.eachDir { subdir ->
        val name = ":${dir.name}-${subdir.name}"
        include(name)
        project(name).projectDir = File("src/${dir.name}/${subdir.name}")
    }
}

inline fun File.eachDir(block: (File) -> Unit) {
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
