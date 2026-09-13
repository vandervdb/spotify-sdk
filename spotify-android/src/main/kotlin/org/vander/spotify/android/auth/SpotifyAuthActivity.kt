package org.vander.spotify.android.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.AuthorizationResponse

/**
 * Activity transparente qui porte tout le cycle de l'autorisation : elle ouvre l'onglet,
 * reçoit la redirection, et détecte l'abandon.
 *
 * Les trois dans la même Activity, parce que seul cet enchaînement permet de distinguer
 * « l'utilisateur a refusé » de « rien ne s'est encore passé » :
 *
 * ```
 * onCreate            demande mémorisée
 * onResume  (1re)     ouvre l'onglet
 * onNewIntent         redirection reçue → on livre le code
 * onResume  (2e)      de retour sans redirection → l'utilisateur a fermé l'onglet
 * ```
 *
 * `launchMode="singleTask"` est indispensable : la redirection doit revenir sur *cette*
 * instance, pas en créer une seconde, sinon `onNewIntent` n'est jamais appelé.
 */
public class SpotifyAuthActivity : Activity() {
    private var tabLaunched = false
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tabLaunched = savedInstanceState?.getBoolean(STATE_TAB_LAUNCHED) == true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let(::deliver)
    }

    override fun onResume() {
        super.onResume()

        // onResume suit onNewIntent : sans ce garde, un succès serait immédiatement
        // réinterprété en abandon.
        if (delivered) {
            finish()
            return
        }

        // Cas du démarrage à froid : le système peut livrer la redirection dans l'intent
        // initial plutôt que par onNewIntent.
        intent?.data?.let {
            deliver(it)
            finish()
            return
        }

        if (!tabLaunched) {
            launchTab()
            return
        }

        // De retour sur cette Activity, l'onglet ouvert, et aucune redirection : l'onglet a
        // été fermé par l'utilisateur.
        deliver(Result.failure(SpotifyError.AuthorizationCancelled()))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_TAB_LAUNCHED, tabLaunched)
    }

    private fun launchTab() {
        val url = intent?.getStringExtra(EXTRA_AUTHORIZE_URL)
        if (url.isNullOrBlank()) {
            deliver(Result.failure(SpotifyError.Api(0, "URL d'autorisation absente")))
            finish()
            return
        }

        tabLaunched = true
        runCatching {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))
        }.onFailure { error ->
            // Aucun navigateur capable d'onglets personnalisés : l'implémentation par défaut
            // retombe sur un Intent VIEW ordinaire, mais si même cela échoue, il n'y a pas
            // de flot possible.
            deliver(Result.failure(SpotifyError.Network(error)))
            finish()
        }
    }

    private fun deliver(redirect: Uri) {
        val error = redirect.getQueryParameter("error")
        val code = redirect.getQueryParameter("code")
        val state = redirect.getQueryParameter("state").orEmpty()

        deliver(
            when {
                error != null -> Result.failure(errorFor(error))
                code.isNullOrBlank() -> Result.failure(SpotifyError.Api(0, "redirection sans code"))
                else -> Result.success(AuthorizationResponse(code, state))
            },
        )
    }

    private fun deliver(result: Result<AuthorizationResponse>) {
        if (delivered) return
        delivered = true
        PendingAuthorization.deliver(result)
    }

    /** `access_denied` est le refus explicite de l'utilisateur, pas une panne. */
    private fun errorFor(error: String): SpotifyError =
        if (error == "access_denied") {
            SpotifyError.AuthorizationCancelled()
        } else {
            SpotifyError.Unauthorized(error)
        }

    public companion object {
        internal const val EXTRA_AUTHORIZE_URL: String = "org.vander.spotify.AUTHORIZE_URL"
        private const val STATE_TAB_LAUNCHED = "tabLaunched"
    }
}
