plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Même exigence que sur le cœur : la surface publique est déclarée, pas subie.
kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "org.vander.spotify.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Aucun BuildConfig : rien de la configuration Spotify ne doit être figé à la
    // compilation de la lib. Le clientId arrive par SpotifyConfig, à l'exécution.
    buildFeatures { buildConfig = false }

    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    api(project(":spotify-core"))

    implementation(libs.kotlinx.coroutines.android)
    // Strictement ce qui est utilisé. core-ktx et annotation avaient été ajoutés par
    // réflexe ; leurs versions récentes exigent compileSdk 37 et AGP 9.1, contrainte que
    // cette lib imposerait alors à tous ses consommateurs pour rien.
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.datastore.preferences)

    // Les AAR du SDK Spotify ne sont ni redistribuables ni publiés sur Maven Central.
    // `compileOnly` : ils ne sont pas empaquetés dans notre AAR — l'application hôte les
    // fournit. Voir README.md pour l'étape d'installation.
    compileOnly(files("libs/spotify-app-remote-release-0.8.0.aar"))
    compileOnly(files("libs/spotify-auth-release-2.1.0.aar"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // La version `-core` de DataStore fonctionne sur JVM nu : le store de jetons se teste
    // sur un fichier temporaire, sans Robolectric ni émulateur.
    testImplementation(libs.androidx.datastore.preferences.core)
}
