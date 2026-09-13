import NativeSpotify from './NativeSpotify';

export type SessionState = 'SIGNED_OUT' | 'AUTHORIZING' | 'AUTHORIZED' | 'FAILED';
export type RepeatMode = 'OFF' | 'TRACK' | 'CONTEXT';

export type Track = {
  id: string;
  name: string;
  artist: string;
  album: string;
  durationMs: number;
};

export type NowPlaying = {
  track: Track | null;
  positionMs: number;
  isPaused: boolean;
  shuffle: boolean;
  repeat: RepeatMode;
  coverImageUri: string | null;
};

export type Playlist = {
  id: string;
  name: string;
  trackCount: number;
  coverUrl: string | null;
};

/**
 * Surface typée par-dessus le TurboModule.
 *
 * Le spec ne peut transporter que des types que le codegen connaît — d'où les `Object` qui
 * y figurent. Cette couche leur redonne un type côté TypeScript, sans rien changer au pont.
 */
export const Spotify = {
  signIn: (): Promise<void> => NativeSpotify.signIn(),
  signOut: (): Promise<void> => NativeSpotify.signOut(),
  getSessionState: (): Promise<SessionState> =>
    NativeSpotify.getSessionState() as Promise<SessionState>,

  refreshPlaylists: (): Promise<Playlist[]> =>
    NativeSpotify.refreshPlaylists() as Promise<Playlist[]>,
  refreshQueue: (): Promise<Track[]> => NativeSpotify.refreshQueue() as Promise<Track[]>,
  isSaved: (trackId: string): Promise<boolean> => NativeSpotify.isSaved(trackId),
  setSaved: (trackId: string, saved: boolean): Promise<void> =>
    NativeSpotify.setSaved(trackId, saved),

  /**
   * `false` sur iOS. Le contrôle de lecture local passe par l'App Remote, qui n'existe que
   * sur Android — ce n'est pas une lacune de ce pont mais une propriété de la plateforme.
   */
  isPlayerAvailable: (): boolean => NativeSpotify.isPlayerAvailable(),
  connectPlayer: (): Promise<void> => NativeSpotify.connectPlayer(),
  disconnectPlayer: (): Promise<void> => NativeSpotify.disconnectPlayer(),
  play: (trackId: string): Promise<void> => NativeSpotify.play(trackId),
  resume: (): Promise<void> => NativeSpotify.resume(),
  pause: (): Promise<void> => NativeSpotify.pause(),
  skipNext: (): Promise<void> => NativeSpotify.skipNext(),
  skipPrevious: (): Promise<void> => NativeSpotify.skipPrevious(),
  seekTo: (positionMs: number): Promise<void> => NativeSpotify.seekTo(positionMs),
  setShuffle: (enabled: boolean): Promise<void> => NativeSpotify.setShuffle(enabled),
  setRepeat: (mode: RepeatMode): Promise<void> => NativeSpotify.setRepeat(mode),

  onSessionChange: NativeSpotify.onSessionChange,
  onNowPlayingChange: NativeSpotify.onNowPlayingChange,
  onPlaylistsChange: NativeSpotify.onPlaylistsChange,
  onQueueChange: NativeSpotify.onQueueChange,
};

export default Spotify;
