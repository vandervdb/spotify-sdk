# :spotify-android-hilt

Câblage Hilt de `:spotify-android`. **Facultatif.**

```kotlin
implementation("org.vander.spotify:spotify-android-hilt:<version>")
```

Une bibliothèque publiée ne choisit pas l'injection de dépendances de ses consommateurs :
`:spotify-android` s'utilise très bien avec une fabrique, un service locator, ou rien du
tout. Cet artefact existe pour ceux qui utilisent Hilt, et ne coûte qu'une ligne à ceux-là.

## Ce que l'application doit fournir

Deux liaisons, parce qu'elles lui appartiennent :

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AppSpotifyModule {
    @Provides
    fun config(): SpotifyConfig = SpotifyConfig(
        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
        redirectUri = "vinylotech://callback",
    )

    @Provides
    fun authorizer(holder: CurrentActivityHolder): Authorizer =
        ActivityAuthorizer { holder.current }
}
```

Le `clientId` est propre à chaque application, et l'`Authorizer` a besoin de l'Activity
courante — que seule l'application sait fournir.

En retour, `SpotifyModule` fournit `AndroidSpotifyClient`, `SpotifyClient` et
`SpotifyPlayer`, pour qu'un ViewModel n'injecte que ce dont il a besoin.

## Deux contraintes de version, vérifiées

- **Hilt épinglé à 2.58.** À partir de 2.60, le plugin Gradle refuse AGP 8
  (« only compatible with AGP 9.0.0 or higher »). Dépingler Hilt et adopter AGP 9 sont la
  même décision, et une lib n'impose pas AGP 9 à ses consommateurs pour un artefact
  facultatif.
- **kapt, pas KSP.** Au 12/09/2026 le dernier KSP publié cible Kotlin 2.3.12, et ce projet
  est sur 2.4.20. kapt est indépendant de la version du compilateur, et sa lenteur est sans
  conséquence sur un module qui ne contient que des annotations.
- `kotlin-metadata-jvm` est ajouté au classpath d'annotation : le lecteur embarqué par
  Dagger/Hilt est en retard sur le compilateur et refuse les métadonnées Kotlin 2.4
  ([google/dagger#5177](https://github.com/google/dagger/issues/5177)).
