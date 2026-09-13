import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

description = "Cœur multiplateforme : domaine, client Web API Spotify et flot d'autorisation PKCE, sans dépendance Android."

kotlin {
    // Mode strict : tout membre public doit être déclaré explicitement et porter un type
    // de retour explicite. C'est le compilateur qui tient la frontière de l'API, pas la revue.
    explicitApi()

    jvm()

    // Pas de cible Android : ce module ne contient aucun code Android, un consommateur
    // Android prend l'artefact JVM. La couche App Remote vit dans :spotify-android.
    iosArm64()

    // Sans device explicite, Gradle marque la tâche de test SKIPPED : les tests sont
    // compilés mais jamais exécutés, et le build est vert pour rien. Le nom doit
    // correspondre à une entrée de `xcrun simctl list devices available`.
    iosX64 {
        testRuns["test"].deviceId = providers.gradleProperty("spotify.simulator")
            .getOrElse("iPhone 15 Pro")
    }

    iosSimulatorArm64 {
        // Sans device explicite, Gradle marque `iosSimulatorArm64Test` SKIPPED et les tests
        // sont compilés mais jamais exécutés — un vert trompeur. Le nom doit correspondre à
        // un simulateur de `xcrun simctl list devices available`.
        testRuns["test"].deviceId = providers.gradleProperty("spotify.simulator")
            .getOrElse("iPhone 15 Pro")
    }

    // Un XCFramework réunit les trois architectures iOS en un artefact unique, celui que
    // consomme un projet Xcode ou un podspec. `./gradlew :spotify-core:assembleSpotifyCoreXCFramework`
    // le produit dans build/XCFrameworks.
    val xcf = XCFramework("SpotifyCore")
    listOf(iosArm64(), iosX64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "SpotifyCore"
            // Statique : une lib distribuée par CocoaPods évite d'imposer à l'application
            // l'embarquement et la signature d'un framework dynamique.
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api` et non `implementation` : `SpotifyClient.session` rend un `StateFlow`,
            // donc les coroutines font partie de la surface publique. En `implementation`,
            // un consommateur ne peut pas nommer le type que la lib lui rend — défaut
            // invisible depuis le dépôt, où la dépendance est déjà sur le classpath, et
            // attrapé par un projet consommateur qui résout depuis mavenLocal.
            api(libs.kotlinx.coroutines.core)

            // Ktor et la sérialisation ne traversent aucune signature publique : le moteur
            // HTTP a été sorti des fabriques, les DTO sont internes.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
        jvmMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
