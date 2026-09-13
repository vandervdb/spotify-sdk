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

// Le spec généré est un artefact de build : il disparaît au `clean`, et la compilation
// échouait alors sur des références introuvables. Elle en dépend donc explicitement.
val codegen =
    tasks.register<Exec>("codegen") {
        description = "Régénère le spec TurboModule depuis src/NativeSpotify.ts"
        group = "build"

        val packageDir = layout.projectDirectory.dir("..")
        workingDir = packageDir.asFile

        inputs.file(packageDir.file("src/NativeSpotify.ts"))
        outputs.dir(packageDir.dir("build/generated"))

        doFirst {
            require(packageDir.dir("node_modules").asFile.exists()) {
                "Le codegen React Native a besoin de ses dépendances : lancer `npm install` " +
                    "dans spotify-rn/ avant de compiler ce module."
            }
        }

        commandLine("sh", "-c", "npm run codegen")
    }

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(codegen)
}
