# :spotify-testing

`FakeSpotifyClient` — le second adapter du seam `SpotifyClient`, publié comme artefact.

```kotlin
testImplementation("org.vander.spotify:spotify-testing:<version>")
```

## Pourquoi un artefact et pas un fichier recopié

Un seam n'est réel que s'il a deux implémentations. Celle de production en est une ; ce
double est l'autre. Le livrer évite que chaque consommateur réécrive la sienne — et surtout
qu'il la réécrive **vide**.

Ce double **se comporte** : `signIn()` fait passer `session` à `Authorized`, `setSaved()`
modifie un état que `isSaved()` relit, `signOut()` vide les caches. Il est couvert par ses
propres tests, comme n'importe quel code publié.

## Trois usages

```kotlin
val spotify = FakeSpotifyClient()

// 1 — piloter l'état
spotify.emitPlaylists(PlaylistCollection(listOf(Playlist(PlaylistId("p1"), "Matin")), total = 1))

// 2 — scénariser une panne, valable pour un seul appel
spotify.nextFailure = SpotifyError.Unauthorized("jeton expiré")
assertTrue(spotify.refreshQueue().isFailure)
assertTrue(spotify.refreshQueue().isSuccess)

// 3 — vérifier ce qui a été demandé
assertEquals(listOf(SpotifyCall.RefreshQueue, SpotifyCall.RefreshQueue), spotify.calls)
```

Un scénario ne vaut que pour un appel, délibérément : sans ça, tester une reprise après
erreur oblige à reconstruire le double, et un test qui reconstruit son double teste deux
choses à la fois.

Cibles : `jvm`, `iosArm64`, `iosX64`, `iosSimulatorArm64`. Pour Android, voir
[`:spotify-android-testing`](../spotify-android-testing).
