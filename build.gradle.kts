// Tous les plugins sont amenés sur le classpath ici, en `apply false`. Un sous-projet qui
// redéclarerait une version échouerait : Gradle refuse de vérifier la compatibilité d'un
// plugin déjà chargé.
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.binary.compatibility.validator)
}

// La surface publique est versionnée dans `<module>/api/*.api`. Toute modification
// apparaît en diff de revue : une lib publiée ne change pas son contrat par accident.
// `./gradlew apiDump` régénère les fichiers, `./gradlew apiCheck` échoue s'ils divergent.
apiValidation {
    ignoredProjects.add("spotify-sdk")
}

allprojects {
    group = "org.vander.spotify"
    version = "0.1.0-SNAPSHOT"
}
