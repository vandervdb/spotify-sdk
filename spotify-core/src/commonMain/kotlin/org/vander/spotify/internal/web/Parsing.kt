package org.vander.spotify.internal.web

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.vander.spotify.SpotifyError
import org.vander.spotify.internal.dto.AccountsErrorDto
import org.vander.spotify.internal.dto.ApiErrorBodyDto

internal val SpotifyJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

/**
 * Lit une réponse Spotify en [T], ou en [SpotifyError].
 *
 * Le corps est inspecté avant d'être désérialisé parce que l'API répond parfois `200` avec
 * un objet `error` : le code de statut seul ne suffit pas à décider.
 *
 * Rien n'est journalisé ici, volontairement. L'implémentation d'origine écrivait les corps
 * bruts en debug, ce qui déversait les données personnelles des endpoints `me` et les jetons
 * dans logcat. Une lib publiée ne peut pas décider ça pour ses consommateurs.
 */
internal suspend inline fun <reified T> HttpResponse.parseSpotify(): Result<T> {
    val raw = bodyAsText()

    return try {
        if (raw.isBlank()) {
            return if (status.value in 200..299) {
                Result.failure(SpotifyError.Api(status.value, "réponse vide"))
            } else {
                Result.failure(SpotifyError.Api(status.value, status.description))
            }
        }

        val root = SpotifyJson.parseToJsonElement(raw).jsonObject
        if ("error" in root) return Result.failure(raw.toSpotifyError(status.value))
        if (status.value !in 200..299) {
            return Result.failure(SpotifyError.Api(status.value, status.description))
        }

        Result.success(SpotifyJson.decodeFromString<T>(raw))
    } catch (e: SpotifyError) {
        Result.failure(e)
    } catch (e: Exception) {
        Result.failure(SpotifyError.Serialization(e))
    }
}

/** Réponse sans corps utile : seul le statut compte (PUT/DELETE de la bibliothèque). */
internal suspend fun HttpResponse.parseEmpty(): Result<Unit> {
    if (status.value in 200..299) return Result.success(Unit)
    val raw = runCatching { bodyAsText() }.getOrDefault("")
    return Result.failure(
        if (raw.isBlank()) SpotifyError.Api(status.value, status.description)
        else raw.toSpotifyError(status.value),
    )
}

/**
 * Les deux services parlent des dialectes d'erreur différents : `api.spotify.com` rend un
 * objet `{ error: { status, message } }`, `accounts.spotify.com` une chaîne
 * `{ error, error_description }`. On essaie les deux avant d'abandonner.
 */
internal fun String.toSpotifyError(httpStatus: Int): SpotifyError {
    runCatching { SpotifyJson.decodeFromString<ApiErrorBodyDto>(this) }
        .onSuccess { return it.error.toError(httpStatus) }

    runCatching { SpotifyJson.decodeFromString<AccountsErrorDto>(this) }
        .onSuccess { dto ->
            val reason = dto.errorDescription ?: dto.error
            return if (httpStatus == 401 || dto.error == "invalid_grant") {
                SpotifyError.Unauthorized(reason)
            } else {
                SpotifyError.Api(httpStatus, reason)
            }
        }

    return SpotifyError.Api(httpStatus, "erreur illisible")
}

private fun org.vander.spotify.internal.dto.ApiErrorDto.toError(httpStatus: Int): SpotifyError {
    val code = status.takeIf { it != 0 } ?: httpStatus
    return if (code == 401) SpotifyError.Unauthorized(message) else SpotifyError.Api(code, message)
}
