plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

description = "Doubles de test multiplateformes pour spotify-core, publiés comme artefact."

kotlin {
    explicitApi()

    jvm {
        // Sans cible explicite, le bytecode suit le JDK qui lance le build (JDK 25 → classes v69
        // illisibles par un Gradle en JDK 21). Aligné sur les modules Android du SDK.
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
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
