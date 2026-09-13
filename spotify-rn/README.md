# @vander/spotify-rn

Pont React Native vers la bibliothèque Spotify. Le spec TypeScript est la source unique du
codegen ; les adaptateurs Android et iOS ne font que traduire.

```bash
npm install @vander/spotify-rn   # pas encore publié
```

---

## État de vérification

C'est la tranche la moins vérifiée du dépôt, et il faut le dire précisément.

| Élément | État |
|---|---|
| Spec TypeScript (`src/NativeSpotify.ts`) | ✅ accepté par le codegen : 19 méthodes, 4 événements |
| Surface TypeScript (`src/index.ts`) | ✅ `tsc --noEmit` sans erreur |
| Spec Android généré | ✅ produit, `NativeSpotifySpec.java` |
| Adaptateur Kotlin | ✅ **compile** contre le spec généré et `com.facebook.react:react-android` |
| `SpotifyCore.xcframework` | ✅ construit — tranches `ios-arm64` et `ios-arm64_x86_64-simulator` |
| Adaptateur Swift (`ios/SpotifyRn.swift`) | ❌ **non compilé** — aucun projet Xcode ici |
| Fonctionnement dans une application | ❌ non observé, sur aucune des deux plateformes |

Rien de tout cela n'a tourné dans une vraie application React Native.

---

## Le contrat, et son asymétrie

```ts
import Spotify from '@vander/spotify-rn';

await Spotify.signIn();                       // PKCE, aucun secret
const playlists = await Spotify.refreshPlaylists();

if (Spotify.isPlayerAvailable()) {            // false sur iOS
  await Spotify.connectPlayer();
  await Spotify.play('4cOdK2wGLETKBW3PvgPWqT');
}

Spotify.onNowPlayingChange(state => setNowPlaying(state));
```

**`isPlayerAvailable()` est le point à comprendre.** Le contrôle de lecture local passe par
l'App Remote, dont le SDK n'existe que sur Android. Côté Kotlin cette distinction est portée
par le classpath — `SpotifyPlayer` est absent sur iOS, et le code qui l'appellerait ne
compile pas. Mais le spec TurboModule est partagé entre les deux plateformes et ne peut pas
exprimer « cette méthode n'existe que sur Android ». D'où un drapeau à l'exécution, et des
commandes qui rejettent avec `PLAYER_UNAVAILABLE` plutôt que d'être absentes.

C'est le seul endroit où les deux surfaces divergent, et c'est une contrainte du pont, pas
un choix.

## Erreurs

Le `Result` de Kotlin traverse le pont en `resolve`/`reject`. Le code est celui du type
scellé `SpotifyError`, donc identique des deux côtés et exploitable par un `switch` :

`NOT_SIGNED_IN` · `UNAUTHORIZED` · `AUTHORIZATION_CANCELLED` · `STATE_MISMATCH` ·
`NETWORK` · `API_<statut>` · `SERIALIZATION` · `PLAYER_UNAVAILABLE` (iOS)

## Installation côté Android

L'application hôte fournit sa `SpotifyConfig` — le `clientId` et la redirect URI lui
appartiennent :

```kotlin
// MainApplication.kt
override fun getPackages() = PackageList(this).packages.apply {
    add(SpotifyRnPackage(SpotifyConfig(clientId = …, redirectUri = "monapp://callback")))
}
```

L'`ActivityAuthorizer` est construit par le paquet : côté React Native, l'Activity courante
se lit sur le contexte, l'hôte JavaScript n'a pas à la connaître.

---

## Développement

```bash
npm install
npm run typecheck                                   # tsc
npm run codegen                                     # régénère le spec natif
./gradlew :spotify-rn-android:compileDebugKotlin    # compile l'adaptateur Kotlin
./gradlew :spotify-core:assembleSpotifyCoreXCFramework
```

Le module Gradle `:spotify-rn-android` n'existe que pour cette vérification : le paquet se
livre par npm, pas par Maven. Il est exclu de la publication et de la validation de surface
publique.

## Ce qui reste à faire

1. **Un projet Xcode ou un Pod de test** — sans lui, `ios/SpotifyRn.swift` n'est pas compilé
   et sa liaison au spec ObjC++ généré n'existe pas.
2. **Copier le XCFramework dans `ios/`** au moment de l'empaquetage : le podspec l'attend
   sous `ios/SpotifyCore.xcframework`, il est produit dans `spotify-core/build/XCFrameworks`.
3. **Une application d'exemple** sur chaque plateforme — c'est la seule façon de vérifier que
   le pont fonctionne, par opposition à compiler.
