plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    // kapt plutôt que KSP : au 12/09/2026 le dernier KSP publié est pour Kotlin 2.3.12, et
    // ce projet est sur 2.4.20. kapt est indépendant de la version du compilateur, et sa
    // lenteur est sans conséquence sur un module qui ne contient que des annotations.
    alias(libs.plugins.kotlin.kapt)
}

kotlin {
    explicitApi()
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "org.vander.spotify.android.hilt"
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
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    kapt(libs.kotlin.metadata.jvm)
}
