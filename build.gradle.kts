import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.MavenPublishBaseExtension

// Tous les plugins sont amenés sur le classpath ici, en `apply false`. Un sous-projet qui
// redéclarerait une version échouerait : Gradle refuse de vérifier la compatibilité d'un
// plugin déjà chargé.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.kapt) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

// La surface publique est versionnée dans `<module>/api/*.api`. Toute modification
// apparaît en diff de revue : une lib publiée ne change pas son contrat par accident.
// `./gradlew apiDump` régénère les fichiers, `./gradlew apiCheck` échoue s'ils divergent.
apiValidation {
    ignoredProjects.add("spotify-sdk")
}

allprojects {
    // `io.github.vandervdb` et non `org.vander.*` : Maven Central exige de prouver la
    // propriété du namespace, ce qui supposerait de posséder le domaine vander.org. Le
    // préfixe io.github.<compte> se vérifie avec le compte GitHub, gratuitement.
    //
    // Les paquets Kotlin restent `org.vander.spotify.*` : Central ne demande pas qu'ils
    // coïncident avec le groupId, et les renommer serait un remaniement sans contrepartie.
    group = "io.github.vandervdb"
    version = "0.1.0-SNAPSHOT"
}

subprojects {
    // Le projet racine ne produit pas d'artefact.
    plugins.apply("com.vanniktech.maven.publish")

    // AGP embarque une version de Dokka qui ne sait pas lire les métadonnées Kotlin 2.4 :
    // `javaDocReleaseGeneration` échoue. Les modules KMP, eux, publient déjà un javadoc.jar
    // vide — vanniktech ne génère de vraie documentation qu'avec Dokka, non branché ici.
    // On s'aligne donc plutôt que de faire semblant : aucun javadoc.jar côté Android.
    //
    // À reprendre avant la 1.0 : Maven Central exige un javadoc.jar pour une version
    // publiée (pas pour un SNAPSHOT). Brancher Dokka réglera les deux d'un coup.
    plugins.withId("com.android.library") {
        extensions.configure<MavenPublishBaseExtension> {
            configure(
                AndroidSingleVariantLibrary(
                    variant = "release",
                    sourcesJar = true,
                    publishJavadocJar = false,
                ),
            )
        }
    }

    extensions.configure<MavenPublishBaseExtension> {
        publishToMavenCentral()

        // La signature n'est exigée que par Maven Central. L'imposer inconditionnellement
        // casserait `publishToMavenLocal` et la CI sur toute machine sans clé — c'est-à-dire
        // pendant tout le développement.
        if (providers.gradleProperty("signingInMemoryKey").isPresent) {
            signAllPublications()
        }

        pom {
            name.set(this@subprojects.name)
            description.set(
                provider { this@subprojects.description ?: "Module de la bibliothèque Spotify Kotlin Multiplatform" },
            )
            url.set("https://github.com/vandervdb/Spotify-sdk")
            inceptionYear.set("2026")

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("vandervdb")
                    name.set("Arnaud Vanderbecq")
                    url.set("https://github.com/vandervdb")
                }
            }
            scm {
                url.set("https://github.com/vandervdb/Spotify-sdk")
                connection.set("scm:git:git://github.com/vandervdb/Spotify-sdk.git")
                developerConnection.set("scm:git:ssh://git@github.com/vandervdb/Spotify-sdk.git")
            }
        }
    }
}
