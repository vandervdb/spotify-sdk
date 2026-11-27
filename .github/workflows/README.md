# Intégration continue

Trois jobs.

| Job | Runner | Couvre |
|---|---|---|
| `jvm` | ubuntu | `spotify-core`, `spotify-testing` — tests et contrôle de surface publique |
| `ios` | macos (arm64) | les mêmes suites sur simulateur, ce qui valide les `actual` Darwin |
| `android` | ubuntu | les quatre modules Android |

## L'AAR App Remote de Spotify

Les modules Android ne compilent pas sans `spotify-app-remote-release-0.8.0.aar`, publié sur
aucun dépôt Maven et absent de ce dépôt. Il est en revanche public (Apache-2.0) dans
[spotify/android-sdk](https://github.com/spotify/android-sdk) : l'action
`.github/actions/spotify-aar` le télécharge et vérifie son empreinte SHA-256, pour qu'un
fichier remplacé en amont fasse échouer le job au lieu de passer inaperçu.

Un secret n'est pas une alternative : GitHub plafonne un secret à 48 Ko, l'AAR encodé en
base64 en fait plus de 170.

## Deux garde-fous qui méritent d'exister

**Le simulateur est choisi à l'exécution.** Une cible iOS dont le `deviceId` n'existe pas sur
le runner rend la tâche de test `SKIPPED` : le build reste vert sans avoir rien exécuté. Le
job interroge `xcrun simctl list devices available` et passe un appareil réel.

**On vérifie que les tests iOS ont produit des résultats.** Même précaution, à l'autre bout :
si aucun fichier de résultats n'existe, le job échoue. Le piège s'est présenté pendant le
développement, il ne se présentera pas deux fois.
