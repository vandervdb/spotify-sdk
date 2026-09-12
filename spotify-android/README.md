# :spotify-android

Couche Android : contrôle de lecture local par l'**App Remote**, autorisation par le SDK
Spotify, conservation du jeton dans DataStore. Dépend de `:spotify-core`, qui apporte tout
le reste.

---

## Installation

### 1. Les AAR du SDK Spotify

Ils **ne sont pas dans ce dépôt**. Ils portent les conditions de Spotify et ne sont publiés
ni sur Maven Central ni sur le dépôt Google ; les redistribuer ici n'est pas une option pour
une bibliothèque publiée. `libs/*.aar` est ignoré par git.

Les télécharger depuis [developer.spotify.com](https://developer.spotify.com/documentation/android/)
et les déposer sous `spotify-android/libs/` :

```
spotify-android/libs/spotify-app-remote-release-0.8.0.aar
spotify-android/libs/spotify-auth-release-2.1.0.aar
```

Ils sont déclarés en `compileOnly` : ils ne sont pas empaquetés dans l'AAR produit, et c'est
l'application hôte qui les met sur son classpath d'exécution.

### 2. Les placeholders de manifeste

Le SDK d'autorisation déclare son `LoginActivity` avec un intent-filter dont le schéma et
l'hôte viennent de deux placeholders. Ils sont fixés à la compilation de **l'application**,
et doivent correspondre à la `redirectUri` passée dans `SpotifyConfig` :

```kotlin
// build.gradle.kts de l'application
android {
    defaultConfig {
        manifestPlaceholders["redirectSchemeName"] = "vinylotech"
        manifestPlaceholders["redirectHostName"] = "callback"
    }
}
```

soit `redirectUri = "vinylotech://callback"`. `SpotifyAndroid.create` vérifie la forme de
l'URI et échoue tôt si elle ne peut pas correspondre — l'erreur serait sinon découverte au
retour de l'écran d'autorisation, là où elle est le plus coûteuse à diagnostiquer.

C'est une contrainte du SDK Spotify, pas un choix de cette lib : le `clientId` arrive à
l'exécution, la redirect URI doit aussi être connue à la compilation.

---

## Utilisation

```kotlin
val spotify = SpotifyAndroid.create(
    context = applicationContext,
    config = SpotifyConfig(
        clientId = BuildConfig.SPOTIFY_CLIENT_ID,
        redirectUri = "vinylotech://callback",
    ),
    authorizer = ActivityAuthorizer { currentActivity },
)

spotify.signIn()                       // PKCE, aucun secret
spotify.player.connect()               // liaison App Remote
spotify.player.play(TrackId("4cOdK2w"))

spotify.player.nowPlaying.collect { render(it) }
```

`AndroidSpotifyClient` hérite de `SpotifyClient` : le code écrit contre le contrat commun
fonctionne tel quel, et seul ce qui touche `player` est propre à Android.

---

## Deux axes, deux jeux de verbes

C'est le point de conception le plus important de ce module, et la correction d'un défaut
bien réel de l'implémentation dont il s'inspire.

| | Identité | Liaison App Remote |
|---|---|---|
| État | `spotify.session` | `spotify.player.connection` |
| Ouvrir | `signIn()` | `player.connect()` |
| Fermer | `signOut()` | `player.disconnect()` |

Les deux sont indépendants. `player.disconnect()` **ne déconnecte pas l'utilisateur** :
le jeton reste en place et la liaison se rouvre sans repasser par l'écran Spotify.
`signOut()` **ne coupe pas la liaison**. Mélanger les deux dans un seul état est ce qui rend
impossible de distinguer « en arrière-plan » de « jamais connecté ».

`connect()` après `disconnect()` rétablit l'abonnement à l'état du lecteur — c'est vérifié
par un test, parce que c'est exactement là que l'implémentation d'origine restait muette
jusqu'au redémarrage du processus.

---

## Le seul fichier sans test

`internal/remote/SdkRemoteConnector.kt` est le seul point de contact avec le SDK App Remote.
`SpotifyAppRemote.connect` est une fonction statique : elle ne peut pas être simulée.

Tout ce qui est au-dessus travaille sur les interfaces `RemoteConnector`, `RemoteHandle` et
`PlayerSnapshot`, donc se teste sans appareil, sans application Spotify installée et sans un
seul type du SDK. Concentrer l'intestable en un point aussi mince que possible est la seule
façon honnête de traiter une dépendance qu'on ne peut pas simuler.

---

## Tests

```bash
./gradlew :spotify-android:testDebugUnitTest    # 21 tests, sans émulateur
```

`DataStoreTokenStore` prend un `DataStore` et non un `Context`, donc il se teste sur un
fichier temporaire en JVM nue — ni Robolectric, ni appareil.

**Non vérifié** : tout comportement réel de l'App Remote et de l'écran d'autorisation. Il
faut un appareil avec l'application Spotify connectée et un `clientId` enregistré. Le code
compile et ses contrats sont testés ; personne n'a encore vu ce module parler à Spotify.
