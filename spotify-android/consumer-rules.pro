# Le SDK App Remote communique par réflexion sur ses types de protocole : R8 ne voit pas
# les usages et les supprimerait sans cette règle.
-keep class com.spotify.protocol.** { *; }
-keep class com.spotify.android.appremote.** { *; }
-keep class com.spotify.sdk.android.auth.** { *; }
