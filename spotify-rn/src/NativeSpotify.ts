import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';
// Depuis React Native 0.76, `CodegenTypes` n'a pas de déclaration TypeScript ; c'est
// `CodegenTypesNamespace` qui porte le .d.ts. Le codegen accepte les deux, le
// vérificateur de types un seul.
import type { EventEmitter } from 'react-native/Libraries/Types/CodegenTypesNamespace';

/**
 * Spécification du TurboModule — source unique du codegen pour Android et iOS.
 *
 * Le vocabulaire est dérivé de `SpotifyClient` et `SpotifyPlayer` côté Kotlin, pas
 * reconçu : les `suspend fun` deviennent des `Promise`, les `StateFlow` deviennent des
 * événements. C'est un adaptateur, et un adaptateur qui invente sa propre forme finit par
 * diverger de ce qu'il adapte.
 *
 * Contrainte réelle et assumée : le spec est partagé entre les deux plateformes, donc le
 * système de types ne peut pas dire « ces méthodes n'existent que sur Android ». Côté
 * Kotlin, `SpotifyPlayer` n'existe tout simplement pas sur iOS et le compilateur tranche ;
 * ici il faut un drapeau à l'exécution — d'où `isPlayerAvailable`. Les méthodes du lecteur
 * rejettent proprement sur iOS plutôt que d'être absentes.
 */
export interface Spec extends TurboModule {
  // ---- identité ----------------------------------------------------------
  /** Ouvre l'écran d'autorisation Spotify (PKCE). Rejette si l'utilisateur renonce. */
  signIn(): Promise<void>;
  signOut(): Promise<void>;
  /** `SIGNED_OUT` · `AUTHORIZING` · `AUTHORIZED` · `FAILED`. */
  getSessionState(): Promise<string>;

  // ---- bibliothèque, disponible partout -----------------------------------
  refreshPlaylists(): Promise<Object>;
  refreshQueue(): Promise<Object>;
  currentUser(): Promise<Object>;
  isSaved(trackId: string): Promise<boolean>;
  setSaved(trackId: string, saved: boolean): Promise<void>;

  // ---- lecture locale, Android uniquement ---------------------------------
  /**
   * `false` sur iOS, où l'App Remote n'existe pas. À interroger avant d'afficher des
   * commandes de lecture : sur iOS, toutes celles qui suivent rejettent.
   */
  isPlayerAvailable(): boolean;
  connectPlayer(): Promise<void>;
  disconnectPlayer(): Promise<void>;
  play(trackId: string): Promise<void>;
  resume(): Promise<void>;
  pause(): Promise<void>;
  skipNext(): Promise<void>;
  skipPrevious(): Promise<void>;
  seekTo(positionMs: number): Promise<void>;
  setShuffle(enabled: boolean): Promise<void>;
  /** `OFF` · `TRACK` · `CONTEXT`. */
  setRepeat(mode: string): Promise<void>;

  // ---- état poussé --------------------------------------------------------
  readonly onSessionChange: EventEmitter<string>;
  readonly onNowPlayingChange: EventEmitter<Object>;
  readonly onPlaylistsChange: EventEmitter<Object>;
  readonly onQueueChange: EventEmitter<Object>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SpotifyRn');
