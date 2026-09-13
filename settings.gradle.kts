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
    }
}

rootProject.name = "spotify-sdk"

include(":spotify-core")
include(":spotify-android")
include(":spotify-testing")
include(":spotify-android-testing")
include(":spotify-android-hilt")
include(":spotify-android-koin")
