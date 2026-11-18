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
        exclusiveContent {
            forRepository {
                maven("https://jitpack.io")
            }
            filter {
                includeGroup("com.github.TeamNewPipe")
                includeGroup("com.github.InfinityLoop1308.PipePipeExtractor")
            }
        }
        exclusiveContent {
            forRepository {
                maven("https://clojars.org/repo")
            }
            filter {
                includeGroupByRegex("org\\.clojars(\\..+)?")
            }
        }
    }
}

rootProject.name = "AuroraShelf"
include(":app")
includeBuild("third_party/pipepipe-extractor")
