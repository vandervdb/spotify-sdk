import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Le clientId vient de local.properties, jamais du dépôt. C'est exactement ce que fait une
// application consommatrice : la lib, elle, le reçoit à l'exécution via SpotifyConfig.
val localProperties =
    Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
val spotifyClientId: String = localProperties.getProperty("CLIENT_ID") ?: ""

android {
    namespace = "org.vander.spotify.sample"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.vander.spotify.sample"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidCompileSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"$spotifyClientId\"")

        // Doit correspondre à une redirect URI enregistrée sur le dashboard Spotify pour ce
        // clientId. Le SDK d'autorisation déclare son LoginActivity avec un intent-filter
        // paramétré par ces deux placeholders.
        manifestPlaceholders["redirectSchemeName"] = "org-vander-androidapp"
        manifestPlaceholders["redirectHostName"] = "callback"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging { resources.excludes += "META-INF/**" }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":spotify-android"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Les AAR du SDK Spotify sont `compileOnly` dans la lib : c'est l'application qui les
    // met sur le classpath d'exécution. Une application consommatrice fera de même.
    implementation(files("../spotify-android/libs/spotify-app-remote-release-0.8.0.aar"))
    implementation(files("../spotify-android/libs/spotify-auth-release-2.1.0.aar"))
}
