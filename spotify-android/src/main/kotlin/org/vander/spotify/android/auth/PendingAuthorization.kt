package org.vander.spotify.android.auth

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import org.vander.spotify.SpotifyError
import org.vander.spotify.auth.AuthorizationResponse

/**
 * Point de rendez-vous entre la coroutine suspendue dans [CustomTabAuthorizer] et
 * [SpotifyAuthActivity], que le système instancie et à qui l'on ne peut rien passer.
 *
 * Un objet de portée processus est le seul moyen : l'Activity de redirection est créée par
 * Android en réponse à une URL, pas par nous.
 *
 * Conséquence assumée : si le processus est tué pendant que l'utilisateur est dans le
 * navigateur, la demande en attente disparaît et l'appel est perdu. L'application doit alors
 * rappeler `signIn()`. Persister la demande n'aurait de sens que si l'on persistait aussi le
 * `code_verifier`, ce qui reviendrait à écrire sur disque le secret même que PKCE protège.
 */
internal object PendingAuthorization {
    private var pending: CompletableDeferred<Result<AuthorizationResponse>>? = null

    /**
     * Enregistre une demande, en annulant celle qui traînerait.
     *
     * Une demande abandonnée survivrait sinon indéfiniment, et la suivante recevrait la
     * réponse de la précédente.
     */
    @Synchronized
    fun start(): CompletableDeferred<Result<AuthorizationResponse>> {
        pending?.complete(Result.failure(SpotifyError.AuthorizationCancelled()))
        return CompletableDeferred<Result<AuthorizationResponse>>().also { pending = it }
    }

    @Synchronized
    fun deliver(result: Result<AuthorizationResponse>) {
        pending?.complete(result)
        pending = null
    }

    @Synchronized
    fun isPending(): Boolean = pending?.isActive == true
}
