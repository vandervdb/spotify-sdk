plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
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
