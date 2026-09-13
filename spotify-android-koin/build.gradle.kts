plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

description = "Câblage Koin facultatif pour spotify-android."

kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "org.vander.spotify.android.koin"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = false }
}

dependencies {
    api(project(":spotify-android"))

    // Aucun processeur d'annotations, donc aucune des contraintes qui pèsent sur le module
    // Hilt : ni kapt, ni KSP, ni plafond d'AGP. Le graphe est du Kotlin ordinaire.
    api(platform(libs.koin.bom))
    api(libs.koin.core)
    implementation(libs.koin.android)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
}
