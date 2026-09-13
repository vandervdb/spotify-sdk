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

// Module de vérification : le paquet React Native se livre par npm, pas par Maven.
// L'inclure ici permet de compiler l'adaptateur Kotlin contre le spec généré.
include(":sample-android")
include(":spotify-rn-android")
project(":spotify-rn-android").projectDir = file("spotify-rn/android")
