plugins {
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}

// The project lives under macOS Documents, where cloud file providers can duplicate
// Gradle's rapidly changing class files. A .nosync output root prevents corrupt dex builds.
layout.buildDirectory = layout.projectDirectory.dir("build.nosync/root")
subprojects {
    layout.buildDirectory = rootProject.layout.projectDirectory.dir("build.nosync/${project.name}")
}
