# :spotify-android-koin

Câblage Koin de `:spotify-android`. **Facultatif**, comme son jumeau
[`:spotify-android-hilt`](../spotify-android-hilt).

```kotlin
implementation("org.vander.spotify:spotify-android-koin:<version>")
```

Une bibliothèque publiée ne choisit pas l'injection de dépendances de ses consommateurs.
`:spotify-android` s'utilise très bien avec une fabrique, un service locator, ou rien du
tout. Ces deux artefacts existent pour ceux qui utilisent déjà l'un ou l'autre, et ne
coûtent qu'une ligne à ceux-là. **N'en prendre qu'un.**

## Utilisation

```kotlin
startKoin {
    androidContext(this@MainApplication)
    modules(
        spotifyModule(),
        module {
            single {
                SpotifyConfig(
                    clientId = BuildConfig.SPOTIFY_CLIENT_ID,
                    redirectUri = "vinylotech://callback",
                )
            }
            single<Authorizer> { ActivityAuthorizer { get<CurrentActivityHolder>().current } }
        },
    )
}
```

L'application fournit `SpotifyConfig` et `Authorizer`, parce qu'ils lui appartiennent : le
`clientId` est propre à chaque application, et l'`Authorizer` a besoin de l'Activity
courante. En retour, `spotifyModule()` fournit `AndroidSpotifyClient`, `SpotifyClient` et
`SpotifyPlayer`, pour qu'un composant n'injecte que ce dont il a besoin.

`androidContext(...)` est obligatoire dans `startKoin` : le module en dépend.

## Koin ou Hilt ?

Le contrat est identique des deux côtés — mêmes trois fournitures, mêmes deux liaisons à la
charge de l'application. Ce qui diffère est le coût de construction, et il n'est pas
symétrique dans l'état actuel de l'écosystème :

| | Koin | Hilt |
|---|---|---|
| Processeur d'annotations | aucun | kapt ou KSP |
| Contrainte de version | aucune | Hilt ≤ 2.58 tant qu'on reste sur AGP 8 |
| KSP | sans objet | indisponible pour Kotlin 2.4 au 13/09/2026 |
| `kotlin-metadata-jvm` | sans objet | à forcer ([google/dagger#5177](https://github.com/google/dagger/issues/5177)) |
| Graphe vérifié | partiellement, par des tests | à la compilation de l'application |

Hilt attrape à la compilation ce que Koin ne découvre qu'à l'exécution — c'est son avantage
réel, et il ne disparaît pas. Mais dans ce projet précis, le module Hilt a coûté trois
contournements de version et n'a aucun test, là où le module Koin en a quatre.

## Tests

```bash
./gradlew :spotify-android-koin:testDebugUnitTest    # 4 tests
```

Ils vérifient les **déclarations** : les trois contrats fournis, tous en `single`, et
l'absence de `SpotifyConfig` et `Authorizer` — le contrat avec l'application mérite un test,
sinon rien n'empêche la lib de décider à la place de son consommateur.

Ils ne vérifient pas l'instanciation : `SpotifyAndroid.create` a besoin d'un `Context` réel
pour le répertoire de DataStore et la liaison App Remote. Ce que l'on gagnerait à le simuler
ne concernerait plus le câblage mais la lib elle-même, déjà couverte par les tests de
`:spotify-android`.

`single` n'est pas un confort : deux instances ouvriraient deux liaisons vers l'application
Spotify, et DataStore refuserait le second store — il n'accepte qu'une instance active par
fichier.
