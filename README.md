# spotify-sdk

Bibliothèque Spotify publiable, pensée pour des applications Android natives et React
Native. **Web API partout, App Remote sur Android uniquement**, et aucun secret client —
le flot d'autorisation est Authorization Code + PKCE.

> État : tranche 1 sur 5. `spotify-core` est écrit et testé ; les quatre autres artefacts
> ne sont pas encore commencés. Voir *Feuille de route* plus bas.

---

## Le découpage, et pourquoi

Spotify est joignable par deux canaux sans rapport l'un avec l'autre, et cette frontière
commande toute l'architecture :

| | Web API | App Remote |
|---|---|---|
| Transport | HTTPS | IPC vers l'app Spotify installée |
| Donne | playlists, file d'attente, titres likés, profil | contrôle de lecture local, état poussé |
| Portable | oui | non — SDK propre à chaque OS |

D'où : un cœur multiplateforme pour le premier, une couche Android pour le second. On ne
combat pas la frontière, on la rend visible dans les artefacts.

| Artefact | Contenu | Cibles |
|---|---|---|
| **`spotify-core`** | domaine, Web API, PKCE, contrat `TokenStore` | `jvm` · `iosArm64` · `iosX64` · `iosSimulatorArm64` |
| `spotify-android` | App Remote, SDK auth, DataStore | AAR |
| `spotify-android-hilt` | un `@Module`, facultatif | AAR |
| `spotify-testing` | `FakeSpotifyClient` | KMP |
| `@vander/spotify-rn` | spec TurboModule + adaptateurs | npm |

`spotify-core` ne déclare **pas** de cible Android : il ne contient aucun code Android, un
consommateur Android prend l'artefact JVM. Ça retire AGP et le SDK Android du chemin
critique du module le plus important.

---

## Aucun secret, par construction

Une bibliothèque distribuée ne peut pas embarquer de `client_secret` : il serait celui de
tous ses consommateurs, et un `unzip` de l'APK suffirait à le lire. Le flot est donc
**Authorization Code + PKCE** (RFC 7636) :

1. un `code_verifier` aléatoire est tiré du CSPRNG de la plateforme ;
2. son condensé, `BASE64URL(SHA256(verifier))`, part dans l'URL d'autorisation ;
3. le `verifier` lui-même n'est présenté qu'à l'échange du code.

`SpotifyConfig` n'a délibérément pas de champ `clientSecret`, et un test vérifie
qu'aucune requête ne porte ni `client_secret` ni en-tête `Authorization` vers
`accounts.spotify.com`.

Le `state` anti-rejeu (RFC 6749 §10.12) est vérifié avant tout échange de code.

---

## Utilisation

```kotlin
val spotify = Spotify.create(
    config = SpotifyConfig(
        clientId = "…",                       // fourni à l'init, jamais via BuildConfig
        redirectUri = "monapp://callback",
        scopes = SpotifyScope.DEFAULT,
    ),
    tokenStore = myTokenStore,                 // DataStore sur Android, Keychain sur iOS
    authorizer = myAuthorizer,                 // ouvre l'écran d'autorisation
)

spotify.signIn().onFailure { return }
spotify.refreshPlaylists()

spotify.playlists.collect { page -> render(page.items) }
```

Il n'y a pas d'ordre d'appel à retenir, pas de `launcher` à fournir et pas de `CoroutineScope`
à passer. Toute opération rend un `Result` dont l'échec est un `SpotifyError` : aucune
exception ne traverse la frontière publique — un hôte React Native n'a pas de `try`/`catch`
Kotlin.

---

## Discipline d'API publique

- **`explicitApi()`** : tout membre public doit être déclaré explicitement, avec un type de
  retour explicite. Le compilateur tient la frontière, pas la revue.
- **binary-compatibility-validator** : la surface publique est figée dans
  `spotify-core/api/spotify-core.api`, versionné. Toute modification apparaît en diff.
  `./gradlew apiCheck` échoue si le contrat a bougé sans que le fichier suive ;
  `./gradlew apiDump` le régénère.

39 déclarations publiques aujourd'hui. Ni l'implémentation du client, ni la couche HTTP, ni
les DTO n'en font partie.

---

## Build et tests

```bash
./gradlew :spotify-core:jvmTest            # 30 tests
./gradlew :spotify-core:iosX64Test         # les mêmes 30, sur simulateur
./gradlew apiCheck                         # la surface publique n'a pas bougé
```

Les tests n'ont besoin ni de réseau, ni de compte Spotify, ni d'identifiants : le seam de
test est le moteur Ktor, remplacé par un `MockEngine`. Deux adapters, donc un vrai seam.

**Cible simulateur.** La tâche de test iOS est marquée `SKIPPED` — donc verte pour rien —
si aucun appareil n'est désigné. Le nom par défaut est `iPhone 15 Pro` ; il doit exister
dans `xcrun simctl list devices available`. Pour en choisir un autre :

```bash
./gradlew :spotify-core:iosX64Test -Pspotify.simulator="iPhone 15"
```

**Hôte x86_64.** Sur cette machine, la JVM et le shell tournent sous Rosetta, donc
Kotlin/Native voit un hôte `macos-x86_64` et refuse `iosSimulatorArm64Test`. La cible
`iosX64` prend le relais et exécute la même suite. Un JDK arm64 lèverait la restriction et
accélérerait tous les builds.

---

## Ce qui est prouvé, et ce qui ne l'est pas

Prouvé par un test qui tourne :

- l'enchaînement PKCE, contre le **vecteur de référence de la RFC 7636 annexe B** ;
- SHA-256, contre les vecteurs NIST — sur JVM **et** sur iOS, donc les deux `actual` ;
- qu'aucun secret ne part vers `accounts.spotify.com` ;
- qu'un `state` qui ne correspond pas empêche l'échange du code ;
- que deux appels concurrents ne déclenchent qu'un seul renouvellement de jeton ;
- qu'un `refresh_token` absent de la réponse ne remplace pas celui qu'on détient.

**Non vérifié**, et qui le restera tant qu'un appareil et des identifiants ne seront pas
dans la boucle : tout appel réel à l'API Spotify, et tout comportement de l'App Remote.
Le code compile et ses contrats sont testés ; personne n'a encore vu la lib parler à
Spotify.

---

## Feuille de route

| | Tranche | État |
|---|---|---|
| 1 | `spotify-core` — domaine, Web API, PKCE, session | **fait, 30 tests verts** |
| 2 | `spotify-android` — App Remote, SDK auth, DataStore | à faire |
| 3 | `spotify-testing` + `spotify-android-hilt` | à faire |
| 4 | `@vander/spotify-rn` — spec TS, adaptateurs Kotlin et Swift | à faire |
| 5 | publication Maven, CI | à faire |

À la tranche 2 : les AAR du SDK Spotify ne sont **pas** versionnés ici. Ils portent les
conditions de Spotify et ne sont pas publiés sur Maven Central ; `spotify-android` les
déclarera en `compileOnly` avec un `libs/` ignoré par git et une étape d'installation
documentée.

---

## Licence

[MIT](LICENSE) — © 2026 Arnaud Vanderbecq.

La licence couvre le code de ce dépôt. Elle ne s'étend pas aux binaires du SDK Spotify, qui
portent les conditions de Spotify. Les marques Spotify appartiennent à Spotify AB ; ce
dépôt n'est ni affilié à Spotify, ni approuvé par Spotify.
