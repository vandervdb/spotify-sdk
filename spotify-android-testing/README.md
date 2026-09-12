# :spotify-android-testing

`FakeSpotifyPlayer` et `FakeAndroidSpotifyClient`, pour tester un consommateur Android.

```kotlin
testImplementation("org.vander.spotify:spotify-android-testing:<version>")
```

## Pourquoi un artefact séparé de `:spotify-testing`

`SpotifyPlayer` n'existe que dans `:spotify-android` — c'est le choix « App Remote sur
Android uniquement », tenu jusqu'au bout. Son double ne peut donc pas vivre dans un module
multiplateforme qui cible aussi iOS. Le découpage des doubles reflète exactement celui de la
lib, et un consommateur iOS ne tire jamais de code Android.

## Utilisation

```kotlin
val spotify = FakeAndroidSpotifyClient()
val viewModel = PlayerViewModel(spotify)

spotify.fakePlayer.emitNowPlaying(nowPlaying)
viewModel.onSkipNext()

assertEquals(listOf(PlayerCommand.SkipNext), spotify.fakePlayer.commands)
```

`FakeAndroidSpotifyClient` combine les deux doubles derrière le type unique qu'un ViewModel
injecte, tout en les laissant accessibles séparément pour piloter chaque axe.

## Le double respecte le contrat

Une commande envoyée sans liaison ouverte **échoue**, comme dans l'implémentation réelle.
Un double permissif laisserait passer un écran qui commande le lecteur avant de s'y être
connecté : le test réussirait, l'application non.
