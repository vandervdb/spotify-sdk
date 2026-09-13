plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "Couche Android : contrôle de lecture via l'App Remote, autorisation par le SDK Spotify, jeton en DataStore."

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
    // Onglets personnalisés : le flot d'autorisation est le nôtre, pas celui du SDK Spotify.
    implementation(libs.androidx.browser)

    // Dépendance d'exécution NON DÉCLARÉE de l'AAR App Remote : son `GsonMapper` construit
    // un `GsonBuilder` dès `ConnectionParams.Builder.build()`. En `compileOnly`, l'AAR
    // n'apporte aucune transitive, et l'application plantait sur NoClassDefFoundError au
    // premier appel à connect(). Découvert sur appareil — aucun test ne pouvait le voir,
    // le seam RemoteConnector remplaçant justement tout le SDK.
    //
    // Déclarée ici plutôt que laissée aux consommateurs : Gson est sur Maven Central et
    // redistribuable, contrairement à l'AAR.
    implementation(libs.gson)

    // Un seul AAR Spotify désormais, celui de l'App Remote. Le SDK d'autorisation a été
    // abandonné : son chemin app-à-app jette les paramètres personnalisés, donc PKCE ne
    // peut pas y passer. Voir CustomTabAuthorizer.
    //
    // `compileOnly` : non redistribuable, non publié sur Maven, et non empaqueté dans notre
    // AAR — l'application hôte le fournit. Voir README.md.
    compileOnly(files("libs/spotify-app-remote-release-0.8.0.aar"))

    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // La version `-core` de DataStore fonctionne sur JVM nu : le store de jetons se teste
    // sur un fichier temporaire, sans Robolectric ni émulateur.
    testImplementation(libs.androidx.datastore.preferences.core)
}
