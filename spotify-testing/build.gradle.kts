plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = "Doubles de test multiplateformes pour spotify-core, publiés comme artefact."

kotlin {
    explicitApi()

    jvm()
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // `api` et non `implementation` : un test qui utilise le double manipule les
            // types du cœur, il doit les voir sans les redéclarer.
            api(project(":spotify-core"))
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
