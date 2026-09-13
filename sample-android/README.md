# :sample-android

Application de démonstration — et surtout **le seul endroit du dépôt où la bibliothèque
parle vraiment à Spotify**.

Tout le reste est vérifié contre des contrats : 71 tests, aucun appel réel. Cette application
lève les hypothèses que les tests ne peuvent pas lever :

- les paramètres PKCE passés par `setCustomParam` arrivent-ils au service d'autorisation ?
- l'App Remote se connecte-t-il avec `showAuthView(false)`, l'autorisation ayant été faite
  par notre flot et non par le sien ?
- le paramètre `state` fait-il l'aller-retour ?

Elle sert aussi de documentation : un exemple qui compile ne peut pas mentir, contrairement
à un extrait de README.

## Configuration

Le `clientId` est lu depuis `local.properties` à la racine, jamais versionné :

```properties
CLIENT_ID=<votre identifiant client Spotify>
```

L'application doit être déclarée sur le [dashboard Spotify](https://developer.spotify.com/dashboard)
avec **trois** éléments, faute de quoi l'autorisation échoue sur
`AUTHENTICATION_SERVICE_UNAVAILABLE` :

| | Valeur |
|---|---|
| Redirect URI | `org-vander-androidapp://callback` |
| Package name | `org.vander.spotify.sample` |
| Empreinte SHA-1 | celle du keystore qui signe l'APK |

```bash
keytool -list -v -keystore ~/.android/debug.keystore -storepass android | grep -i "SHA 1"
```

Le contrôle porte sur le **paquet appelant et sa signature**, pas seulement sur le
`clientId` : réutiliser l'identifiant d'une autre application ne suffit pas, il faut aussi
y déclarer ce paquet-ci.

## Lancer

```bash
./gradlew :sample-android:installDebug
adb shell am start -n org.vander.spotify.sample/.MainActivity
adb logcat -s SpotifySample
```

Chaque bouton correspond à une hypothèse, et le résultat de chaque appel est affiché tel
quel — succès comme échec, avec le type d'erreur. C'est un banc d'essai, pas une vitrine.

## Ce qu'elle a déjà prouvé

Au premier lancement réel, sur un appareil avec Spotify installé :

- le client se construit et lit son `clientId` depuis la configuration fournie par l'hôte ;
- `ActivityAuthorizer` enregistre son lanceur et ouvre bien
  `com.spotify.appauthorization.sso.AuthorizationActivity` — le chemin **app-à-app**, pas le
  repli navigateur, ce qui écarte au passage toute collision de schéma de redirection ;
- le résultat revient, est converti en `SpotifyError.Unauthorized` et remonte jusqu'à
  `SessionState.Failed`, affiché à l'écran.

Autrement dit toute la plomberie fonctionne. Ce qui manquait était côté dashboard :
le paquet de cette application n'y était pas déclaré.
