plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

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

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
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
