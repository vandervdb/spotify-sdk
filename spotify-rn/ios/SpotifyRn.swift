import Foundation
import SpotifyCore

/// Adaptateur iOS du TurboModule.
///
/// ⚠️ **Non compilé.** Ce fichier est écrit contre l'en-tête Objective-C généré par
/// Kotlin/Native (`SpotifyCore.xcframework`, produit et vérifié), mais aucun projet Xcode
/// n'existe dans ce dépôt : ni ce code ni sa liaison au spec ObjC++ n'ont été construits.
/// Voir la section « Ce qui reste à faire » du README du paquet.
///
/// Différence assumée avec Android : l'App Remote n'existe pas sur iOS, donc
/// `isPlayerAvailable` rend `false` et toutes les commandes de lecture rejettent. Côté
/// Kotlin, cette distinction est portée par le classpath — `SpotifyPlayer` est simplement
/// absent et le compilateur tranche. Le spec TurboModule étant partagé entre les deux
/// plateformes, il faut ici un drapeau à l'exécution.
@objc(SpotifyRn)
public final class SpotifyRn: NSObject {
    private let client: SpotifyCoreSpotifyClient
    private var observers: [SpotifyCoreCancellable] = []

    /// - Parameter config: fournie par l'application hôte ; le `clientId` lui appartient.
    @objc public init(config: SpotifyCoreSpotifyConfig, authorizer: SpotifyCoreAuthorizer) {
        self.client = SpotifyCoreSpotify().create(
            config: config,
            tokenStore: KeychainTokenStore(),
            authorizer: authorizer
        )
        super.init()
    }

    // MARK: - Identité

    @objc public func signIn(resolve: @escaping RCTPromiseResolveBlock,
                             reject: @escaping RCTPromiseRejectBlock) {
        client.signIn { result, error in Self.settle(result, error, resolve, reject) }
    }

    @objc public func signOut(resolve: @escaping RCTPromiseResolveBlock,
                              reject: @escaping RCTPromiseRejectBlock) {
        client.signOut { _, error in Self.settle(nil, error, resolve, reject) }
    }

    @objc public func getSessionState(resolve: @escaping RCTPromiseResolveBlock,
                                      reject: @escaping RCTPromiseRejectBlock) {
        resolve(Self.wireName(client.session.value as! SpotifyCoreSessionState))
    }

    // MARK: - Bibliothèque

    @objc public func refreshPlaylists(resolve: @escaping RCTPromiseResolveBlock,
                                       reject: @escaping RCTPromiseRejectBlock) {
        client.refreshPlaylists { result, error in
            Self.settle(result.map(Self.playlistsToDictionary), error, resolve, reject)
        }
    }

    @objc public func setSaved(_ trackId: String, saved: Bool,
                               resolve: @escaping RCTPromiseResolveBlock,
                               reject: @escaping RCTPromiseRejectBlock) {
        client.setSaved(track: SpotifyCoreTrackId(value: trackId), saved: saved) { _, error in
            Self.settle(nil, error, resolve, reject)
        }
    }

    // MARK: - Lecture locale — indisponible sur iOS

    /// `false`, toujours. Le contrôle de lecture local passe par l'App Remote, dont le SDK
    /// n'existe que sur Android. Ce n'est pas une lacune de ce pont mais une propriété de
    /// la plateforme, et JavaScript doit l'interroger avant d'afficher des commandes.
    @objc public func isPlayerAvailable() -> NSNumber { NSNumber(value: false) }

    @objc public func connectPlayer(resolve: @escaping RCTPromiseResolveBlock,
                                    reject: @escaping RCTPromiseRejectBlock) {
        Self.rejectUnsupported(reject)
    }

    @objc public func play(_ trackId: String,
                           resolve: @escaping RCTPromiseResolveBlock,
                           reject: @escaping RCTPromiseRejectBlock) {
        Self.rejectUnsupported(reject)
    }

    // ... resume, pause, skipNext, skipPrevious, seekTo, setShuffle, setRepeat :
    //     toutes rejettent de la même façon.

    // MARK: - Plomberie

    private static func rejectUnsupported(_ reject: @escaping RCTPromiseRejectBlock) {
        reject(
            "PLAYER_UNAVAILABLE",
            "Le contrôle de lecture local exige l'App Remote, qui n'existe que sur Android.",
            nil
        )
    }

    /// Le `Result` de Kotlin traverse le pont en `resolve`/`reject` ; le code d'erreur est
    /// celui du type scellé `SpotifyError`, donc identique à celui émis côté Android.
    private static func settle(_ value: Any?, _ error: Error?,
                               _ resolve: @escaping RCTPromiseResolveBlock,
                               _ reject: @escaping RCTPromiseRejectBlock) {
        if let error {
            reject(errorCode(error), error.localizedDescription, error)
        } else {
            resolve(value)
        }
    }

    private static func errorCode(_ error: Error) -> String {
        switch error {
        case is SpotifyCoreSpotifyErrorNotSignedIn: return "NOT_SIGNED_IN"
        case is SpotifyCoreSpotifyErrorUnauthorized: return "UNAUTHORIZED"
        case is SpotifyCoreSpotifyErrorAuthorizationCancelled: return "AUTHORIZATION_CANCELLED"
        case is SpotifyCoreSpotifyErrorStateMismatch: return "STATE_MISMATCH"
        case is SpotifyCoreSpotifyErrorNetwork: return "NETWORK"
        case is SpotifyCoreSpotifyErrorSerialization: return "SERIALIZATION"
        default: return "UNKNOWN"
        }
    }

    private static func wireName(_ state: SpotifyCoreSessionState) -> String {
        switch state {
        case is SpotifyCoreSessionStateSignedOut: return "SIGNED_OUT"
        case is SpotifyCoreSessionStateAuthorizing: return "AUTHORIZING"
        case is SpotifyCoreSessionStateAuthorized: return "AUTHORIZED"
        default: return "FAILED"
        }
    }

    private static func playlistsToDictionary(_ collection: SpotifyCorePlaylistCollection) -> [String: Any] {
        [
            "total": collection.total,
            "items": collection.items.map { playlist in
                [
                    "id": playlist.id.value,
                    "name": playlist.name,
                    "trackCount": playlist.trackCount,
                    "coverUrl": playlist.cover?.url as Any,
                ]
            },
        ]
    }
}
