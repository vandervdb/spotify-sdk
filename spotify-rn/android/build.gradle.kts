plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

android {
    namespace = "org.vander.spotify.rn"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.androidMinSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { buildConfig = false }

    // Le spec est produit par le codegen React Native à partir de src/NativeSpotify.ts.
    // Dans une application hôte, c'est le plugin Gradle de React Native qui le lance ;
    // ici on pointe simplement sa sortie, pour pouvoir compiler l'adaptateur — sans quoi
    // il ne serait vérifié que le jour où quelqu'un l'intègre.
    sourceSets["main"].java.srcDir(layout.projectDirectory.dir("../build/generated/java"))
}

dependencies {
    api(project(":spotify-android"))
    compileOnly(libs.react.android)
    implementation(libs.kotlinx.coroutines.android)
}

// `./gradlew :spotify-rn-android:codegen` régénère le spec depuis le TypeScript.
tasks.register<Exec>("codegen") {
    description = "Régénère le spec TurboModule depuis src/NativeSpotify.ts"
    group = "build"
    workingDir = layout.projectDirectory.dir("..").asFile
    commandLine("sh", "-c", "npm run codegen")
}
