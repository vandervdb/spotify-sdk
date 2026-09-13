# spotify-sdk

[![CI](https://github.com/vandervdb/Spotify-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/vandervdb/Spotify-sdk/actions/workflows/ci.yml)
![statut](https://img.shields.io/badge/statut-work%20in%20progress-orange)
![licence](https://img.shields.io/badge/licence-MIT-blue)

Bibliothèque Spotify publiable, pensée pour des applications Android natives et React
Native. **Web API partout, App Remote sur Android uniquement**, et aucun secret client —
le flot d'autorisation est Authorization Code + PKCE.

---

## ⚠️ Work in progress — ne pas utiliser en production

Ce dépôt est public parce qu'il n'y a pas de raison de le cacher, pas parce qu'il est prêt.
Trois choses à savoir avant d'aller plus loin :

- **Rien n'est publié.** Aucun artefact sur Maven Central ; les coordonnées `org.vander.spotify:*`
  qui apparaissent dans ce README décrivent l'intention, pas quelque chose que vous pouvez
  résoudre aujourd'hui. Il faut construire depuis les sources.
- **L'API n'est pas stable.** Version `0.1.0-SNAPSHOT`. La surface publique est figée dans
  des fichiers `api/*.api` et toute modification apparaît en revue — mais elle *va* changer,
  et sans préavis tant que la 1.0 n'est pas là.
- **Personne n'a encore vu cette lib parler à Spotify.** Le code compile et ses contrats
  sont couverts par 71 tests, mais aucun appel réel à l'API Spotify ni aucun comportement de
  l'App Remote n'a été observé : cela demande un `client_id` enregistré et un appareil avec
  l'application Spotify connectée. Voir *Ce qui est prouvé, et ce qui ne l'est pas*.

**Avancement** : tranches 1 à 3 sur 5. Le cœur, la couche Android, les doubles de test et
les deux câblages DI sont écrits et testés — **71 tests, 111 exécutions, 0 échec**, sans
réseau ni appareil. Restent le pont React Native et la publication. Voir *Feuille de route*.

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
| **`spotify-android`** | App Remote, SDK auth, DataStore | AAR |
| **`spotify-android-hilt`** | câblage Hilt, facultatif | AAR |
| **`spotify-android-koin`** | câblage Koin, facultatif — alternative à ci-dessus | AAR |
| **`spotify-testing`** | `FakeSpotifyClient` | KMP |
| **`spotify-android-testing`** | `FakeSpotifyPlayer`, `FakeAndroidSpotifyClient` | AAR |
| `@vander/spotify-rn` | spec TurboModule + adaptateurs | npm |

Sept artefacts et non cinq comme prévu initialement. Deux raisons, toutes deux assumées.
Les câblages Hilt et Koin sont deux alternatives : on n'en prend qu'une, et une lib n'impose
pas son DI. Et pour les doubles : `SpotifyPlayer` n'existant que côté
Android, son double ne peut pas vivre dans un module multiplateforme qui cible aussi iOS.
Le découpage des doubles suit exactement celui de la lib — un consommateur iOS ne tire
jamais de code Android.

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
./gradlew :spotify-core:jvmTest                     # 30 tests
./gradlew :spotify-core:iosX64Test                  # les mêmes 30, sur simulateur
./gradlew :spotify-android:testDebugUnitTest        # 21 tests, sans émulateur
./gradlew :spotify-testing:jvmTest                  # 10 tests, sur le double lui-même
./gradlew :spotify-android-testing:testDebugUnitTest # 6 tests
./gradlew :spotify-android-koin:testDebugUnitTest    # 4 tests
./gradlew apiCheck                                  # la surface publique n'a pas bougé
```

Les doubles de test sont couverts par leurs propres tests. Un double publié est du code
livré : un double dont les corps sont vides passe les tests qu'il est censé servir, et
l'erreur se découvre chez le consommateur.

**Ce que le badge CI couvre — et ce qu'il ne couvre pas.** Les jobs `jvm` et `ios` vérifient
`spotify-core` et `spotify-testing`, tests et surface publique, sur les deux plateformes. Les
quatre modules Android ne sont vérifiés que si les AAR du SDK Spotify sont fournis à la CI
par un secret de dépôt, puisqu'ils ne peuvent pas être versionnés ici. Sans ce secret, le job
Android s'arrête en nommant ce qui n'est pas vérifié plutôt que de passer au vert.
Voir [`.github/workflows/README.md`](.github/workflows/README.md).

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
- qu'un `refresh_token` absent de la réponse ne remplace pas celui qu'on détient ;
- que se reconnecter à l'App Remote après une déconnexion rétablit bien l'abonnement ;
- que la déconnexion annule l'abonnement **avant** de fermer la liaison ;
- qu'une commande de lecture sans liaison ouverte échoue au lieu de ne rien faire.

**Non vérifié**, et qui le restera tant qu'un appareil et des identifiants ne seront pas
dans la boucle : tout appel réel à l'API Spotify, et tout comportement de l'App Remote.
Le code compile et ses contrats sont testés ; personne n'a encore vu la lib parler à
Spotify.

---

## Feuille de route

| | Tranche | État |
|---|---|---|
| 1 | `spotify-core` — domaine, Web API, PKCE, session | **fait, 30 tests verts** |
| 2 | `spotify-android` — App Remote, SDK auth, DataStore | **fait, 21 tests verts** |
| 3 | `spotify-testing`, `spotify-android-testing`, `spotify-android-hilt`, `spotify-android-koin` | **fait, 20 tests verts** |
| 4 | `@vander/spotify-rn` — spec TS, adaptateurs Kotlin et Swift | à faire |
| 5 | publication Maven, CI | à faire |

Les AAR du SDK Spotify ne sont **pas** versionnés ici : ils portent les conditions de
Spotify et ne sont publiés sur aucun dépôt Maven. `spotify-android` les déclare en
`compileOnly`, `libs/` est ignoré par git, et l'étape d'installation est documentée dans
[`spotify-android/README.md`](spotify-android/README.md).

---

## Licence

[MIT](LICENSE) — © 2026 Arnaud Vanderbecq.

La licence couvre le code de ce dépôt. Elle ne s'étend pas aux binaires du SDK Spotify, qui
portent les conditions de Spotify. Les marques Spotify appartiennent à Spotify AB ; ce
dépôt n'est ni affilié à Spotify, ni approuvé par Spotify.
