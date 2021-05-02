plugins {
    java
}

sourceSets {
    main {
        java {
            srcDirs(listOf("src"))
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
