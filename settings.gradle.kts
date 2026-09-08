pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://clojars.org/repo")
    }
}

rootProject.name = "AuroraShelf"
include(":app")
includeBuild("third_party/pipepipe-extractor")
