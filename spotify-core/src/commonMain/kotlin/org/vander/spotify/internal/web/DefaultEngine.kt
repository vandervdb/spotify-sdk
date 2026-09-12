package org.vander.spotify.internal.web

import io.ktor.client.engine.HttpClientEngine

/**
 * Moteur HTTP par défaut de la plateforme : OkHttp sur JVM/Android, Darwin sur iOS.
 *
 * Exposé comme valeur par défaut et non imposé : un consommateur qui gère déjà son pool de
 * connexions doit pouvoir fournir le sien.
 */
internal expect fun defaultHttpEngine(): HttpClientEngine
