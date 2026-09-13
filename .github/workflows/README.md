# Intégration continue

Trois jobs, et une limite assumée.

| Job | Runner | Couvre |
|---|---|---|
| `jvm` | ubuntu | `spotify-core`, `spotify-testing` — tests et contrôle de surface publique |
| `ios` | macos (arm64) | les mêmes suites sur simulateur, ce qui valide les `actual` Darwin |
| `android` | ubuntu | les quatre modules Android — **seulement si le secret est présent** |

## Pourquoi les modules Android sont conditionnels

Ils ne compilent pas sans `spotify-app-remote-release-*.aar` et `spotify-auth-release-*.aar`,
qui portent les conditions de Spotify, ne sont publiés sur aucun dépôt Maven, et sont donc
absents de ce dépôt. Vérifié : sans eux, `:spotify-android:compileDebugKotlin` échoue.

Sans le secret, le job ne prétend pas réussir : il émet un avertissement nommant les quatre
modules non vérifiés. Un job vert qui ne compile rien est pire que pas de job du tout.

## Fournir les AAR à la CI

Le secret n'expose rien publiquement : GitHub le chiffre, et les workflows déclenchés par une
pull request venue d'un fork n'y ont pas accès.

```bash
cd spotify-android/libs
tar -cz *.aar | base64 | pbcopy
```

Puis *Settings → Secrets and variables → Actions → New repository secret*, nommé
`SPOTIFY_SDK_AARS`.

## Deux garde-fous qui méritent d'exister

**Le simulateur est choisi à l'exécution.** Une cible iOS dont le `deviceId` n'existe pas sur
le runner rend la tâche de test `SKIPPED` : le build reste vert sans avoir rien exécuté. Le
job interroge `xcrun simctl list devices available` et passe un appareil réel.

**On vérifie que les tests iOS ont produit des résultats.** Même précaution, à l'autre bout :
si aucun fichier de résultats n'existe, le job échoue. Le piège s'est présenté pendant le
développement, il ne se présentera pas deux fois.
