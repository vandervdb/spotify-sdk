package org.vander.spotify.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.vander.spotify.android.AndroidSpotifyClient
import org.vander.spotify.android.PlayerConnection
import org.vander.spotify.auth.SessionState

/**
 * Écran unique : l'identité, la bibliothèque, la lecture.
 *
 * Chaque bouton correspond à une hypothèse à vérifier contre le vrai Spotify. Le résultat de
 * chaque appel est affiché tel quel — c'est un banc d'essai, pas une vitrine.
 */
@Composable
fun SampleScreen(
    spotify: AndroidSpotifyClient,
    log: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val session by spotify.session.collectAsStateWithLifecycle()
    val connection by spotify.player.connection.collectAsStateWithLifecycle()
    val nowPlaying by spotify.player.nowPlaying.collectAsStateWithLifecycle()
    val playlists by spotify.playlists.collectAsStateWithLifecycle()
    var lastResult by remember { mutableStateOf("—") }

    fun run(
        label: String,
        block: suspend () -> Result<*>,
    ) {
        scope.launch {
            val outcome =
                block().fold(
                    onSuccess = { "$label : OK" },
                    onFailure = { "$label : ÉCHEC — ${it::class.simpleName} — ${it.message}" },
                )
            lastResult = outcome
            log(outcome)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Session : ${session.label()}", style = MaterialTheme.typography.titleMedium)
                    Text("App Remote : ${connection.label()}")
                    Text(
                        nowPlaying?.let { "${it.title} — ${it.artist}${if (it.isPaused) " (en pause)" else ""}" }
                            ?: "aucune lecture",
                    )
                    HorizontalDivider()
                    Text("Dernier appel : $lastResult", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text("1 · Identité — PKCE, sans secret", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run("signIn") { spotify.signIn() } }) { Text("Se connecter") }
                OutlinedButton(onClick = {
                    run("signOut") {
                        spotify.signOut()
                        Result.success(Unit)
                    }
                }) { Text("Se déconnecter") }
            }

            Text("2 · Web API", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run("currentUser") { spotify.currentUser() } }) { Text("Profil") }
                Button(onClick = { run("refreshPlaylists") { spotify.refreshPlaylists() } }) {
                    Text("Playlists")
                }
                Button(onClick = { run("refreshQueue") { spotify.refreshQueue() } }) { Text("File") }
            }

            Text("3 · App Remote — Android uniquement", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { run("player.connect") { spotify.player.connect() } }) {
                    Text("Connecter")
                }
                Button(onClick = { run("resume") { spotify.player.resume() } }) { Text("Play") }
                Button(onClick = { run("pause") { spotify.player.pause() } }) { Text("Pause") }
                Button(onClick = { run("skipNext") { spotify.player.skipNext() } }) { Text("Suivant") }
            }

            HorizontalDivider()
            Text(
                "Playlists (${playlists.items.size} sur ${playlists.total})",
                style = MaterialTheme.typography.labelLarge,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(playlists.items, key = { it.id.value }) { playlist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(playlist.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${playlist.trackCount} titres", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun SessionState.label(): String =
    when (this) {
        SessionState.SignedOut -> "déconnecté"
        SessionState.Authorizing -> "autorisation en cours"
        SessionState.Authorized -> "identifié"
        is SessionState.Failed -> "échec — ${error::class.simpleName} : ${error.message}"
    }

private fun PlayerConnection.label(): String =
    when (this) {
        PlayerConnection.NotConnected -> "non connecté"
        PlayerConnection.Connecting -> "connexion…"
        PlayerConnection.Connected -> "connecté"
        is PlayerConnection.Failed -> "échec — ${error.message}"
    }
